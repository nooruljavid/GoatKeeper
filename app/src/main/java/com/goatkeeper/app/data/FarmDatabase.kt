package com.goatkeeper.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Entity(tableName = "goats")
data class Goat(
    @PrimaryKey val id: String = "",
    // Stable Firestore document identity. Goat ID is editable; this value is not.
    val cloudId: String = java.util.UUID.randomUUID().toString(),
    val name: String = "",
    val breed: String = "",
    val dateOfBirth: String = "",
    val gender: String = "",
    val status: String = "Active",
    val damId: String = "",
    val sireId: String = "",
    val photoUri: String = "",
    val colorMarkings: String = "",
    val microchipId: String = "",
    val notes: String = "",
    val lastViewed: Long = 0
)

/** A compact, extensible ledger for Health, Breeding, Kidding, Insurance, Sale and Transfer events. */
@Entity(
    tableName = "farm_records",
    foreignKeys = [
        ForeignKey(
            entity = Goat::class,
            parentColumns = ["id"],
            childColumns = ["goatId"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("goatId")]
)
data class FarmRecord(
    @PrimaryKey(autoGenerate = true) val recordId: Long = 0,
    val goatId: String? = null,
    val type: String = "",
    val date: String = "",
    val dueDate: String = "",
    val title: String = "",
    val details: String = "",
    val amount: Double? = null,
    val quantity: Double? = null,
    val unit: String = "",
    val party: String = "",
    val paymentStatus: String = "",
    val sireId: String = "",
    val actualDate: String = "",
    val kidsCount: Int? = null,
    val kidsAlive: Int? = null
)

@Entity(tableName = "farm_details")
data class FarmDetails(
    @PrimaryKey val id: Int = 0, // Single row entity
    val farmName: String = "",
    val country: String = "",
    val state: String = "",
    val city: String = "",
    val district: String = "",
    val address: String = "",
    val postalCode: String = "",
    val contactNo: String = "",
    val countryCode: String = "",
    val ownerName: String = ""
)

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = 0, // Single row entity
    val language: String = "en" // "en" or "ta"
)

@Dao
interface FarmDao {
    @Query("SELECT * FROM goats ORDER BY id") fun goats(): Flow<List<Goat>>
    @Query("SELECT * FROM goats WHERE id = :id LIMIT 1") fun goat(id: String): Flow<Goat?>
    @Query("SELECT * FROM farm_records ORDER BY date DESC") fun records(): Flow<List<FarmRecord>>
    @Query("SELECT * FROM farm_records WHERE goatId = :goatId OR goatId IS NULL ORDER BY date DESC") fun recordsFor(goatId: String): Flow<List<FarmRecord>>
    @Query("SELECT * FROM farm_records WHERE dueDate != '' ORDER BY dueDate") fun dueRecords(): Flow<List<FarmRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveGoat(goat: Goat)

    @Update
    suspend fun updateGoat(goat: Goat)

    @Query("UPDATE goats SET status = :status WHERE id = :goatId")
    suspend fun updateGoatStatus(goatId: String, status: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRecord(record: FarmRecord)

    @Update
    suspend fun updateRecord(record: FarmRecord)

    @Delete
    suspend fun deleteGoat(goat: Goat)

    @Delete
    suspend fun deleteRecord(record: FarmRecord)

    @Query("DELETE FROM farm_records WHERE goatId = :goatId AND type = :type")
    suspend fun deleteRecordsByTypeForGoat(goatId: String, type: String)

    @Query("SELECT * FROM farm_records WHERE goatId = :goatId AND type = :type")
    suspend fun findRecordsByTypeForGoat(goatId: String, type: String): List<FarmRecord>

    @Query("DELETE FROM goats")
    suspend fun clearGoats()

    @Query("DELETE FROM farm_records")
    suspend fun clearRecords()

    @Query("UPDATE goats SET lastViewed = :timestamp WHERE id = :id")
    suspend fun updateLastViewed(id: String, timestamp: Long)

    @Query("UPDATE goats SET id = :newId WHERE id = :oldId")
    suspend fun updateGoatId(oldId: String, newId: String)

    @Query("SELECT * FROM farm_details WHERE id = 0")
    fun farmDetails(): Flow<FarmDetails?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveFarmDetails(details: FarmDetails)

    @Query("DELETE FROM farm_details")
    suspend fun clearFarmDetails()

    @Query("SELECT * FROM app_settings WHERE id = 0")
    fun appSettings(): Flow<AppSettings?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAppSettings(settings: AppSettings)
}

@Database(entities = [Goat::class, FarmRecord::class, FarmDetails::class, AppSettings::class], version = 11, exportSchema = false)
abstract class FarmDatabase : RoomDatabase() {
    abstract fun dao(): FarmDao

    companion object {
        private val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Existing Firestore documents used the old Goat ID as their document ID.
                // Preserve that identity when introducing the new stable cloudId field.
                db.execSQL("ALTER TABLE goats ADD COLUMN cloudId TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE goats SET cloudId = id WHERE cloudId = ''")
            }
        }

        @Volatile
        private var networkCallbackRegistered = false

        fun create(context: Context): FarmDatabase {
            val database = Room.databaseBuilder(
                context,
                FarmDatabase::class.java,
                "goatkeeper.db"
            )
                .addMigrations(MIGRATION_6_7)
                .fallbackToDestructiveMigration(true)
                .build()

            registerNetworkSync(context.applicationContext, database.dao())
            return database
        }

        private fun registerNetworkSync(context: Context, dao: FarmDao) {
            if (networkCallbackRegistered) return
            synchronized(this) {
                if (networkCallbackRegistered) return
                val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        CoroutineScope(Dispatchers.IO).launch {
                            SyncManager(context, dao).syncNow()
                        }
                    }
                }
                try {
                    connectivity.registerDefaultNetworkCallback(callback)
                    networkCallbackRegistered = true
                } catch (e: Exception) {
                    android.util.Log.e("FarmDatabase", "Could not register network sync", e)
                }
            }
        }
    }
}
