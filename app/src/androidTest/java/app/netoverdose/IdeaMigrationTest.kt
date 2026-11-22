package app.netoverdose

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.netoverdose.data.MIGRATION_1_2
import app.netoverdose.data.MIGRATION_2_3
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IdeaMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        "app.netoverdose.data.AppDatabase"
    )

    @Test
    fun migrates1to3KeepingIdeasAndTasks() {
        helper.createDatabase("migration-test", 1).use { db ->
            db.execSQL(
                "INSERT INTO tasks (title, difficulty, windowStart, deadline, status, " +
                    "remindOnWindowStart, createdAt, startedAt, closedAt) " +
                    "VALUES ('T', 3, 0, 1, 'PLANNED', 1, 0, NULL, NULL)"
            )
        }
        helper.runMigrationsAndValidate("migration-test", 2, true, MIGRATION_1_2).use { db ->
            db.execSQL("INSERT INTO ideas (text, createdAt) VALUES ('Старая идея', 123)")
        }
        helper.runMigrationsAndValidate("migration-test", 3, true, MIGRATION_1_2, MIGRATION_2_3)
            .use { db ->
                db.query("SELECT text, situation, hits, lastHitAt FROM ideas").use { cursor ->
                    check(cursor.moveToFirst()) { "ideas row lost" }
                    check(cursor.getString(0) == "Старая идея") { "text lost" }
                    check(cursor.getString(1) == "") { "situation should default to empty" }
                    check(cursor.getInt(2) == 1) { "hits should default to 1" }
                    check(cursor.getLong(3) == 123L) { "lastHitAt should seed from createdAt" }
                }
                db.query("SELECT COUNT(*) FROM tasks").use { cursor ->
                    cursor.moveToFirst()
                    check(cursor.getInt(0) == 1) { "task lost" }
                }
            }
    }
}
