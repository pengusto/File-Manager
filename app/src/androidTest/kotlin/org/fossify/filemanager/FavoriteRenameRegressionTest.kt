package org.fossify.filemanager

import android.os.Environment
import android.os.Build
import android.view.inspector.WindowInspector
import android.widget.EditText
import android.widget.RadioButton
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.fossify.commons.dialogs.RenameItemsDialog
import org.fossify.commons.R as CommonsR
import org.fossify.filemanager.activities.MainActivity
import java.io.File
import java.util.Collections
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.fossify.filemanager.helpers.Config
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavoriteRenameRegressionTest {
    @Test
    fun renameUpdatesExactAndNestedPathsWithoutTouchingSiblingsOrUnfavorite() {
        check(Build.HARDWARE == "ranchu") { "Use a disposable emulator" }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val config = Config.newInstance(context)
        val previous = config.favorites
        try {
            config.favorites = hashSetOf("/test/Old", "/test/Old/child", "/test/Old/child/deep", "/test/Older", "/test/old")
            config.moveFavorite("/test/Old", "/test/New")
            assertEquals(setOf("/test/New", "/test/New/child", "/test/New/child/deep", "/test/Older", "/test/old"), config.favorites)
            config.moveFavorite("/test/Old", "/test/New")
            assertTrue(config.favorites.contains("/test/New/child"))
            config.removeFavorite("/test/New")
            assertFalse(config.favorites.contains("/test/New"))
            assertTrue(config.favorites.contains("/test/New/child"))
            config.moveFavorite("", "/bad")
            config.moveFavorite("/", "/bad")
            assertTrue(config.favorites.contains("/test/New/child"))
            config.moveFavorite("/test/New/", "/test/Final/")
            assertTrue(config.favorites.contains("/test/Final/child"))
            assertTrue(Config.newInstance(context).favorites.contains("/test/Final/child/deep"))
            config.removeFavorite("/test/Final", includeDescendants = true)
            assertEquals(setOf("/test/Older", "/test/old"), config.favorites)
            config.removeFavorite("", includeDescendants = true)
            config.removeFavorite("/", includeDescendants = true)
            assertEquals(setOf("/test/Older", "/test/old"), config.favorites)
        } finally { config.favorites = previous }
    }
    @Test
    fun batchRenameReportsOnlySuccessfulItemsAndUpdatesNestedFavorites() {
        check(Build.HARDWARE == "ranchu") { "Use a disposable emulator" }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val config = Config.newInstance(context)
        val previous = config.favorites
        val root = File(Environment.getExternalStorageDirectory(), "PenguinUpstream-${System.nanoTime()}")
        val first = File(root, "First").apply { mkdirs() }
        val second = File(root, "Second").apply { mkdirs() }
        File(first, "child").mkdir()
        File(second, "child").mkdir()
        File(second, "child/proof.txt").writeText("preserved")
        File(root, "Renamed_First").mkdir()
        val renamed = Collections.synchronizedList(mutableListOf<String>())
        try {
            config.favorites = hashSetOf("${first.path}/child", "${second.path}/child")
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    RenameItemsDialog(activity, arrayListOf(first.path, second.path), onItemRenamed = { old, new ->
                        config.moveFavorite(old, new)
                        renamed.add(old)
                    }) { }
                    val views = WindowInspector.getGlobalWindowViews()
                    views.firstNotNullOf { it.findViewById<EditText>(CommonsR.id.rename_items_value) }.setText("Renamed_")
                    views.firstNotNullOf { it.findViewById<RadioButton>(CommonsR.id.rename_items_radio_prepend) }.isChecked = true
                    views.firstNotNullOf { it.findViewById<android.view.View>(android.R.id.button1) }.performClick()
                }
                val deadline = System.currentTimeMillis() + 10000
                while (renamed.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(50)
                assertEquals(listOf(second.path), renamed.toList())
                assertTrue(config.favorites.contains("${first.path}/child"))
                assertTrue(config.favorites.contains("${root.path}/Renamed_Second/child"))
                assertEquals("preserved", File(root, "Renamed_Second/child/proof.txt").readText())
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    WindowInspector.getGlobalWindowViews().forEach {
                        it.findViewById<android.view.View>(android.R.id.button2)?.performClick()
                    }
                }
            }
        } finally {
            config.favorites = previous
            root.deleteRecursively()
        }
    }
}
