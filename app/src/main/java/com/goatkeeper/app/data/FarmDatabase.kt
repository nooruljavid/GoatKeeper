package com.goatkeeper.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "goats")
data class Goat(
    @PrimaryKey val id: String = "",
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
@Entity(tableName = "farm_records")
data class FarmRecord(
    @PrimaryKey(autoGenerate = true) val recordId: Long = 0,
    val goatId: String = "",
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

@Dao
interface FarmDao {
    @Query("SELECT * FROM goats ORDER BY id") fun goats(): Flow<List<Goat>>
    @Query("SELECT * FROM goats WHERE id = :id LIMIT 1") fun goat(id: String): Flow<Goat?>
    @Query("SELECT * FROM farm_records ORDER BY date DESC") fun records(): Flow<List<FarmRecord>>
    @Query("SELECT * FROM farm_records WHERE goatId = :goatId ORDER BY date DESC") fun recordsFor(goatId: String): Flow<List<FarmRecord>>
    @Query("SELECT * FROM farm_records WHERE dueDate != '' ORDER BY dueDate") fun dueRecords(): Flow<List<FarmRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveGoat(goat: Goat)

    @Update
    suspend fun updateGoat(goat: Goat)

    @Query("UPDATE goats SET status = :status WHERE id = :goatId")
    suspend fun updateGoatStatus(goatId: String, status: String)

    @Insert
    suspend fun saveRecord(record: FarmRecord)

    @Update
    suspend fun updateRecord(record: FarmRecord)

    @Delete
    suspend fun deleteGoat(goat: Goat)

    @Delete
    suspend fun deleteRecord(record: FarmRecord)

    @Query("DELETE FROM goats")
    suspend fun clearGoats()

    @Query("DELETE FROM farm_records")
    suspend fun clearRecords()

    @Query("UPDATE goats SET lastViewed = :timestamp WHERE id = :id")
    suspend fun updateLastViewed(id: String, timestamp: Long)
}

@Database(entities = [Goat::class, FarmRecord::class], version = 5, exportSchema = false)
abstract class FarmDatabase : RoomDatabase() {
    abstract fun dao(): FarmDao
    companion object {
        fun create(context: Context) = Room.databaseBuilder(context, FarmDatabase::class.java, "goatkeeper.db")
            .fallbackToDestructiveMigration(true)
            .build()
    }
}
