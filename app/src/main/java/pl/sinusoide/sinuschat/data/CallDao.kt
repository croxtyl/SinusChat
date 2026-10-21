package pl.sinusoide.sinuschat.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CallDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(call: CallEntity)

    @Query("SELECT * FROM calls ORDER BY startedAtMs DESC")
    fun observeAll(): LiveData<List<CallEntity>>

    @Query("SELECT * FROM calls ORDER BY startedAtMs DESC")
    fun getAllSync(): List<CallEntity>

    @Query("DELETE FROM calls WHERE id = :id")
    fun deleteById(id: String)

    @Query("UPDATE calls SET conversationId = :newId WHERE conversationId = :oldId")
    fun renameConversation(oldId: String, newId: String)

    @Query("UPDATE calls SET otherUserHash = :newHash WHERE otherUserHash = :oldHash")
    fun renameOther(oldHash: String, newHash: String)

    @Query("DELETE FROM calls")
    fun clearAll()
}
