package app.mimusica.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        FavoriteEntity::class,
        HistoryEntity::class,
        CachedTrackEntity::class,
        DownloadEntity::class,
        ImportedPlaylistEntity::class,
        ImportedTrackEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cachedTrackDao(): CachedTrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
    abstract fun downloadDao(): DownloadDao
    abstract fun importDao(): ImportDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "mimusica.db")
                .build()
    }
}
