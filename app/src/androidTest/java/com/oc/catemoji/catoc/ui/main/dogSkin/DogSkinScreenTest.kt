package com.oc.catemoji.catoc.ui.main.dogSkin

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.oc.catemoji.catoc.R
import com.oc.catemoji.catoc.RoomLoadState
import com.oc.catemoji.catoc.ViewModelActivity
import com.oc.catemoji.catoc.data.datalocal.room.AppDatabase
import com.oc.catemoji.catoc.data.datalocal.room.entity.DogEntity
import com.oc.catemoji.catoc.data.datalocal.room.entity.AppearanceStateEntity.Companion.AVATAR
import com.oc.catemoji.catoc.data.model.DogSkinCatalogue
import com.oc.catemoji.catoc.data.repository.BattleRepository
import com.oc.catemoji.catoc.data.repository.DogSkinRepository
import com.oc.catemoji.catoc.databinding.ActivityDogSkinBinding
import com.bumptech.glide.request.target.DrawableImageViewTarget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DogSkinScreenTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun directBoneRewardAdds100OnceWhileSavingAndUpdatesAffordableSkins() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val store = ViewModelStore()
        val jobs = mutableListOf<Job>()
        try {
            val repository = BattleRepository(database, database.userDao(), database.dogDao(), database.historyDao())
            repository.createProfile("Reward test")
            lateinit var model: DogSkinViewModel
            instrumentation.runOnMainSync {
                model = DogSkinViewModel(repository, DogSkinRepository(context, database))
                store.put("skins", model)
                jobs += checkNotNull(model.viewModelScope.coroutineContext[Job])
            }
            val initial = withTimeout(5_000) { model.state.first { !it.loading && it.hasUser } }
            assertEquals(100, initial.coinbone)
            assertFalse(initial.items.first { it.skin.id == 3 }.canAfford)
            instrumentation.runOnMainSync {
                model.claimBoneReward()
                model.claimBoneReward()
                assertTrue(model.state.value.claimingReward)
            }
            val rewarded = withTimeout(5_000) { model.state.first { it.coinbone == 200 && !it.claimingReward } }
            assertTrue(rewarded.items.first { it.skin.id == 3 }.canAfford)
            assertEquals(200, database.userDao().getUser()!!.coinbone)
            instrumentation.runOnMainSync { model.claimBoneReward() }
            withTimeout(5_000) { model.state.first { it.coinbone == 300 && !it.claimingReward } }
            assertEquals(300, database.userDao().getUser()!!.coinbone)
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            jobs.joinAll()
            database.close()
        }
    }

    @Test
    fun catalogueIsReadableAndOnlyFirstSkinStartsUnlockedEvenWithEnoughCoin() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val catalogue = DogSkinRepository(context, database)
        val options = catalogue.loadOptions()
        assertEquals((1..30).toSet(), options.map { it.id }.toSet())
        options.forEach { skin ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open(skin.image.removePrefix("file:///android_asset/")).use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            assertEquals("image/webp", bounds.outMimeType)
            assertTrue(bounds.outWidth > 0 && bounds.outHeight > 0)
            context.assets.open(skin.statePreview.removePrefix("file:///android_asset/")).use { assertTrue(it.read() >= 0) }
        }
        val repository = BattleRepository(database, database.userDao(), database.dogDao(), database.historyDao())
        val store = ViewModelStore()
        val jobs = mutableListOf<Job>()
        try {
            val user = repository.createProfile("Player")
            lateinit var model: DogSkinViewModel
            instrumentation.runOnMainSync {
                model = DogSkinViewModel(repository, catalogue)
                store.put("skin", model)
                jobs += checkNotNull(model.viewModelScope.coroutineContext[Job])
            }
            val initial = withTimeout(5_000) { model.state.first { !it.loading } }
            assertEquals((1..15).toList(), initial.items.map { it.skin.folderId })
            assertTrue(initial.items.first().selected)
            assertEquals(listOf(1), initial.items.filter { !it.locked }.map { it.skin.id })
            val paid = options.first { it.price > user.coinbone }
            assertTrue(initial.items.first { it.skin.id == paid.id }.locked)
            val price = paid.price
            repository.saveUser(user.copy(coinbone = price))
            val enough = withTimeout(5_000) { model.state.first { it.coinbone == price } }
            assertTrue(enough.items.first { it.skin.id == paid.id }.locked)
            assertTrue(enough.items.first { it.skin.id == paid.id }.canAfford)
            repository.saveUser(user.copy(coinbone = price - 1))
            val insufficient = withTimeout(5_000) { model.state.first { it.coinbone == price - 1 } }
            assertTrue(insufficient.items.first { it.skin.id == paid.id }.locked)
            assertFalse(insufficient.items.first { it.skin.id == paid.id }.canAfford)
            assertFalse(insufficient.items.first().locked)
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            jobs.joinAll()
            database.close()
        }
    }

    @Test
    fun equippingRequiresUnlockedSkinAndPreservesBalanceProfileAndDogProgress() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val repository = BattleRepository(database, database.userDao(), database.dogDao(), database.historyDao())
        val catalogue = DogSkinRepository(context, database)
        val store = ViewModelStore()
        val jobs = mutableListOf<Job>()
        try {
            val original = repository.createProfile("Player")
            val skin = catalogue.loadOptions().first { it.folderId == 2 && it.imageNumber == 1 }
            repository.saveDog(DogEntity(skin.id, stepRank = 2, progress = 45, matchesPlayed = 20, matchesWon = 9))
            try {
                catalogue.equip(skin.id)
                fail("A skin with insufficient CoinBone must remain locked")
            } catch (expected: IllegalStateException) {
                assertEquals(original, database.userDao().getUser())
            }
            repository.saveUser(original.copy(coinbone = skin.price))
            lateinit var model: DogSkinViewModel
            lateinit var home: ViewModelActivity
            instrumentation.runOnMainSync {
                model = DogSkinViewModel(repository, catalogue)
                home = ViewModelActivity(repository, context)
                store.put("skin", model)
                store.put("home", home)
                jobs += checkNotNull(model.viewModelScope.coroutineContext[Job])
                jobs += checkNotNull(home.viewModelScope.coroutineContext[Job])
            }
            withTimeout(5_000) { model.state.first { !it.loading && it.coinbone == skin.price } }
            instrumentation.runOnMainSync { model.onSkinClicked(skin.id); model.onSkinClicked(skin.id) }
            val purchased = withTimeout(5_000) {
                model.state.first { !it.selecting && it.coinbone == 0 && it.items.any { item -> item.skin.id == skin.id && !item.locked } }
            }
            assertFalse(purchased.items.first { it.skin.id == skin.id }.selected)
            assertEquals(original.copy(coinbone = 0), database.userDao().getUser())
            instrumentation.runOnMainSync { model.onSkinClicked(skin.id); model.onSkinClicked(skin.id) }
            val equipped = withTimeout(5_000) { model.state.first { it.items.any { item -> item.skin.id == skin.id && item.selected } } }
            assertEquals(1, equipped.items.count { it.selected })
            assertEquals(original.copy(coinbone = 0, iddog = skin.id), database.userDao().getUser())
            catalogue.purchase(skin.id)
            assertEquals(0, database.userDao().getUser()?.coinbone)
            assertEquals(DogEntity(skin.id, stepRank = 2, progress = 45, unlock = true, selected = true,
                matchesPlayed = 20, matchesWon = 9),
                database.dogDao().getDog(skin.id))
            assertEquals(1, database.dogDao().observeDogs().first().count { it.selected })
            val preview = withTimeout(5_000) {
                home.homeState.first { it is RoomLoadState.Success && it.data.selectedDog?.iddog == skin.id }
            } as RoomLoadState.Success
            assertEquals("file:///android_asset/2/state/adult/1.webp", preview.data.dogPreview)

            repository.saveUser(checkNotNull(database.userDao().getUser()).copy(coinbone = 0))
            val afterSpending = withTimeout(5_000) { model.state.first { it.coinbone == 0 } }
            assertFalse(afterSpending.items.first { it.skin.id == skin.id }.locked)
            assertTrue(afterSpending.items.first { it.skin.id == skin.id }.selected)
            catalogue.equip(1)
            assertEquals(1, database.userDao().getUser()?.iddog)
            assertFalse(checkNotNull(database.dogDao().getDog(skin.id)).selected)
            assertEquals(45, database.dogDao().getDog(skin.id)?.progress)
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            jobs.joinAll()
            database.close()
        }
    }

    @Test
    fun concurrentPurchasesChargeOnceAndInsufficientBalanceCannotUnlockAnotherSkin() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repository = BattleRepository(database, database.userDao(), database.dogDao(), database.historyDao())
            val original = repository.createProfile("Player")
            repository.saveUser(original.copy(coinbone = 500))
            val skins = DogSkinRepository(context, database)
            coroutineScope { List(4) { async { skins.purchase(3) } }.awaitAll() }
            assertEquals(300, database.userDao().getUser()?.coinbone)
            assertEquals(original.iddog, database.userDao().getUser()?.iddog)
            assertTrue(checkNotNull(database.dogDao().getDog(3)).unlock)
            assertFalse(checkNotNull(database.dogDao().getDog(3)).selected)
            assertTrue(checkNotNull(database.appearanceDao().getState(AVATAR, "2")).unlock)
            try {
                skins.purchase(5)
                fail("Insufficient balance must not buy the next skin")
            } catch (expected: IllegalStateException) {
                assertEquals(300, database.userDao().getUser()?.coinbone)
                assertNull(database.dogDao().getDog(5))
            }
        } finally { database.close() }
    }

    @Test
    fun recycledItemResetsLockSelectionAndProgressOnPhoneAndTablet() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val skin = try {
            DogSkinRepository(context, database).loadOptions().first { it.folderId == 2 && it.imageNumber == 1 }
        } finally { database.close() }
        instrumentation.runOnMainSync {
            listOf(360 to 740, 800 to 1100).forEach { (widthDp, heightDp) ->
                val configuration = Configuration(context.resources.configuration).apply {
                    screenWidthDp = widthDp
                    screenHeightDp = heightDp
                    smallestScreenWidthDp = widthDp
                }
                val themed = ContextThemeWrapper(context.createConfigurationContext(configuration), R.style.Base_Theme_BaseActivity)
                val adapter = DogSkinAdapter()
                val holder = adapter.onCreateViewHolder(FrameLayout(themed), 0)
                val binding = holder.binding
                val max = DogSkinItem.from(skin,
                    DogEntity(skin.id, unlock = true, selected = true,
                        matchesPlayed = skin.winsToMaxLevel.toLong(), matchesWon = skin.winsToMaxLevel.toLong()), 0, skin.id)
                fun bind(item: DogSkinItem) {
                    adapter.submitList(listOf(item))
                    adapter.onBindViewHolder(holder, 0)
                    val width = ((widthDp - 48) / 2 * themed.resources.displayMetrics.density).toInt()
                    binding.root.measure(
                        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    )
                    binding.root.layout(0, 0, width, binding.root.measuredHeight)
                    assertTrue(binding.imgDogSkin.height > 0)
                    assertTrue(binding.txtLock.height > 0)
                    assertTrue(binding.root.height >= binding.stoker1.bottom)
                    assertTrue(binding.root.height >= (binding.txtLock.parent as View).bottom)
                    assertEquals(width.toFloat(), binding.stoker1.height.toFloat(), 2f)
                    assertEquals(width * 0.2f / 1.56f, binding.txtLock.height.toFloat(), 2f)
                }
                bind(max)
                assertEquals(View.GONE, binding.keyDogSkin.visibility)
                assertEquals(View.VISIBLE, binding.view7.visibility)
                assertEquals(View.VISIBLE, binding.selectDogSkin.visibility)
                assertEquals(View.VISIBLE, binding.txtMaxProgress.visibility)
                assertEquals(themed.getString(R.string.max_level_reached), binding.txtMaxProgress.text.toString())
                assertEquals(themed.getString(R.string.dog_skin_equipped), binding.txtLock.text.toString())
                assertEquals(themed.getColor(R.color._22C55E), (binding.txtLock.background as ColorDrawable).color)
                assertEquals(themed.getColor(R.color.white), binding.txtLock.currentTextColor)
                assertEquals(1f, (binding.view12.layoutParams as ConstraintLayout.LayoutParams).guidePercent)
                assertEquals((binding.bgItemPogress.parent as View).width, binding.bgItemPogress.width)

                bind(DogSkinItem.from(skin, null, skin.price - 1, null))
                assertEquals(View.VISIBLE, binding.keyDogSkin.visibility)
                assertEquals(View.GONE, binding.view7.visibility)
                assertSame(binding.view7, binding.stoker2.parent)
                assertSame(binding.view7, binding.txtProgressPercent.parent)
                assertEquals(View.GONE, binding.selectDogSkin.visibility)
                assertEquals(View.GONE, binding.txtMaxProgress.visibility)
                assertFalse(binding.root.isSelected)
                assertEquals(skin.price.toString(), binding.txtCoinBoneSkinDog.text.toString())
                assertEquals(themed.getString(R.string.locked), binding.txtLock.text.toString())
                assertEquals(themed.getColor(R.color._868686), (binding.txtLock.background as ColorDrawable).color)
                assertEquals(themed.getColor(R.color.white), binding.txtLock.currentTextColor)
                assertTrue(binding.txtCoinBoneSkinDog.width > 0)

                bind(DogSkinItem.from(skin, DogEntity(skin.id, unlock = true, matchesPlayed = 20, matchesWon = 3), skin.price, null))
                assertEquals(View.GONE, binding.keyDogSkin.visibility)
                assertEquals(View.VISIBLE, binding.view7.visibility)
                assertEquals(themed.getString(R.string.dog_skin_unlocked), binding.txtLock.text.toString())
                assertEquals(themed.getColor(R.color.white), (binding.txtLock.background as ColorDrawable).color)
                assertEquals(themed.getColor(R.color.app_color), binding.txtLock.currentTextColor)
                assertTrue(binding.stoker2.height > 0)
                assertEquals(binding.root.width * 0.15f / 1.56f, binding.view7.height.toFloat(), 2f)
                assertEquals(0.6f, (binding.view12.layoutParams as ConstraintLayout.LayoutParams).guidePercent)
                assertEquals((binding.bgItemPogress.parent as View).width * 0.6f, binding.bgItemPogress.width.toFloat(), 2f)
                assertEquals(View.GONE, binding.txtMaxProgress.visibility)

                val screen = ActivityDogSkinBinding.inflate(LayoutInflater.from(themed))
                screen.recycleDogSkin.adapter = DogSkinAdapter().apply {
                    submitList(DogSkinCatalogue.create(100).filter { it.imageNumber == 1 }.map { max.copy(skin = it) })
                }
                val density = themed.resources.displayMetrics.density
                screen.root.measure(
                    View.MeasureSpec.makeMeasureSpec((widthDp * density).toInt(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec((heightDp * density).toInt(), View.MeasureSpec.EXACTLY),
                )
                screen.root.layout(0, 0, screen.root.measuredWidth, screen.root.measuredHeight)
                assertTrue(screen.recycleDogSkin.height > 0)
                assertNotNull(screen.recycleDogSkin.findViewHolderForAdapterPosition(0))
                assertNotNull(screen.recycleDogSkin.findViewHolderForAdapterPosition(1))
                assertEquals(screen.recycleDogSkin.findViewHolderForAdapterPosition(0)?.itemView?.top,
                    screen.recycleDogSkin.findViewHolderForAdapterPosition(1)?.itemView?.top)
            }
        }
    }

    @Test
    fun activityShowsCatalogueAndBackButtonClosesScreen() {
        ActivityScenario.launch(DogSkinActivity::class.java).use { scenario ->
            val deadline = android.os.SystemClock.uptimeMillis() + 5_000
            var loaded = false
            while (!loaded && android.os.SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity { activity ->
                    val list = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycleDogSkin)
                    val first = list.findViewHolderForAdapterPosition(0)?.itemView
                    val imagesReady = (0..1).all { position ->
                        val image = list.findViewHolderForAdapterPosition(position)?.itemView
                            ?.findViewById<ImageView>(R.id.imgDogSkin)
                        image != null && DrawableImageViewTarget(image).request?.isComplete == true
                    }
                    loaded = list.adapter?.itemCount == 15 && first != null && first.width > 0 && first.height > 0 && list.isShown && imagesReady
                }
                if (!loaded) android.os.SystemClock.sleep(20)
            }
            assertTrue("Dog skin screen should display its catalogue", loaded)
            instrumentation.waitForIdleSync()
            android.os.SystemClock.sleep(200)
            instrumentation.uiAutomation.takeScreenshot()?.let { screenshot ->
                try {
                    context.cacheDir.resolve("dog-skin-screen.png").outputStream().use {
                        screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                } finally {
                    screenshot.recycle()
                }
            }
            scenario.onActivity { it.findViewById<View>(R.id.btnActionBarLeft).performClick() }
            val closeDeadline = android.os.SystemClock.uptimeMillis() + 5_000
            while (scenario.state != androidx.lifecycle.Lifecycle.State.DESTROYED &&
                android.os.SystemClock.uptimeMillis() < closeDeadline) {
                android.os.SystemClock.sleep(20)
            }
            assertEquals(androidx.lifecycle.Lifecycle.State.DESTROYED, scenario.state)
        }
    }
}
