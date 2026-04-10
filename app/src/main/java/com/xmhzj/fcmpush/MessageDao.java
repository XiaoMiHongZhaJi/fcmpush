package com.xmhzj.fcmpush;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(MessageModel message);

    @Query("SELECT * FROM messages ORDER BY sendTimestamp DESC, id ASC")
    List<MessageModel> getAll();

    @Delete
    void delete(MessageModel message);

    @Query("DELETE FROM messages")
    void clear();
}
