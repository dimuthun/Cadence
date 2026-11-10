package com.cadence.di

import com.cadence.core.coroutines.ApplicationScope
import com.cadence.core.coroutines.DefaultDispatcher
import com.cadence.core.coroutines.IoDispatcher
import com.cadence.core.time.SystemDefaultZoneClock
import com.cadence.data.serialization.CadenceJson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json

/** App-wide infrastructure: clock, JSON, dispatchers, and the application scope. */
@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    @Provides
    fun provideClock(): Clock = SystemDefaultZoneClock

    @Provides
    fun provideJson(): Json = CadenceJson.instance

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
}
