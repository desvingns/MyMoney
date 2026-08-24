package com.kshavrin.mymoney.feature.support

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import com.kshavrin.mymoney.core.designsystem.R as DesignSystemR

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SupportArtworkAssetContractTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `all support artwork ids resolve to decodable png bitmaps`() {
        artworkResources.forEach { (resourceName, resourceId) ->
            context.resources.openRawResource(resourceId).use { stream ->
                val signature = ByteArray(PNG_SIGNATURE.size)
                assertEquals(PNG_SIGNATURE.size, stream.read(signature))
                assertTrue(
                    "$resourceName must be a PNG resource",
                    PNG_SIGNATURE.contentEquals(signature),
                )
            }

            val bitmap = decode(resourceId)
            try {
                assertEquals(resourceName, context.resources.getResourceEntryName(resourceId))
                assertEquals(Bitmap.Config.ARGB_8888, bitmap.config)
                assertTrue("$resourceName must have positive width", bitmap.width > 0)
                assertTrue("$resourceName must have positive height", bitmap.height > 0)
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test
    fun `support artwork ids remain wired to their intended consumers`() {
        val consumerSources =
            consumerPaths.associateWith { path ->
                normalize(File(findRepositoryRoot(), path).readText())
            }
        val allConsumers = consumerSources.values.joinToString(" ")

        artworkConsumers.forEach { (resourceName, consumerPath) ->
            val resourceReference = "DesignSystemR.drawable.$resourceName"
            assertTrue(
                "$resourceReference must remain in $consumerPath",
                consumerSources.getValue(consumerPath).contains(resourceReference),
            )
            assertEquals(
                "$resourceReference must have exactly one production consumer",
                1,
                allConsumers.countOccurrences(resourceReference),
            )
        }
    }

    @Test
    fun `avatar keeps transparent regions instead of a baked checkerboard`() {
        val bitmap = decode(DesignSystemR.drawable.support_neon_avatar)
        try {
            assertTrue("avatar must preserve alpha", bitmap.hasAlpha())
            val pixels = bitmap.pixels()
            val transparentPixels = pixels.count { Color.alpha(it) == 0 }
            assertTrue("avatar must contain transparent regions", transparentPixels > 0)

            val cornerPixels =
                intArrayOf(
                    bitmap.getPixel(0, 0),
                    bitmap.getPixel(bitmap.width - 1, 0),
                    bitmap.getPixel(0, bitmap.height - 1),
                    bitmap.getPixel(bitmap.width - 1, bitmap.height - 1),
                )
            assertTrue(
                "avatar corners must be transparent, not checkerboard pixels",
                cornerPixels.all { Color.alpha(it) == 0 },
            )
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun `large coffee keeps contrasting cappuccino and thanks regions`() {
        val bitmap = decode(DesignSystemR.drawable.support_neon_coffee_large)
        try {
            val cappuccinoPixels =
                countPixels(bitmap, xStart = 0.15f, xEnd = 0.85f, yStart = 0.10f, yEnd = 0.52f) {
                    it.alpha >= 180 && it.red > it.green && it.green > it.blue && it.red - it.blue > 25
                }
            val thanksPixels =
                countPixels(bitmap, xStart = 0.20f, xEnd = 0.80f, yStart = 0.55f, yEnd = 0.82f) {
                    it.alpha >= 180 && (it.red + it.green + it.blue) / 3 < 90
                }

            assertTrue("large coffee must retain visible cappuccino artwork", cappuccinoPixels > 100)
            assertTrue("large coffee must retain a readable Thanks contrast region", thanksPixels > 100)
        } finally {
            bitmap.recycle()
        }
    }

    private fun decode(resourceId: Int): Bitmap {
        val options =
            BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inScaled = false
            }
        val bitmap = BitmapFactory.decodeResource(context.resources, resourceId, options)
        assertNotNull("resource $resourceId must decode", bitmap)
        return requireNotNull(bitmap)
    }

    private fun Bitmap.pixels(): IntArray =
        IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }

    private fun countPixels(
        bitmap: Bitmap,
        xStart: Float,
        xEnd: Float,
        yStart: Float,
        yEnd: Float,
        predicate: (ColorSample) -> Boolean,
    ): Int {
        val left = (bitmap.width * xStart).toInt()
        val right = (bitmap.width * xEnd).toInt()
        val top = (bitmap.height * yStart).toInt()
        val bottom = (bitmap.height * yEnd).toInt()
        var count = 0
        for (x in left until right step 4) {
            for (y in top until bottom step 4) {
                val pixel = bitmap.getPixel(x, y)
                if (predicate(ColorSample(pixel))) count++
            }
        }
        return count
    }

    private data class ColorSample(
        val pixel: Int,
    ) {
        val alpha: Int get() = Color.alpha(pixel)
        val red: Int get() = Color.red(pixel)
        val green: Int get() = Color.green(pixel)
        val blue: Int get() = Color.blue(pixel)
    }

    private fun String.countOccurrences(value: String): Int =
        windowed(value.length, 1).count { it == value }

    private fun normalize(source: String): String = source.replace(Regex("\\s+"), " ")

    private companion object {
        private val PNG_SIGNATURE =
            byteArrayOf(
                0x89.toByte(),
                0x50,
                0x4E,
                0x47,
                0x0D,
                0x0A,
                0x1A,
                0x0A,
            )

        private val artworkResources =
            linkedMapOf(
                "support_neon_avatar" to DesignSystemR.drawable.support_neon_avatar,
                "support_neon_plus" to DesignSystemR.drawable.support_neon_plus,
                "support_neon_coffee_large" to DesignSystemR.drawable.support_neon_coffee_large,
                "support_neon_coffee_small" to DesignSystemR.drawable.support_neon_coffee_small,
                "support_neon_ads" to DesignSystemR.drawable.support_neon_ads,
            )

        private val consumerPaths =
            listOf(
                "feature/support/src/main/java/com/kshavrin/mymoney/feature/support/SupportScreen.kt",
                "feature/support/src/main/java/com/kshavrin/mymoney/feature/support/rewardedad/RewardedAdScreen.kt",
                "feature/support/src/main/java/com/kshavrin/mymoney/feature/support/plus/SupportPlusEntry.kt",
            )

        private val artworkConsumers =
            linkedMapOf(
                "support_neon_avatar" to
                    "feature/support/src/main/java/com/kshavrin/mymoney/feature/support/SupportScreen.kt",
                "support_neon_coffee_small" to
                    "feature/support/src/main/java/com/kshavrin/mymoney/feature/support/SupportScreen.kt",
                "support_neon_coffee_large" to
                    "feature/support/src/main/java/com/kshavrin/mymoney/feature/support/SupportScreen.kt",
                "support_neon_ads" to
                    "feature/support/src/main/java/com/kshavrin/mymoney/feature/support/rewardedad/RewardedAdScreen.kt",
                "support_neon_plus" to
                    "feature/support/src/main/java/com/kshavrin/mymoney/feature/support/plus/SupportPlusEntry.kt",
            )

        fun findRepositoryRoot(): File =
            generateSequence(File(requireNotNull(System.getProperty("user.dir"))).absoluteFile) { it.parentFile }
                .firstOrNull { candidate ->
                    File(candidate, "settings.gradle.kts").isFile &&
                        File(candidate, "app/build.gradle.kts").isFile
                } ?: error("Unable to locate repository root")
    }
}
