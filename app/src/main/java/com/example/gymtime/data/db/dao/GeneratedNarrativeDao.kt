package com.example.gymtime.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.gymtime.data.db.entity.GeneratedNarrative

@Dao
interface GeneratedNarrativeDao {
    @Query("SELECT * FROM generated_narratives WHERE kind = :kind AND subjectKey = :subjectKey LIMIT 1")
    suspend fun get(kind: String, subjectKey: String): GeneratedNarrative?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(narrative: GeneratedNarrative)

    @Query("DELETE FROM generated_narratives WHERE kind = :kind AND subjectKey = :subjectKey")
    suspend fun delete(kind: String, subjectKey: String)

    @Query(
        """DELETE FROM generated_narratives
           WHERE kind = :kind
             AND subjectStartEpochMs IS NOT NULL
             AND subjectStartEpochMs < :cutoffEpochMs"""
    )
    suspend fun deletePeriodicBefore(kind: String, cutoffEpochMs: Long)
}
