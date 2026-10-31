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
import com.oc.catemoji.catoc.data.datalocal.room.entity.BattleResultEntity
import com.oc.catemoji.catoc.data.datalocal.room.dao.BattleResultDao

@Database(
    entities = [UserEntity::class, DogEntity::class, HistoryEntity::class, AchievementEntity::class, AppearanceStateEntity::class, BattleResultEntity::class],
    version = 7,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun dogDao(): DogDao
    abstract fun historyDao(): HistoryDao
    abstract fun profileDao(): ProfileDao
    abstract fun appearanceDao(): AppearanceDao
    abstract fun battleResultDao(): BattleResultDao

    companion object {
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS battle_result (
                        matchId TEXT NOT NULL PRIMARY KEY, historyId INTEGER NOT NULL,
                        won INTEGER NOT NULL, bones INTEGER NOT NULL, rankPoints INTEGER NOT NULL,
                        previousRank INTEGER NOT NULL, nextRank INTEGER NOT NULL,
                        badgeIds TEXT NOT NULL, bonusClaimed INTEGER NOT NULL
                    )
                """.trimIndent())
                for (id in 1..7) {
                    db.execSQL("UPDATE achievement SET image = ? WHERE id = ?",
                        arrayOf("file:///android_asset/Achievement/$id.webp", id))
                }
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Assets were converted to WebP; repair only the bundled paths we own.
                // Appearance unlocks/selections use stable IDs and need no changes.
                for (id in 1..15) {
                    listOf("user" to "imageAvatar", "history" to "imageAvatar1",
                        "history" to "imageAvatar2", "user" to "imageFrame").forEach { (table, column) ->
                        val type = if (column == "imageFrame") "frame" else "avatar"
                        val path = "file:///android_asset/$id/$type"
                        db.execSQL("UPDATE $table SET $column = ? WHERE $column = ?",
                            arrayOf("$path.webp", "$path.png"))
                    }
                }
                val titles = listOf("First Bark", "Battle Pup", "Top Dog", "Bark King",
                    "Invincible Hound", "Speed Barker", "Call the Cops!")
                titles.forEachIndexed { index, title ->
                    val path = "file:///android_asset/Achievement/${index + 1}. $title"
                    db.execSQL("UPDATE achievement SET image = ? WHERE image = ?",
                        arrayOf("$path.webp", "$path.png"))
                }
            }
        }

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
