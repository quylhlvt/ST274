package com.oc.catemoji.catoc.data.datalocal.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.oc.catemoji.catoc.data.datalocal.room.dao.DogDao
import com.oc.catemoji.catoc.data.datalocal.room.dao.HistoryDao
import com.oc.catemoji.catoc.data.datalocal.room.dao.UserDao
import com.oc.catemoji.catoc.data.datalocal.room.entity.DogEntity
import com.oc.catemoji.catoc.data.datalocal.room.entity.HistoryEntity
import com.oc.catemoji.catoc.data.datalocal.room.entity.UserEntity

@Database(
    entities = [UserEntity::class, DogEntity::class, HistoryEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun dogDao(): DogDao
    abstract fun historyDao(): HistoryDao

    companion object {
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
