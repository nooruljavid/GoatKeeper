package com.goatkeeper.app.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Synchronizes the local Room database with Firestore.
 *
 * Important: Goat.id is user-editable. cloudId is the stable Firestore document identity,
 * so changing 0001 -> 0002 updates the same cloud document instead of creating a duplicate.
 */
class SyncManager(private val dao: FarmDao) {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun getUserId() = auth.currentUser?.uid

    /** Fire-and-forget upload used after normal local edits. Failed uploads are retried by syncNow(). */
    fun uploadToCloud() {
        CoroutineScope(Dispatchers.IO).launch {
            uploadToCloudNow()
        }
    }

    private suspend fun uploadToCloudNow(): Boolean = withContext(Dispatchers.IO) {
        val uid = getUserId() ?: return@withContext false
        try {
            android.util.Log.d("SyncManager", "Starting upload")

            val goats = dao.goats().first()
            for (goat in goats) {
                val stableId = goat.cloudId.ifBlank { goat.id }
                firestore.collection("users").document(uid)
                    .collection("goats").document(stableId)
                    .set(goat.copy(cloudId = stableId)).await()
            }

            val records = dao.records().first()
            for (record in records) {
                firestore.collection("users").document(uid)
                    .collection("farm_records").document(record.recordId.toString())
                    .set(record).await()
            }

            // Do not download until all writes currently issued by this sync are acknowledged.
            firestore.waitForPendingWrites().await()

            android.util.Log.d("SyncManager", "Upload successful: ${goats.size} goats, ${records.size} records")
            true
        } catch (e: Exception) {
            android.util.Log.e("SyncManager", "Upload failed; local data will be retried", e)
            false
        }
    }

    /**
     * Full synchronization. Upload happens BEFORE download so offline local changes cannot
     * be overwritten by an older cloud copy when the network returns.
     */
    suspend fun syncNow(): Boolean = withContext(Dispatchers.IO) {
        if (getUserId() == null) return@withContext false
        val uploaded = uploadToCloudNow()
        if (!uploaded) return@withContext false
        downloadFromCloudNow()
        true
    }

    /** Backwards-compatible download entry point used by login. */
    fun downloadFromCloud(onComplete: () -> Unit = {}) {
        CoroutineScope(Dispatchers.IO).launch {
            if (downloadFromCloudNow()) onComplete()
        }
    }

    private suspend fun downloadFromCloudNow(): Boolean = withContext(Dispatchers.IO) {
        val uid = getUserId() ?: return@withContext false
        try {
            android.util.Log.d("SyncManager", "Starting download")

            val goatSnapshots = firestore.collection("users").document(uid)
                .collection("goats").get().await()
            for (snapshot in goatSnapshots.documents) {
                val goat = snapshot.toObject(Goat::class.java) ?: continue
                // For old cloud documents, snapshot.id is the old Goat ID. Preserve it as cloudId.
                dao.saveGoat(goat.copy(cloudId = snapshot.id))
            }

            val recordSnapshots = firestore.collection("users").document(uid)
                .collection("farm_records").get().await()
            for (snapshot in recordSnapshots.documents) {
                val record = snapshot.toObject(FarmRecord::class.java) ?: continue
                dao.saveRecord(record)
            }

            android.util.Log.d("SyncManager", "Download successful: ${goatSnapshots.size()} goats, ${recordSnapshots.size()} records")
            true
        } catch (e: Exception) {
            android.util.Log.e("SyncManager", "Download failed", e)
            false
        }
    }

    suspend fun deleteGoatFromCloud(id: String) {
        val uid = getUserId() ?: return
        try {
            firestore.collection("users").document(uid)
                .collection("goats").document(id).delete().await()
        } catch (e: Exception) {
            android.util.Log.e("SyncManager", "Delete goat failed", e)
        }
    }

    suspend fun deleteRecordFromCloud(id: Long) {
        val uid = getUserId() ?: return
        try {
            firestore.collection("users").document(uid)
                .collection("farm_records").document(id.toString()).delete().await()
        } catch (e: Exception) {
            android.util.Log.e("SyncManager", "Delete record failed", e)
        }
    }
}
