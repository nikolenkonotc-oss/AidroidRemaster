package app.aidroid.remaster.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "chats",
    indices = [Index(value = ["syncId"], unique = true)]
)
data class ChatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "Новый чат",
    val createdAt: Long,
    val updatedAt: Long,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    @ColumnInfo(defaultValue = "''") val syncId: String = UUID.randomUUID().toString(),
    val deletedAt: Long? = null
)
