package com.example.photostorage.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public interface MediaItemDao {
    @Query("SELECT * FROM media_items")
    List<MediaItemEntity> getAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<MediaItemEntity> items);

    @Query("DELETE FROM media_items WHERE scanVersion != :activeVersion")
    void deleteNotSeenIn(long activeVersion);

    @Query("DELETE FROM media_items")
    void clear();

    @Transaction
    default void replaceScan(List<MediaItemEntity> items, long activeVersion) {
        if (!items.isEmpty()) upsertAll(items);
        deleteNotSeenIn(activeVersion);
    }
}
