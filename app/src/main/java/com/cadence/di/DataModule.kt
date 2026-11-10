package com.cadence.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.cadence.core.coroutines.ApplicationScope
import com.cadence.core.coroutines.IoDispatcher
import com.cadence.data.backup.RoomMacroBackupRepository
import com.cadence.data.local.datastore.DataStoreSettingsRepository
import com.cadence.data.local.db.CadenceDatabase
import com.cadence.data.local.db.dao.ExecutionDao
import com.cadence.data.local.db.dao.GlobalVariableDao
import com.cadence.data.local.db.dao.MacroDao
import com.cadence.data.repository.RoomExecutionLogRepository
import com.cadence.data.repository.RoomGlobalVariableRepository
import com.cadence.domain.variable.GlobalVariableRepository
import com.cadence.data.repository.RoomMacroRepository
import com.cadence.domain.repository.ExecutionLogRepository
import com.cadence.domain.repository.MacroBackupRepository
import com.cadence.domain.repository.MacroRepository
import com.cadence.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.plus

/** Room database, DataStore, and DAOs. */
@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CadenceDatabase =
        Room.databaseBuilder(context, CadenceDatabase::class.java, CadenceDatabase.NAME).build()

    @Provides
    fun provideMacroDao(database: CadenceDatabase): MacroDao = database.macroDao()

    @Provides
    fun provideExecutionDao(database: CadenceDatabase): ExecutionDao = database.executionDao()

    @Provides
    fun provideGlobalVariableDao(database: CadenceDatabase): GlobalVariableDao = database.globalVariableDao()

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = scope + ioDispatcher,
        produceFile = { context.preferencesDataStoreFile(DataStoreSettingsRepository.FILE_NAME) },
    )
}

/** Binds domain repository interfaces to their data-layer implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindMacroRepository(impl: RoomMacroRepository): MacroRepository

    @Binds
    abstract fun bindExecutionLogRepository(impl: RoomExecutionLogRepository): ExecutionLogRepository

    @Binds
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    abstract fun bindMacroBackupRepository(impl: RoomMacroBackupRepository): MacroBackupRepository

    @Binds
    abstract fun bindGlobalVariableRepository(impl: RoomGlobalVariableRepository): GlobalVariableRepository
}
