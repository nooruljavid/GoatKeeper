package com.goatkeeper.app.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.time.Duration.Companion.milliseconds

class SyncManager(private val dao: FarmDao) {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val scope = CoroutineScope(Dispatchers.IO)

    private fun getUserId() = auth.currentUser?.uid

    /** Syncs all local data to the cloud. Called periodically or on data change. */
    fun uploadToCloud() {
        val uid = getUserId() ?: return
        scope.launch {
            try {
                android.util.Log.d("SyncManager", "Starting Upload...")
                // Upload Goats
                val goats = dao.goats().first()
                goats.forEach { goat ->
                    firestore.collection("users").document(uid)
                        .collection("goats").document(goat.id)
                        .set(goat).await()
                }

                // Upload Records
                val records = dao.records().first()
                records.forEach { record ->
                    firestore.collection("users").document(uid)
                        .collection("farm_records").document(record.recordId.toString())
                        .set(record).await()
                }
                android.util.Log.d("SyncManager", "Upload Successful!")
            } catch (e: Exception) {
                android.util.Log.e("SyncManager", "Upload Failed", e)
            }
        }
    }

    /** Downloads all data from the cloud to local database. Called on first login. */
    fun downloadFromCloud(onComplete: () -> Unit = {}) {
        val uid = getUserId() ?: return
        scope.launch {
            try {
                android.util.Log.d("SyncManager", "Starting Download...")
                // Download Goats
                val goatSnapshots = firestore.collection("users").document(uid)
                    .collection("goats").get().await()
                
                android.util.Log.d("SyncManager", "Found ${goatSnapshots.size()} goats")
                
                goatSnapshots.toObjects(Goat::class.java).forEach { goat ->
                    dao.saveGoat(goat)
                }

                // Download Records
                val recordSnapshots = firestore.collection("users").document(uid)
                    .collection("farm_records").get().await()
                
                android.util.Log.d("SyncManager", "Found ${recordSnapshots.size()} records")
                
                recordSnapshots.toObjects(FarmRecord::class.java).forEach { record ->
                    dao.saveRecord(record)
                }
                
                android.util.Log.d("SyncManager", "Download Successful!")
                delay(500.milliseconds) // Small delay to let database settle
                onComplete()
            } catch (e: Exception) {
                android.util.Log.e("SyncManager", "Download Failed", e)
            }
        }
    }

    fun deleteGoatFromCloud(id: String) {
        val uid = getUserId() ?: return
        scope.launch {
            firestore.collection("users").document(uid)
                .collection("goats").document(id).delete()
        }
    }

    fun deleteRecordFromCloud(id: Long) {
        val uid = getUserId() ?: return
        scope.launch {
            firestore.collection("users").document(uid)
                .collection("farm_records").document(id.toString()).delete()
        }
    }
}
