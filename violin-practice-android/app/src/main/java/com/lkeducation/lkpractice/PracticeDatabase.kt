package com.lkeducation.lkpractice

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

@Entity(tableName = "households")
internal data class HouseholdEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val activeChildProfileId: String,
)

@Entity(
    tableName = "child_profiles",
    foreignKeys = [
        ForeignKey(
            entity = HouseholdEntity::class,
            parentColumns = ["id"],
            childColumns = ["householdId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["householdId"]),
        Index(value = ["householdId", "sortOrder"]),
    ],
)
internal data class ChildProfileEntity(
    @PrimaryKey
    val id: String,
    val householdId: String,
    val name: String,
    val instrument: String,
    val excelWorkbookUri: String?,
    val excelWorkbookName: String?,
    val sortOrder: Int,
)

@Entity(
    tableName = "assignments",
    foreignKeys = [
        ForeignKey(
            entity = ChildProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["childProfileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["childProfileId"]),
        Index(value = ["childProfileId", "startDate", "endDate"]),
        Index(value = ["childProfileId", "sortOrder"]),
    ],
)
internal data class AssignmentEntity(
    @PrimaryKey
    val id: String,
    val childProfileId: String,
    val startDate: String,
    val endDate: String,
    val title: String,
    val source: String,
    val sourceType: String,
    val startSeconds: Int,
    val endSeconds: Int?,
    val repetitions: Int,
    val coachNote: String,
    val whyImportant: String,
    val sheetMusicUri: String?,
    val teacherId: String?,
    val instrument: String,
    val assignmentType: String,
    val goalType: String,
    val targetSeconds: Int,
    val enabledModesJson: String,
    val revision: Int,
    val archivedAtMillis: Long?,
    val sortOrder: Int,
)

@Entity(
    tableName = "assignment_history",
    primaryKeys = ["assignmentId", "revision"],
    foreignKeys = [
        ForeignKey(
            entity = ChildProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["childProfileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["childProfileId"]),
        Index(value = ["assignmentId"]),
    ],
)
internal data class AssignmentHistoryEntity(
    val assignmentId: String,
    val revision: Int,
    val childProfileId: String,
    val changedAtMillis: Long,
    val changedFieldsJson: String,
    val previousAssignmentJson: String,
)

@Entity(
    tableName = "daily_progress",
    primaryKeys = ["childProfileId", "date"],
    foreignKeys = [
        ForeignKey(
            entity = ChildProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["childProfileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["childProfileId", "date"])],
)
internal data class DailyProgressEntity(
    val childProfileId: String,
    val date: String,
    val completedTaskIdsJson: String,
    val repetitionsByTaskJson: String,
    val elapsedSeconds: Long,
)

@Entity(
    tableName = "practice_events",
    foreignKeys = [
        ForeignKey(
            entity = ChildProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["childProfileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["childProfileId"]),
        Index(value = ["childProfileId", "date"]),
        Index(value = ["childProfileId", "sortOrder"]),
        Index(value = ["sessionId"]),
    ],
)
internal data class PracticeEventEntity(
    @PrimaryKey
    val id: String,
    val childProfileId: String,
    val date: String,
    val taskId: String?,
    val taskTitle: String,
    val event: String,
    val atMillis: Long,
    val videoUri: String?,
    val videoPath: String?,
    val videoFileName: String?,
    val sessionId: String?,
    val sortOrder: Long,
)

@Entity(
    tableName = "practice_sessions",
    foreignKeys = [
        ForeignKey(
            entity = ChildProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["childProfileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["childProfileId"]),
        Index(value = ["childProfileId", "date"]),
        Index(value = ["childProfileId", "sortOrder"]),
        Index(value = ["assignmentId"]),
    ],
)
internal data class PracticeSessionEntity(
    @PrimaryKey
    val id: String,
    val childProfileId: String,
    val assignmentId: String,
    val assignmentTitle: String,
    val instrument: String,
    val date: String,
    val mode: String,
    val modesUsedJson: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long?,
    val repetitionsCompleted: Int,
    val status: String,
    val assignmentRevision: Int,
    val assignmentType: String,
    val goalType: String,
    val targetRepetitions: Int,
    val targetSeconds: Int,
    val elapsedSeconds: Long,
    val reflection: String?,
    val masteryStatus: String,
    val studentInitiated: Boolean,
    val sortOrder: Long,
)

internal data class RecordingPathReference(
    val childProfileId: String,
    val videoPath: String?,
)

@Dao
internal interface HouseholdDao {
    @Query("SELECT * FROM households LIMIT 1")
    fun load(): HouseholdEntity?

    @Upsert
    fun upsert(household: HouseholdEntity)

    @Query(
        "UPDATE households SET activeChildProfileId = :childProfileId " +
            "WHERE id = :householdId",
    )
    fun updateActiveChild(householdId: String, childProfileId: String)
}

@Dao
internal interface ChildProfileDao {
    @Query(
        "SELECT * FROM child_profiles WHERE householdId = :householdId " +
            "ORDER BY sortOrder, id",
    )
    fun loadForHousehold(householdId: String): List<ChildProfileEntity>

    @Query("SELECT * FROM child_profiles WHERE id = :childProfileId LIMIT 1")
    fun find(childProfileId: String): ChildProfileEntity?

    @Upsert
    fun upsert(profile: ChildProfileEntity)

    @Upsert
    fun upsertAll(profiles: List<ChildProfileEntity>)

    @Query("DELETE FROM child_profiles WHERE id = :childProfileId")
    fun delete(childProfileId: String)

    @Query(
        "DELETE FROM child_profiles WHERE householdId = :householdId " +
            "AND id NOT IN (:retainedIds)",
    )
    fun deleteExcept(householdId: String, retainedIds: List<String>)
}

@Dao
internal interface AssignmentDao {
    @Query(
        "SELECT * FROM assignments WHERE childProfileId = :childProfileId " +
            "ORDER BY sortOrder, id",
    )
    fun loadForProfile(childProfileId: String): List<AssignmentEntity>

    @Upsert
    fun upsertAll(assignments: List<AssignmentEntity>)

    @Query("DELETE FROM assignments WHERE id IN (:ids)")
    fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM assignments WHERE childProfileId = :childProfileId")
    fun deleteForProfile(childProfileId: String)
}

@Dao
internal interface AssignmentHistoryDao {
    @Query(
        "SELECT * FROM assignment_history WHERE childProfileId = :childProfileId " +
            "ORDER BY changedAtMillis, revision",
    )
    fun loadForProfile(childProfileId: String): List<AssignmentHistoryEntity>

    @Upsert
    fun upsertAll(history: List<AssignmentHistoryEntity>)

    @Delete
    fun deleteAll(history: List<AssignmentHistoryEntity>)

    @Query("DELETE FROM assignment_history WHERE childProfileId = :childProfileId")
    fun deleteForProfile(childProfileId: String)
}

@Dao
internal interface DailyProgressDao {
    @Query(
        "SELECT * FROM daily_progress WHERE childProfileId = :childProfileId " +
            "ORDER BY date",
    )
    fun loadForProfile(childProfileId: String): List<DailyProgressEntity>

    @Upsert
    fun upsertAll(progress: List<DailyProgressEntity>)

    @Query(
        "DELETE FROM daily_progress WHERE childProfileId = :childProfileId " +
            "AND date IN (:dates)",
    )
    fun deleteByDates(childProfileId: String, dates: List<String>)

    @Query("DELETE FROM daily_progress WHERE childProfileId = :childProfileId")
    fun deleteForProfile(childProfileId: String)
}

@Dao
internal interface PracticeEventDao {
    @Query(
        "SELECT * FROM practice_events WHERE childProfileId = :childProfileId " +
            "ORDER BY sortOrder, id",
    )
    fun loadForProfile(childProfileId: String): List<PracticeEventEntity>

    @Query(
        "SELECT childProfileId, videoPath FROM practice_events " +
            "WHERE videoPath IS NOT NULL",
    )
    fun loadRecordingReferences(): List<RecordingPathReference>

    @Upsert
    fun upsertAll(events: List<PracticeEventEntity>)

    @Query("DELETE FROM practice_events WHERE id IN (:ids)")
    fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM practice_events WHERE childProfileId = :childProfileId")
    fun deleteForProfile(childProfileId: String)
}

@Dao
internal interface PracticeSessionDao {
    @Query(
        "SELECT * FROM practice_sessions WHERE childProfileId = :childProfileId " +
            "ORDER BY sortOrder, id",
    )
    fun loadForProfile(childProfileId: String): List<PracticeSessionEntity>

    @Upsert
    fun upsertAll(sessions: List<PracticeSessionEntity>)

    @Query("DELETE FROM practice_sessions WHERE id IN (:ids)")
    fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM practice_sessions WHERE childProfileId = :childProfileId")
    fun deleteForProfile(childProfileId: String)
}

@Database(
    entities = [
        HouseholdEntity::class,
        ChildProfileEntity::class,
        AssignmentEntity::class,
        AssignmentHistoryEntity::class,
        DailyProgressEntity::class,
        PracticeEventEntity::class,
        PracticeSessionEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
internal abstract class PracticeDatabase : RoomDatabase() {
    abstract fun householdDao(): HouseholdDao

    abstract fun childProfileDao(): ChildProfileDao

    abstract fun assignmentDao(): AssignmentDao

    abstract fun assignmentHistoryDao(): AssignmentHistoryDao

    abstract fun dailyProgressDao(): DailyProgressDao

    abstract fun practiceEventDao(): PracticeEventDao

    abstract fun practiceSessionDao(): PracticeSessionDao
}

internal interface DatabaseRunner {
    fun <T> run(block: () -> T): T
}

internal object DirectDatabaseRunner : DatabaseRunner {
    override fun <T> run(block: () -> T): T = block()
}

private class ExecutorDatabaseRunner : DatabaseRunner {
    private val workerThread = AtomicReference<Thread?>()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "lk-practice-room").also(workerThread::set)
    }

    override fun <T> run(block: () -> T): T {
        if (Thread.currentThread() == workerThread.get()) return block()
        return try {
            executor.submit(Callable(block)).get()
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw error
        } catch (error: ExecutionException) {
            throw error.cause ?: error
        }
    }
}

internal data class RoomStoreDependencies(
    val database: PracticeDatabase,
    val runner: DatabaseRunner,
)

internal object PracticeDatabaseProvider {
    @Volatile
    private var database: PracticeDatabase? = null

    private val runner: DatabaseRunner = ExecutorDatabaseRunner()

    fun get(context: Context): RoomStoreDependencies {
        val appContext = context.applicationContext
        val existing = database
        if (existing != null) return RoomStoreDependencies(existing, runner)
        return synchronized(this) {
            val resolved = database ?: Room.databaseBuilder(
                appContext,
                PracticeDatabase::class.java,
                PRACTICE_DATABASE_NAME,
            ).addMigrations(
                PRACTICE_DATABASE_MIGRATION_1_2,
                PRACTICE_DATABASE_MIGRATION_2_3,
            )
                .build()
                .also { database = it }
            RoomStoreDependencies(resolved, runner)
        }
    }
}

internal val PRACTICE_DATABASE_MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `assignments` ADD COLUMN `teacherId` TEXT")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `assignment_history` (
                `assignmentId` TEXT NOT NULL,
                `revision` INTEGER NOT NULL,
                `childProfileId` TEXT NOT NULL,
                `changedAtMillis` INTEGER NOT NULL,
                `changedFieldsJson` TEXT NOT NULL,
                `previousAssignmentJson` TEXT NOT NULL,
                PRIMARY KEY(`assignmentId`, `revision`),
                FOREIGN KEY(`childProfileId`) REFERENCES `child_profiles`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_assignment_history_childProfileId` " +
                "ON `assignment_history` (`childProfileId`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_assignment_history_assignmentId` " +
                "ON `assignment_history` (`assignmentId`)",
        )
    }
}

internal val PRACTICE_DATABASE_MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `assignments_new` (
                `id` TEXT NOT NULL,
                `childProfileId` TEXT NOT NULL,
                `startDate` TEXT NOT NULL,
                `endDate` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `source` TEXT NOT NULL,
                `sourceType` TEXT NOT NULL,
                `startSeconds` INTEGER NOT NULL,
                `endSeconds` INTEGER,
                `repetitions` INTEGER NOT NULL,
                `coachNote` TEXT NOT NULL,
                `whyImportant` TEXT NOT NULL,
                `sheetMusicUri` TEXT,
                `teacherId` TEXT,
                `instrument` TEXT NOT NULL,
                `assignmentType` TEXT NOT NULL,
                `goalType` TEXT NOT NULL,
                `targetSeconds` INTEGER NOT NULL,
                `enabledModesJson` TEXT NOT NULL,
                `revision` INTEGER NOT NULL,
                `archivedAtMillis` INTEGER,
                `sortOrder` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`childProfileId`) REFERENCES `child_profiles`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `assignments_new` (
                `id`, `childProfileId`, `startDate`, `endDate`, `title`, `source`,
                `sourceType`, `startSeconds`, `endSeconds`, `repetitions`, `coachNote`,
                `whyImportant`, `sheetMusicUri`, `teacherId`, `instrument`,
                `assignmentType`, `goalType`, `targetSeconds`, `enabledModesJson`,
                `revision`, `archivedAtMillis`, `sortOrder`
            )
            SELECT
                `id`, `childProfileId`, `assignedDate`, `assignedDate`, `title`, `source`,
                `sourceType`, `startSeconds`, `endSeconds`, `repetitions`, `coachNote`,
                `whyImportant`, `sheetMusicUri`, `teacherId`, `instrument`,
                `assignmentType`, `goalType`, `targetSeconds`, `enabledModesJson`,
                `revision`, `archivedAtMillis`, `sortOrder`
            FROM `assignments`
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `assignments`")
        db.execSQL("ALTER TABLE `assignments_new` RENAME TO `assignments`")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_assignments_childProfileId` " +
                "ON `assignments` (`childProfileId`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_assignments_childProfileId_startDate_endDate` " +
                "ON `assignments` (`childProfileId`, `startDate`, `endDate`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_assignments_childProfileId_sortOrder` " +
                "ON `assignments` (`childProfileId`, `sortOrder`)",
        )
        db.execSQL(
            "ALTER TABLE `practice_sessions` ADD COLUMN " +
                "`studentInitiated` INTEGER NOT NULL DEFAULT 0",
        )
    }
}

private const val PRACTICE_DATABASE_NAME = "lk_practice.db"
