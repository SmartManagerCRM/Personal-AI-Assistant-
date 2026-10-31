package com.smartmanager.ai.di

import android.content.Context
import androidx.room.Room
import com.smartmanager.ai.data.AppDatabase
import com.smartmanager.ai.engine.AssistantController
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun db(@ApplicationContext ctx: Context): AppDatabase = Room.databaseBuilder(ctx, AppDatabase::class.java, "smartmanager.db").fallbackToDestructiveMigration().build()
}

/** For framework-instantiated components (receivers, assistant session) that cannot use constructor injection. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ControllerEntryPoint { fun controller(): AssistantController; fun db(): AppDatabase }
