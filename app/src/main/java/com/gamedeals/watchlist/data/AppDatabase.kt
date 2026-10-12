package com.gamedeals.watchlist.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchDao {
    @Query("SELECT * FROM watch_items ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<WatchItem>>

    @Query("SELECT * FROM watch_items ORDER BY addedAt DESC")
    suspend fun getAll(): List<WatchItem>

    @Query("SELECT * FROM watch_items WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): WatchItem?

    @Query("SELECT * FROM watch_items WHERE url = :url LIMIT 1")
    suspend fun findByUrl(url: String): WatchItem?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: WatchItem): Long

    @Update
    suspend fun update(item: WatchItem)

    @Delete
    suspend fun delete(item: WatchItem)
}

class Converters {
    @TypeConverter
    fun storeToString(store: Store): String = store.name

    @TypeConverter
    fun stringToStore(value: String): Store = Store.valueOf(value)
}

@Database(entities = [WatchItem::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchDao(): WatchDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "game_deals.db",
                ).build().also { instance = it }
            }
    }
}
