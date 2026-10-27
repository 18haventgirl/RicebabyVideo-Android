package com.ricebaby.video.di

import android.content.Context
import androidx.room.Room
import com.ricebaby.video.data.db.FavoriteDao
import com.ricebaby.video.data.db.HistoryDao
import com.ricebaby.video.data.db.RicebabyDatabase
import com.ricebaby.video.data.db.SearchHistoryDao
import com.ricebaby.video.data.db.SourceDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): RicebabyDatabase =
        Room.databaseBuilder(context, RicebabyDatabase::class.java, "ricebaby.db").build()

    @Provides fun sourceDao(db: RicebabyDatabase): SourceDao = db.sourceDao()
    @Provides fun favoriteDao(db: RicebabyDatabase): FavoriteDao = db.favoriteDao()
    @Provides fun historyDao(db: RicebabyDatabase): HistoryDao = db.historyDao()
    @Provides fun searchHistoryDao(db: RicebabyDatabase): SearchHistoryDao = db.searchHistoryDao()
}
