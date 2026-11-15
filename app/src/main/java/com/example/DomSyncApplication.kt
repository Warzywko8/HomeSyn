package com.example

import android.app.Application
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings

class DomSyncApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        configureFirestoreCacheSafely()
    }

    private fun configureFirestoreCacheSafely() {
        runCatching {
            val databaseId = getString(R.string.firestore_database_id)
            if (databaseId.isNotBlank()) {
                val db = FirebaseFirestore.getInstance(databaseId)
                db.firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(
                        PersistentCacheSettings.newBuilder()
                            .setSizeBytes(50L * 1024L * 1024L)
                            .build()
                    )
                    .build()
            }
        }
    }
}
