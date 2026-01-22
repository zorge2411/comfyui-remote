package com.example.comfyui_remote.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkflowDao {
    @Query("SELECT * FROM workflows ORDER BY createdAt DESC")
    fun getAll(): Flow<List<WorkflowEntity>>

    @Insert
    suspend fun insert(workflow: WorkflowEntity)

    @Update
    suspend fun update(workflow: WorkflowEntity)

    @Delete
    suspend fun delete(workflow: WorkflowEntity)
}

@Database(entities = [WorkflowEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workflowDao(): WorkflowDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "comfy_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
