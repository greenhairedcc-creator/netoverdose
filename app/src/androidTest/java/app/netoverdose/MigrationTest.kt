package app.netoverdose

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.netoverdose.data.MIGRATION_1_2
import app.netoverdose.data.MIGRATION_2_3
import app.netoverdose.data.MIGRATION_3_4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real migrations against a real SQLite file and validates the result against the
 * schema Room generated for v4. Fails if the handwritten SQL drifts from the entity classes.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        "app.netoverdose.data.AppDatabase"
    )

    @Test
    fun migratesOneToFourKeepsData() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO tasks (title, difficulty, windowStart, deadline, status, " +
                    "remindOnWindowStart, createdAt, startedAt, closedAt) " +
                    "VALUES ('Старая задача', 4, 100, 200, 'PLANNED', 1, 50, NULL, NULL)"
            )
        }
        helper.runMigrationsAndValidate(DB, 2, true, MIGRATION_1_2)
        helper.runMigrationsAndValidate(DB, 3, true, MIGRATION_1_2, MIGRATION_2_3).use { db ->
            // v3 added NOT NULL situation/hits/lastHitAt, so they must be supplied here.
            db.execSQL(
                "INSERT INTO ideas (text, createdAt, situation, hits, lastHitAt) " +
                    "VALUES ('Идея', 700, '', 1, 700)"
            )
        }
        helper.runMigrationsAndValidate(DB, 4, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .use { db ->
                db.query("SELECT title, difficulty, orderIndex, sprintStartedAt FROM tasks").use { c ->
                    check(c.moveToFirst()) { "task lost during migration" }
                    check(c.getString(0) == "Старая задача") { "task title lost" }
                    check(c.getInt(1) == 4) { "difficulty lost" }
                    check(c.getInt(2) == 0) { "orderIndex should default to 0" }
                    check(c.isNull(3)) { "sprintStartedAt should default to NULL" }
                }
                db.query("SELECT text, situation, hits FROM ideas").use { c ->
                    check(c.moveToFirst()) { "idea lost" }
                    check(c.getString(0) == "Идея") { "idea text lost" }
                    check(c.getString(1) == "") { "situation default" }
                    check(c.getInt(2) == 1) { "hits default" }
                }
                db.query("SELECT COUNT(*) FROM notes").use { c ->
                    c.moveToFirst()
                    check(c.getInt(0) == 0) { "notes should start empty" }
                }
            }
    }

    private companion object {
        const val DB = "migration-test"
    }
}
