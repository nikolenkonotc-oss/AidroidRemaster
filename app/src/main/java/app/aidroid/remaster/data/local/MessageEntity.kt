package app.aidroid.remaster.data.local

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("chatId"),
        Index(value = ["chatId", "createdAt"]),
        Index(value = ["syncId"], unique = true)
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chatId: Long,
    val role: String,
    val content: String,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "'[]'") val attachmentsJson: String = "[]",
    @ColumnInfo(defaultValue = "''") val syncId: String = UUID.randomUUID().toString(),
    val deletedAt: Long? = null
)
