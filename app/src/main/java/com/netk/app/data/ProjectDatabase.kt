package com.netk.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val pdfPath: String,
)

@Entity(
    tableName = "project_images",
    foreignKeys = [ForeignKey(
        entity = ProjectEntity::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("projectId")],
)
data class ProjectImageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val position: Int,
    val localPath: String,
    val displayName: String,
)

data class ProjectWithImages(
    @Embedded val project: ProjectEntity,
    @Relation(parentColumn = "id", entityColumn = "projectId")
    val images: List<ProjectImageEntity>,
) {
    val orderedImages: List<ProjectImageEntity> get() = images.sortedBy { it.position }
}

@Dao
interface ProjectDao {
    @Transaction
    @Query("SELECT * FROM projects ORDER BY createdAt DESC")
    fun observeProjects(): Flow<List<ProjectWithImages>>

    @Insert
    suspend fun insertProject(project: ProjectEntity): Long

    @Insert
    suspend fun insertImages(images: List<ProjectImageEntity>)
}

@Database(entities = [ProjectEntity::class, ProjectImageEntity::class], version = 2, exportSchema = false)
abstract class ProjectDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao

    companion object {
        @Volatile private var instance: ProjectDatabase? = null

        fun get(context: Context): ProjectDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                ProjectDatabase::class.java,
                "pdf_kanto.db",
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE project_images_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        projectId INTEGER NOT NULL,
                        position INTEGER NOT NULL,
                        localPath TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        FOREIGN KEY(projectId) REFERENCES projects(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO project_images_new (id, projectId, position, localPath, displayName)
                    SELECT id, projectId, position,
                        CASE WHEN uri LIKE 'file://%' THEN substr(uri, 8) ELSE uri END,
                        displayName
                    FROM project_images
                """.trimIndent())
                db.execSQL("DROP TABLE project_images")
                db.execSQL("ALTER TABLE project_images_new RENAME TO project_images")
                db.execSQL("CREATE INDEX index_project_images_projectId ON project_images(projectId)")
            }
        }
    }
}
