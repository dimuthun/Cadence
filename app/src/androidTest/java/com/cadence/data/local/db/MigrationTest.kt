package com.cadence.data.local.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies every schema version migrates without losing data, using the exported schemas. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), CadenceDatabase::class.java)

    @Test
    fun migrate1To2KeepsMacrosAndAddsVariables() {
        helper.createDatabase(DB_NAME, 1).apply {
            execSQL(
                "INSERT INTO macros (id, name, enabled, category, triggers_json, actions_json, constraints_json, created_at, updated_at) " +
                    "VALUES (1, 'Kept', 1, 'General', '[]', '[]', '[]', 0, 0)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_NAME, 2, true)

        db.query("SELECT name FROM macros").use { cursor ->
            cursor.moveToFirst()
            assertEquals("Kept", cursor.getString(0))
        }
        db.execSQL("INSERT INTO variables (name, value, updated_at) VALUES ('counter', '1', 0)")
        db.query("SELECT COUNT(*) FROM variables").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
    }

    private companion object {
        const val DB_NAME = "migration-test.db"
    }
}
