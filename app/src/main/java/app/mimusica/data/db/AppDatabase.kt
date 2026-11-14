package app.mimusica.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        LyricsEntity::class,
        PodcastEntity::class,
        EpisodeProgressEntity::class,
        TrackOverrideEntity::class,
        AlbumArtEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cachedTrackDao(): CachedTrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
    abstract fun downloadDao(): DownloadDao
    abstract fun importDao(): ImportDao
    abstract fun lyricsDao(): LyricsDao
    abstract fun podcastDao(): PodcastDao
    abstract fun overrideDao(): OverrideDao
    abstract fun albumArtDao(): AlbumArtDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "mimusica.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /** v2: letras, podcasts, progreso de episodios, datos corregidos y carátulas encontradas. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `lyrics` (`trackKey` TEXT NOT NULL, `synced` TEXT, `plain` TEXT, `found` INTEGER NOT NULL, `fetchedAt` INTEGER NOT NULL, PRIMARY KEY(`trackKey`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `podcasts` (`feedUrl` TEXT NOT NULL, `title` TEXT NOT NULL, `author` TEXT NOT NULL, `artworkUrl` TEXT, `subscribedAt` INTEGER NOT NULL, PRIMARY KEY(`feedUrl`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `episode_progress` (`trackKey` TEXT NOT NULL, `positionMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`trackKey`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `track_overrides` (`trackKey` TEXT NOT NULL, `title` TEXT, `artist` TEXT, `album` TEXT, `artworkUri` TEXT, PRIMARY KEY(`trackKey`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `album_art` (`albumKey` TEXT NOT NULL, `url` TEXT, `checkedAt` INTEGER NOT NULL, PRIMARY KEY(`albumKey`))")
            }
        }
    }
}
