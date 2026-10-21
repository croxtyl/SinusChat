package pl.sinusoide.sinuschat.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "link_previews")
class LinkPreviewEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String?,
    val description: String?,
    val image: ByteArray?,
    val icon: ByteArray?,
    val savedAt: Long
)

@Dao
interface LinkPreviewDao {
    @Query("SELECT * FROM link_previews WHERE id = :id LIMIT 1")
    fun get(id: String): LinkPreviewEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun put(entity: LinkPreviewEntity)

    @Query("SELECT id FROM link_previews ORDER BY savedAt DESC")
    fun ids(): List<String>

    @Query("DELETE FROM link_previews WHERE id NOT IN (SELECT id FROM link_previews ORDER BY savedAt DESC LIMIT :keep)")
    fun trim(keep: Int)
}
