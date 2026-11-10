package com.cadence.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cadence.data.local.db.dao.MacroDao
import com.cadence.data.local.db.entity.MacroEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MacroDaoTest {

    private lateinit var db: CadenceDatabase
    private lateinit var dao: MacroDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CadenceDatabase::class.java)
            .build()
        dao = db.macroDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entity(name: String, enabled: Boolean = true, category: String = "General") = MacroEntity(
        name = name,
        enabled = enabled,
        category = category,
        triggersJson = "[]",
        actionsJson = "[]",
        constraintsJson = "[]",
        createdAt = 1_000,
        updatedAt = 1_000,
    )

    @Test
    fun insertAndGetById() = runTest {
        val id = dao.insert(entity("Morning"))

        assertEquals(entity("Morning").copy(id = id), dao.getById(id))
    }

    @Test
    fun observeAllIsSortedByNameIgnoringCase() = runTest {
        dao.insert(entity("beta"))
        dao.insert(entity("Alpha"))
        dao.insert(entity("Gamma"))

        assertEquals(listOf("Alpha", "beta", "Gamma"), dao.observeAll().first().map { it.name })
    }

    @Test
    fun observeEnabledExcludesDisabledMacros() = runTest {
        dao.insert(entity("On", enabled = true))
        dao.insert(entity("Off", enabled = false))

        assertEquals(listOf("On"), dao.observeEnabled().first().map { it.name })
    }

    @Test
    fun setEnabledUpdatesFlagAndTimestamp() = runTest {
        val id = dao.insert(entity("Toggle"))

        assertEquals(1, dao.setEnabled(id, enabled = false, updatedAt = 5_000))

        val updated = dao.getById(id)!!
        assertFalse(updated.enabled)
        assertEquals(5_000, updated.updatedAt)
        assertEquals(1_000, updated.createdAt)
    }

    @Test
    fun updateOfMissingRowReturnsZero() = runTest {
        assertEquals(0, dao.update(entity("Ghost").copy(id = 42)))
    }

    @Test
    fun deleteRemovesTheRow() = runTest {
        val id = dao.insert(entity("Temp"))

        assertEquals(1, dao.deleteById(id))
        assertNull(dao.getById(id))
    }

    @Test
    fun categoriesAreDistinctAndSorted() = runTest {
        dao.insert(entity("A", category = "Work"))
        dao.insert(entity("B", category = "home"))
        dao.insert(entity("C", category = "Work"))

        assertEquals(listOf("home", "Work"), dao.observeCategories().first())
    }

    @Test
    fun insertAllReturnsIdsInOrder() = runTest {
        val ids = dao.insertAll(listOf(entity("One"), entity("Two")))

        assertEquals(listOf("One", "Two"), ids.map { dao.getById(it)!!.name })
    }
}
