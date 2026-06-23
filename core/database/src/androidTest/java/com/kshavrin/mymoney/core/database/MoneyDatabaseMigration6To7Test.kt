package com.kshavrin.mymoney.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.common.category.categoryIconDominantHex
import com.kshavrin.mymoney.core.common.category.categoryTextColorHex
import com.kshavrin.mymoney.core.database.migration.MIGRATION_6_7
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MoneyDatabaseMigration6To7Test {
    private val dbName = "migration-6-7-test.db"

    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            MoneyDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun `migrate 6 to 7 adds text_color column and backfills from icon key`() {
        val iconKey = "ic_cat_food"
        val arbitraryColorHex = "#FF0000"
        val expectedColorHex = categoryIconDominantHex(iconKey)
        val expectedTextColor = categoryTextColorHex(iconKey)

        helper.createDatabase(dbName, 6).apply {
            seedCategory(iconKey = iconKey, colorHex = arbitraryColorHex)
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 7, true, MIGRATION_6_7)

        db.query("SELECT `icon_key`, `color_hex`, `text_color` FROM `category` WHERE `icon_key` = '$iconKey'").use { c ->
            assertTrue("Category row must exist after migration", c.moveToFirst())
            val migratedColorHex = c.getString(1)
            val migratedTextColor = c.getString(2)

            assertEquals(
                "color_hex must be recomputed from icon_key (A1: backfill recomputes color_hex)",
                expectedColorHex,
                migratedColorHex,
            )
            assertFalse(
                "color_hex must NOT retain the arbitrary pre-migration value",
                arbitraryColorHex.equals(migratedColorHex, ignoreCase = true),
            )
            assertEquals(
                "text_color must be derived from icon_key via categoryTextColorHex",
                expectedTextColor,
                migratedTextColor,
            )
            assertFalse("text_color must not be empty after backfill", migratedTextColor.isEmpty())
        }

        db.close()
    }

    @Test
    fun `migrate 6 to 7 backfills text_color for every existing category row`() {
        val seeds =
            listOf(
                "ic_cat_food" to "#FF0000",
                "ic_cat_entertainment" to "#00FF00",
                "ic_cat_taxi" to "#0000FF",
            )

        helper.createDatabase(dbName, 6).apply {
            seeds.forEach { (iconKey, colorHex) -> seedCategory(iconKey = iconKey, colorHex = colorHex) }
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 7, true, MIGRATION_6_7)

        seeds.forEach { (iconKey, _) ->
            val expectedTextColor = categoryTextColorHex(iconKey)
            db.query("SELECT `text_color` FROM `category` WHERE `icon_key` = '$iconKey'").use { c ->
                assertTrue("Row for $iconKey must exist", c.moveToFirst())
                assertEquals(
                    "text_color for $iconKey must match categoryTextColorHex",
                    expectedTextColor,
                    c.getString(0),
                )
            }
        }

        db.close()
    }

    @Test
    fun `migrate 6 to 7 recomputes color_hex from icon overwriting the stored value`() {
        val iconKey = "ic_cat_clothing"
        val storedBeforeMigration = "#123456"
        val expectedAfterMigration = categoryIconDominantHex(iconKey)

        helper.createDatabase(dbName, 6).apply {
            seedCategory(iconKey = iconKey, colorHex = storedBeforeMigration)
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 7, true, MIGRATION_6_7)

        db.query("SELECT `color_hex` FROM `category` WHERE `icon_key` = '$iconKey'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(
                "color_hex must be the icon-derived value, not the old arbitrary value",
                expectedAfterMigration,
                c.getString(0),
            )
        }

        db.close()
    }

    @Test
    fun `migrate 6 to 7 leaves no category with empty text_color`() {
        helper.createDatabase(dbName, 6).apply {
            seedCategory(iconKey = "ic_cat_food", colorHex = "#B7FF7A")
            seedCategory(iconKey = "ic_cat_bills", colorHex = "#FF8A80")
            seedCategory(iconKey = "ic_cat_other", colorHex = "#AABBCC")
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 7, true, MIGRATION_6_7)

        db.query("SELECT COUNT(*) FROM `category` WHERE `text_color` = '' OR `text_color` IS NULL").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(
                "No category must have an empty or null text_color after migration",
                0,
                c.getInt(0),
            )
        }

        db.close()
    }

    @Test
    fun `migrate 6 to 7 is a no-op for an empty category table`() {
        helper.createDatabase(dbName, 6).apply { close() }

        val db = helper.runMigrationsAndValidate(dbName, 7, true, MIGRATION_6_7)

        db.query("SELECT COUNT(*) FROM `category`").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Empty category table must remain empty after migration", 0, c.getInt(0))
        }

        db.close()
    }

    private fun SupportSQLiteDatabase.seedCategory(
        iconKey: String,
        colorHex: String,
        name: String = "Cat_$iconKey",
        kind: String = "expense",
        sortOrder: Int = 0,
    ) {
        val now = System.currentTimeMillis()
        execSQL(
            "INSERT INTO `category` (`name`, `kind`, `icon_key`, `color_hex`, `sort_order`, `is_default`, `is_archived`, `created_at`) " +
                "VALUES ('${name.replace("'", "''")}', '$kind', '$iconKey', '$colorHex', $sortOrder, 0, 0, $now)",
        )
    }
}
