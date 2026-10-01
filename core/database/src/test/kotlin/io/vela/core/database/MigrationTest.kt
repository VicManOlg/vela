package io.vela.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The database is the user's library: every released schema must migrate to the current one. */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), VelaDatabase::class.java)

    // Room's driver compares the name it was opened with against the resolved path, so give it the
    // resolved path from the start.
    private val DB = InstrumentationRegistry.getInstrumentation().targetContext.getDatabasePath("migration-test").absolutePath

    @Test
    fun `version 1 migrates to the current schema keeping play data`() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL(
                """INSERT INTO games (id, platformId, kind, title, sortTitle, locationType, locationValue, fileName,
                   fileSize, lastModified, extension, favorite, hidden, playCount, totalPlayTimeMs, addedAt,
                   completion, userRating, duplicateKey)
                   VALUES (1, 'snes', 'ROM', 'Chrono Trigger', 'chrono trigger', 'FILE', '/roms/snes/ct.sfc', 'ct.sfc',
                   4194304, 1, 'sfc', 1, 0, 7, 3600000, 1, 'COMPLETED', 5, 'snes:chrono trigger')""",
            )
            db.execSQL("INSERT INTO play_sessions (gameId, startedAt, endedAt) VALUES (1, 10, 20)")
        }

        helper.runMigrationsAndValidate(DB, CURRENT, true, MIGRATION_1_2).use { db ->
            db.query("SELECT favorite, playCount, totalPlayTimeMs, userRating, completion FROM games WHERE id = 1").use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getInt(0)).isEqualTo(1)
                assertThat(c.getInt(1)).isEqualTo(7)
                assertThat(c.getLong(2)).isEqualTo(3_600_000L)
                assertThat(c.getInt(3)).isEqualTo(5)
                assertThat(c.getString(4)).isEqualTo("COMPLETED")
            }
            db.query("SELECT COUNT(*) FROM play_sessions").use { c ->
                c.moveToFirst()
                assertThat(c.getInt(0)).isEqualTo(1)
            }
        }
    }

    private companion object {
        const val CURRENT = 4
    }
}
