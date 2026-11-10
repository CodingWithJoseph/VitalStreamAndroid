package com.vitalstream.android.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ObservationDao {
    @Query("SELECT * FROM observations WHERE owner = :owner AND syncState != 'deleted' ORDER BY observedAt DESC")
    abstract fun observe(owner: String): Flow<List<Observation>>
    @Query("SELECT * FROM observations WHERE owner = :owner AND syncState = 'pending' LIMIT 100")
    abstract suspend fun pending(owner: String): List<Observation>
    @Query("SELECT * FROM observations WHERE owner = :owner AND id = :id")
    abstract suspend fun find(owner: String, id: String): Observation?
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(record: Observation): Long
    @Update
    abstract suspend fun update(record: Observation)
    @Query("UPDATE observations SET syncState = :state WHERE owner = :owner AND id = :id AND revision = :revision")
    abstract suspend fun acknowledge(owner: String, id: String, revision: Long, state: String)
    @Query("SELECT * FROM observations WHERE owner = :owner AND syncState IN ('synced', 'stale')")
    abstract suspend fun acknowledged(owner: String): List<Observation>

    @Transaction
    open suspend fun reconcile(owner: String, serverRecords: List<Observation>) {
        merge(serverRecords)
        val ids = serverRecords.map { it.id }.toSet()
        acknowledged(owner).filter { it.id !in ids }.forEach {
            acknowledge(owner, it.id, it.revision, "deleted")
        }
    }
    @Transaction
    open suspend fun merge(records: List<Observation>) {
        records.forEach { incoming ->
            if (insert(incoming) == -1L) {
                val previous = find(incoming.owner, incoming.id)!!
                if (previous.syncState != "deleted" && incoming.revision > previous.revision) update(incoming)
            }
        }
    }
}

@Database(entities = [Observation::class], version = 1, exportSchema = true)
abstract class ObservationDatabase : RoomDatabase() {
    abstract fun observations(): ObservationDao
}
