package com.oc.catemoji.catoc.data.datalocal.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.oc.catemoji.catoc.data.datalocal.room.dao.DogDao
import com.oc.catemoji.catoc.data.datalocal.room.dao.HistoryDao
import com.oc.catemoji.catoc.data.datalocal.room.dao.UserDao
import com.oc.catemoji.catoc.data.datalocal.room.dao.ProfileDao
import com.oc.catemoji.catoc.data.datalocal.room.dao.AppearanceDao
import com.oc.catemoji.catoc.data.datalocal.room.entity.AchievementEntity
import com.oc.catemoji.catoc.data.datalocal.room.entity.DogEntity
import com.oc.catemoji.catoc.data.datalocal.room.entity.HistoryEntity
import com.oc.catemoji.catoc.data.datalocal.room.entity.UserEntity
import com.oc.catemoji.catoc.data.datalocal.room.entity.AppearanceStateEntity

@Database(
    entities = [UserEntity::class, DogEntity::class, HistoryEntity::class, AchievementEntity::class, AppearanceStateEntity::class],
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun dogDao(): DogDao
    abstract fun historyDao(): HistoryDao
    abstract fun profileDao(): ProfileDao
    abstract fun appearanceDao(): AppearanceDao

    companion object {
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS appearance_state (
                        type TEXT NOT NULL,
                        assetId TEXT NOT NULL,
                        selected INTEGER NOT NULL,
                        unlock INTEGER NOT NULL,
                        PRIMARY KEY(type, assetId)
                    )
                """.trimIndent())
                // Preserve appearances already applied by existing players.
                db.query("SELECT imageAvatar, imageFrame FROM user WHERE id = 1").use { cursor ->
                    if (cursor.moveToFirst()) {
                        listOf(AppearanceStateEntity.AVATAR, AppearanceStateEntity.FRAME).forEachIndexed { index, type ->
                            val image = cursor.getString(index)
                            val id = image.removePrefix("file:///android_asset/").removeSuffix("/$type.png")
                            if (id.toIntOrNull() != null && image == "file:///android_asset/$id/$type.png") {
                                db.execSQL(
                                    "INSERT INTO appearance_state (type, assetId, selected, unlock) VALUES (?, ?, 1, 1)",
                                    arrayOf(type, id),
                                )
                            }
                        }
                    }
                }
            }
        }

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
