package com.oc.catemoji.catoc.data.datalocal.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.oc.catemoji.catoc.data.datalocal.room.dao.DogDao
import com.oc.catemoji.catoc.data.datalocal.room.dao.HistoryDao
import com.oc.catemoji.catoc.data.datalocal.room.dao.UserDao
import com.oc.catemoji.catoc.data.datalocal.room.dao.ProfileDao
import com.oc.catemoji.catoc.data.datalocal.room.entity.AchievementEntity
import com.oc.catemoji.catoc.data.datalocal.room.entity.DogEntity
import com.oc.catemoji.catoc.data.datalocal.room.entity.HistoryEntity
import com.oc.catemoji.catoc.data.datalocal.room.entity.UserEntity

@Database(
    entities = [UserEntity::class, DogEntity::class, HistoryEntity::class, AchievementEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun dogDao(): DogDao
    abstract fun historyDao(): HistoryDao
    abstract fun profileDao(): ProfileDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE history_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        nameBatter TEXT NOT NULL,
                        date INTEGER NOT NULL,
                        imageAvatar1 TEXT NOT NULL,
                        imageAvatar2 TEXT NOT NULL,
                        typePlay INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO history_new (id, nameBatter, date, imageAvatar1, imageAvatar2, typePlay)
                    SELECT id, nameBatter, date, imageAvatar1, imageAvatar2,
                        CASE WHEN LOWER(TRIM(typePlay)) IN ('victory', 'win', 'true', '1')
                            THEN 1 ELSE 0 END
                    FROM history
                """.trimIndent())
                db.execSQL("DROP TABLE history")
                db.execSQL("ALTER TABLE history_new RENAME TO history")
                db.execSQL("DROP TABLE IF EXISTS profile_statistic")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS profile_statistic (
                        id INTEGER NOT NULL PRIMARY KEY,
                        type TEXT NOT NULL,
                        count INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS achievement (
                        id INTEGER NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        image TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE dog_new (
                        iddog INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        stepRank INTEGER NOT NULL, progress INTEGER NOT NULL,
                        unlock INTEGER NOT NULL, selected INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("INSERT INTO dog_new SELECT iddog, stepRank, progress, unlock, selected FROM dog")
                db.execSQL("DROP TABLE dog")
                db.execSQL("ALTER TABLE dog_new RENAME TO dog")
            }
        }
    }
}
