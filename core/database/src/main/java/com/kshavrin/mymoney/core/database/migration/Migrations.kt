package com.kshavrin.mymoney.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `goal` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`icon_key` TEXT NOT NULL, " +
                "`color_hex` TEXT NOT NULL, " +
                "`account_id` INTEGER NOT NULL, " +
                "`variant` TEXT NOT NULL, " +
                "`target_amount` REAL NOT NULL, " +
                "`starting_capital` REAL NOT NULL, " +
                "`monthly_contribution` REAL NOT NULL, " +
                "`annual_rate_percent` REAL, " +
                "`term_date` INTEGER, " +
                "`created_at` INTEGER NOT NULL, " +
                "`updated_at` INTEGER NOT NULL, " +
                "`is_archived` INTEGER NOT NULL)",
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `goal` ADD COLUMN `contribution_breakdown` TEXT")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `goal` ADD COLUMN `down_payment` REAL")
        db.execSQL("ALTER TABLE `goal` ADD COLUMN `term_months` INTEGER")
    }
}
