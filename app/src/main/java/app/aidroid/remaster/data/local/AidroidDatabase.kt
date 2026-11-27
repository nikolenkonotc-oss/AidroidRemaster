package app.aidroid.remaster.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ChatEntity::class, MessageEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AidroidDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
}
