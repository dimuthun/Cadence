package com.cadence.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cadence.domain.model.AppSettings
import com.cadence.domain.model.ThemeMode
import com.cadence.domain.permission.PermissionRequirement
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataStoreSettingsRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var file: File
    private lateinit var repository: DataStoreSettingsRepository

    @Before
    fun setUp() {
        file = File(context.cacheDir, "settings-test-${UUID.randomUUID()}.preferences_pb")
        repository = DataStoreSettingsRepository(
            PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }),
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
        file.delete()
    }

    @Test
    fun emptyStoreEmitsDefaults() = runTest {
        assertEquals(AppSettings(), repository.settings.first())
    }

    @Test
    fun valuesArePersisted() = runTest {
        repository.setThemeMode(ThemeMode.DARK)
        repository.setDynamicColor(true)
        repository.setLogRetentionDays(30)
        repository.setOnboardingCompleted(true)
        repository.markPermissionExplained(PermissionRequirement.USAGE_ACCESS)
        repository.markPermissionExplained(PermissionRequirement.EXACT_ALARM)

        assertEquals(
            AppSettings(
                themeMode = ThemeMode.DARK,
                dynamicColor = true,
                logRetentionDays = 30,
                onboardingCompleted = true,
                explainedPermissions = setOf(PermissionRequirement.USAGE_ACCESS, PermissionRequirement.EXACT_ALARM),
            ),
            repository.settings.first(),
        )
    }

    @Test
    fun logRetentionIsClamped() = runTest {
        repository.setLogRetentionDays(0)
        assertEquals(DataStoreSettingsRepository.MIN_RETENTION_DAYS, repository.settings.first().logRetentionDays)

        repository.setLogRetentionDays(10_000)
        assertEquals(DataStoreSettingsRepository.MAX_RETENTION_DAYS, repository.settings.first().logRetentionDays)
    }
}
