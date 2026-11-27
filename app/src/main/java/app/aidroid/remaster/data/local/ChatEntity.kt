package app.aidroid.remaster.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "Новый чат",
    val createdAt: Long,
    val updatedAt: Long,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false
)
