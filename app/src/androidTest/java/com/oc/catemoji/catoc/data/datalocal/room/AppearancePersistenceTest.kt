package com.oc.catemoji.catoc.data.datalocal.room

import android.graphics.BitmapFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.oc.catemoji.catoc.data.datalocal.room.entity.AppearanceStateEntity
import com.oc.catemoji.catoc.data.datalocal.room.entity.AppearanceStateEntity.Companion.AVATAR
import com.oc.catemoji.catoc.data.datalocal.room.entity.AppearanceStateEntity.Companion.FRAME
import com.oc.catemoji.catoc.data.repository.BattleRepository
import com.oc.catemoji.catoc.data.repository.ProfileAppearanceRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AppearancePersistenceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun firstInstallCatalogueContainsDecodableImagesAndDefaultSelections() = runBlocking {
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build().useDatabase { database ->
            val repository = BattleRepository(database, database.userDao(), database.dogDao(), database.historyDao())
            val user = repository.createProfile("First player")
            val appearances = ProfileAppearanceRepository(context, database)
            val options = appearances.loadOptions()
            assertEquals((1..15).map { "file:///android_asset/$it/avatar.webp" }, options.avatars)
            assertEquals((1..15).map { "file:///android_asset/$it/frame.webp" }, options.frames)
            (options.avatars + options.frames).forEach { image ->
                context.assets.open(image.removePrefix("file:///android_asset/")).use { stream ->
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(stream, null, bounds)
                    assertTrue("Cannot decode $image", bounds.outWidth > 0 && bounds.outHeight > 0)
                }
            }
            appearances.initializeOptions(options, user.imageAvatar, user.imageFrame)
            val states = appearances.states.first()
            assertEquals(user.imageAvatar, states.single { it.type == AVATAR && it.selected && it.unlock }.image)
            assertEquals(user.imageFrame, states.single { it.type == FRAME && it.selected && it.unlock }.image)
        }
    }

    @Test
    fun migrationFromVersionFiveRepairsBundledPathsAndPreservesProgressAndExternalImages() = runBlocking {
        val filename = "webp-migration-${UUID.randomUUID()}.db"
        try {
            val schema = instrumentation.context.assets.open(
                "com.oc.catemoji.catoc.data.datalocal.room.AppDatabase/5.json",
            ).bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
            context.openOrCreateDatabase(filename, 0, null).use { legacy ->
                val entities = schema.getJSONArray("entities")
                for (index in 0 until entities.length()) {
                    val entity = entities.getJSONObject(index)
                    legacy.execSQL(entity.getString("createSql").replace(
                        "\${TABLE_NAME}", entity.getString("tableName"),
                    ))
                }
                legacy.execSQL("INSERT INTO user VALUES (1, 'Saved', 'file:///android_asset/9/avatar.png', 'file:///android_asset/12/frame.png', 321, 4, 56, 7)")
                legacy.execSQL("INSERT INTO dog VALUES (7, 4, 75, 1, 1)")
                legacy.execSQL("INSERT INTO appearance_state VALUES ('avatar', '9', 1, 1), ('avatar', '2', 0, 1), ('frame', '12', 1, 1)")
                legacy.execSQL("INSERT INTO history VALUES (8, 'Saved match', 1234, 'file:///android_asset/1/avatar.png', 'content://photos/custom.png', 1)")
                legacy.execSQL("INSERT INTO achievement VALUES (1, 'First Bark', 'file:///android_asset/Achievement/1. First Bark.png')")
                legacy.version = 5
            }
            Room.databaseBuilder(context, AppDatabase::class.java, filename)
                .addMigrations(AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7).build().useDatabase { database ->
                    val user = checkNotNull(database.userDao().getUser())
                    assertEquals("file:///android_asset/9/avatar.webp", user.imageAvatar)
                    assertEquals("file:///android_asset/12/frame.webp", user.imageFrame)
                    assertEquals("Saved", user.name)
                    assertEquals(321, user.coinbone)
                    assertEquals(4, user.rank)
                    assertEquals(56, user.rankbatter)
                    assertEquals(7, user.iddog)
                    assertEquals(75, database.dogDao().getDog(7)?.progress)
                    val history = checkNotNull(database.historyDao().getHistory(8))
                    assertEquals("file:///android_asset/1/avatar.webp", history.imageAvatar1)
                    assertEquals("content://photos/custom.png", history.imageAvatar2)
                    assertEquals(1234L, history.date)
                    assertTrue(history.typePlay)
                    assertEquals("file:///android_asset/Achievement/1.webp",
                        database.profileDao().observeAchievements().first().single().image)
                    assertEquals(AppearanceStateEntity(AVATAR, "2", unlock = true),
                        database.appearanceDao().getState(AVATAR, "2"))
                    assertEquals("9", database.appearanceDao().getStates(AVATAR).single { it.selected }.assetId)
                    assertEquals("12", database.appearanceDao().getStates(FRAME).single { it.selected }.assetId)
                }
        } finally {
            context.deleteDatabase(filename)
        }
    }

    @Test
    fun unlocksAndOneSelectionPerTypeSurviveReopeningWithoutStoringCatalogues() = runBlocking {
        val filename = "appearance-${UUID.randomUUID()}.db"
        try {
            Room.databaseBuilder(context, AppDatabase::class.java, filename).build().useDatabase { database ->
                val repository = BattleRepository(database, database.userDao(), database.dogDao(), database.historyDao())
                val original = repository.createProfile("Player")
                val appearances = ProfileAppearanceRepository(context, database)
                val options = appearances.loadOptions()
                assertEquals(15, options.avatars.size)
                assertEquals(15, options.frames.size)
                assertEquals(1, database.appearanceDao().getStates(AVATAR).size)
                assertEquals(1, database.appearanceDao().getStates(FRAME).size)
                appearances.unlockAvatar(options.avatars[1])
                appearances.unlockFrame(options.frames[2])
                // Unlock does not apply or select either item.
                assertEquals(original, database.userDao().getUser())
                assertEquals("1", database.appearanceDao().getStates(AVATAR).single { it.selected }.assetId)
                assertEquals("1", database.appearanceDao().getStates(FRAME).single { it.selected }.assetId)
                repository.updateProfile("Buddy", options.avatars[1], options.frames[2])
                // Repeated reward grants must not reset selected or create duplicates.
                appearances.unlockAvatar(options.avatars[1])
                appearances.unlockFrame(options.frames[2])
            }
            Room.databaseBuilder(context, AppDatabase::class.java, filename).build().useDatabase { database ->
                val user = checkNotNull(database.userDao().getUser())
                assertEquals("Buddy", user.name)
                assertEquals("file:///android_asset/2/avatar.webp", user.imageAvatar)
                assertEquals("file:///android_asset/3/frame.webp", user.imageFrame)
                assertEquals(100, user.coinbone)
                val appearances = ProfileAppearanceRepository(context, database)
                val options = appearances.loadOptions()
                appearances.initializeOptions(options, user.imageAvatar, user.imageFrame)
                assertEquals(2, database.appearanceDao().getStates(AVATAR).size)
                assertEquals(2, database.appearanceDao().getStates(FRAME).size)
                assertEquals("2", database.appearanceDao().getStates(AVATAR).single { it.selected && it.unlock }.assetId)
                assertEquals("3", database.appearanceDao().getStates(FRAME).single { it.selected && it.unlock }.assetId)
                val columns = mutableSetOf<String>()
                database.openHelper.readableDatabase.query("PRAGMA table_info(appearance_state)").use { cursor ->
                    while (cursor.moveToNext()) columns += cursor.getString(cursor.getColumnIndexOrThrow("name"))
                }
                assertEquals(setOf("type", "assetId", "selected", "unlock"), columns)
                try {
                    appearances.unlockAvatar("file:///android_asset/999/avatar.webp")
                    fail("Missing assets cannot be unlocked")
                } catch (expected: IllegalArgumentException) {
                    assertNull(database.appearanceDao().getState(AVATAR, "999"))
                }
            }
        } finally {
            context.deleteDatabase(filename)
        }
    }

    @Test
    fun migrationFromVersionFourKeepsAppliedAvatarFrameAndPlayerProgress() = runBlocking {
        val filename = "appearance-migration-${UUID.randomUUID()}.db"
        try {
            val schema = instrumentation.context.assets.open(
                "com.oc.catemoji.catoc.data.datalocal.room.AppDatabase/4.json",
            ).bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
            context.openOrCreateDatabase(filename, 0, null).use { legacy ->
                val entities = schema.getJSONArray("entities")
                for (index in 0 until entities.length()) {
                    val entity = entities.getJSONObject(index)
                    legacy.execSQL(entity.getString("createSql").replace(
                        "\${TABLE_NAME}", entity.getString("tableName"),
                    ))
                }
                legacy.execSQL("""
                    INSERT INTO user (id, name, imageAvatar, imageFrame, coinbone, rank, rankbatter, iddog)
                    VALUES (1, 'Saved', 'file:///android_asset/9/avatar.png',
                        'file:///android_asset/12/frame.png', 321, 4, 56, 7)
                """.trimIndent())
                legacy.version = 4
            }
            Room.databaseBuilder(context, AppDatabase::class.java, filename)
                .addMigrations(AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7).build().useDatabase { database ->
                    val user = checkNotNull(database.userDao().getUser())
                    assertEquals("Saved", user.name)
                    assertEquals(321, user.coinbone)
                    assertEquals(4, user.rank)
                    assertEquals(56, user.rankbatter)
                    assertEquals(7, user.iddog)
                    assertEquals(listOf(AppearanceStateEntity(AVATAR, "9", selected = true, unlock = true)),
                        database.appearanceDao().getStates(AVATAR))
                    assertEquals(listOf(AppearanceStateEntity(FRAME, "12", selected = true, unlock = true)),
                        database.appearanceDao().getStates(FRAME))
                    val appearances = ProfileAppearanceRepository(context, database)
                    appearances.initializeOptions(appearances.loadOptions(), user.imageAvatar, user.imageFrame)
                    assertEquals("9", database.appearanceDao().getStates(AVATAR).single { it.selected }.assetId)
                    assertEquals("12", database.appearanceDao().getStates(FRAME).single { it.selected }.assetId)
                    assertEquals(user, database.userDao().getUser())
                }
        } finally {
            context.deleteDatabase(filename)
        }
    }

    private inline fun AppDatabase.useDatabase(block: (AppDatabase) -> Unit) {
        try {
            block(this)
        } finally {
            close()
        }
    }

}
