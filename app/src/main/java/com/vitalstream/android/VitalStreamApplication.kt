package com.vitalstream.android

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.vitalstream.android.auth.AuthManager
import com.vitalstream.android.data.*
import com.vitalstream.android.health.HealthConnectSource
import com.vitalstream.android.sync.ObservationRepository

class AppGraph(context: Context) {
    val auth = AuthManager(context)
    val database = Room.databaseBuilder(context, ObservationDatabase::class.java, "vitalstream.db").build()
    val api = VitalStreamApi(BuildConfig.API_URL)
    val repository = ObservationRepository(database.observations(), auth, api)
    val health = HealthConnectSource(context)
}
class VitalStreamApplication : Application() {
    val graph by lazy { AppGraph(this) }
}
