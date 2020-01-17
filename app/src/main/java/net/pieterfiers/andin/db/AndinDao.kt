package net.pieterfiers.andin.db

import androidx.lifecycle.LiveData
import androidx.room.*
import java.util.*

@Dao
interface AndinDao {
    @Insert
    fun insert(room: FavoriteRoom)

    @Delete
    fun delete(room: FavoriteRoom)

    @Query("DELETE FROM favorite_room WHERE uuid = :uuid")
    fun delete(uuid: UUID)

    @Query("SELECT * FROM favorite_room WHERE uuid = :uuid")
    fun get(uuid: UUID): LiveData<FavoriteRoom?>

    @Query("SELECT * FROM favorite_room ORDER BY roomId DESC")
    fun getAll(): LiveData<List<FavoriteRoom>>

    @Query("DELETE FROM favorite_room")
    fun truncate()
}