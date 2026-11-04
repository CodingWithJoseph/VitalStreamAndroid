package com.vitalstream.android.sync

import android.content.Context
import androidx.work.*
import com.vitalstream.android.VitalStreamApplication
import com.vitalstream.android.data.ApiFailure
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val graph = (applicationContext as VitalStreamApplication).graph
        val owner = inputData.getString("owner") ?: return Result.failure()
        if (graph.auth.owner.value != owner) return Result.failure()
        return try { graph.repository.sync(owner); Result.success() }
        catch (error: CancellationException) { throw error }
        catch (error: ApiFailure) { if (error.status == 429 || error.status >= 500) Result.retry() else Result.failure() }
        catch (_: IOException) { Result.retry() }
        catch (_: Exception) { Result.failure() }
    }
    companion object {
        fun enqueue(context: Context, owner: String) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInputData(workDataOf("owner" to owner))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork("sync-$owner", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
