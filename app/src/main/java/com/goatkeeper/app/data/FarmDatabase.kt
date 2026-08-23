package com.goatkeeper.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "goats")
data class Goat(
    @PrimaryKey val id: String,
    val name: String = "",
    val breed: String,
    val dateOfBirth: String,
    val gender: String,
    val status: String = "Active",
    val damId: String = "",
    val sireId: String = "",
    val photoUri: String = "",
    val colorMarkings: String = "",
    val microchipId: String = "",
    val notes: String = ""
)

/** A compact, extensible ledger for Health, Breeding, Kidding, Insurance, Sale and Transfer events. */
@Entity(tableName = "farm_records")
data class FarmRecord(
    @PrimaryKey(autoGenerate = true) val recordId: Long = 0,
    val goatId: String = "",
    val type: String, // Health, Breeding, Insurance, Sale, Transfer
    val date: String,
    val dueDate: String = "", // Next due, Expiry, or Expected Kidding
    val title: String, // Description, Policy #, or Breed Type
    val details: String = "", // Notes, Condition, etc.
    val amount: Double? = null, // Cost, Price, Premium, Coverage
    val quantity: Double? = null,
    val unit: String = "",
    val party: String = "", // Vet, Buyer, Insurer
    val paymentStatus: String = "",
    val sireId: String = "", // For breeding
    val actualDate: String = "", // For actual kidding date
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
    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE) suspend fun saveGoat(goat: Goat): Unit
    @androidx.room.Insert suspend fun saveRecord(record: FarmRecord): Unit
    @androidx.room.Delete suspend fun deleteGoat(goat: Goat): Unit
}

@Database(entities = [Goat::class, FarmRecord::class], version = 2, exportSchema = false)
abstract class FarmDatabase : RoomDatabase() {
    abstract fun dao(): FarmDao
    companion object {
        fun create(context: Context) = Room.databaseBuilder(context, FarmDatabase::class.java, "goatkeeper.db")
            .fallbackToDestructiveMigration()
            .build()
    }
}
