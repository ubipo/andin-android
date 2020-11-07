package net.pfiers.andin.db

import android.content.Context
import androidx.room.*

@Database(
    entities = [FavoriteRoom::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AndinDb : RoomDatabase() {
    abstract val dao: AndinDao

    companion object {
        @Volatile
        private var INSTANCE: AndinDb? = null

        fun getInstance(context: Context): AndinDb {
            synchronized(this) {
                var instance = INSTANCE

                if (instance == null) {
                    instance = Room.databaseBuilder(
                        context.applicationContext,
                        AndinDb::class.java,
                        "andin_db"
                    )
                        .fallbackToDestructiveMigration()
                        .build()
                    INSTANCE = instance
                }
                return instance
            }
        }
    }
}