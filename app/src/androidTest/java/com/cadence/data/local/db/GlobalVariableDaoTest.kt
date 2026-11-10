package com.cadence.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cadence.data.local.db.dao.GlobalVariableDao
import com.cadence.data.local.db.entity.GlobalVariableEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GlobalVariableDaoTest {

    private lateinit var db: CadenceDatabase
    private lateinit var dao: GlobalVariableDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CadenceDatabase::class.java).build()
        dao = db.globalVariableDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun upsertInsertsThenReplaces() = runTest {
        dao.upsert(GlobalVariableEntity("counter", "1", 100))
        dao.upsert(GlobalVariableEntity("counter", "2", 200))

        assertEquals("2", dao.getValue("counter"))
        assertEquals(listOf(GlobalVariableEntity("counter", "2", 200)), dao.observeAll().first())
    }

    @Test
    fun listIsSortedIgnoringCase() = runTest {
        dao.upsert(GlobalVariableEntity("beta", "", 0))
        dao.upsert(GlobalVariableEntity("Alpha", "", 0))

        assertEquals(listOf("Alpha", "beta"), dao.observeAll().first().map { it.name })
    }

    @Test
    fun deleteRemovesTheVariable() = runTest {
        dao.upsert(GlobalVariableEntity("temp", "x", 0))

        assertEquals(1, dao.delete("temp"))
        assertNull(dao.getValue("temp"))
    }
}
