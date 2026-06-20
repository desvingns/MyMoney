package com.kshavrin.mymoney.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 =
    object : Migration(1, 2) {
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

val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `goal` ADD COLUMN `contribution_breakdown` TEXT")
        }
    }

val MIGRATION_3_4 =
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `goal` ADD COLUMN `down_payment` REAL")
            db.execSQL("ALTER TABLE `goal` ADD COLUMN `term_months` INTEGER")
        }
    }

val MIGRATION_4_5 =
    object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("UPDATE `transaction` SET `amount` = ROUND(`amount`, 2) WHERE `amount` IS NOT NULL")
            db.execSQL("UPDATE `transaction` SET `to_amount` = ROUND(`to_amount`, 2) WHERE `to_amount` IS NOT NULL")
            db.execSQL("UPDATE `account` SET `initial_balance` = ROUND(`initial_balance`, 2) WHERE `initial_balance` IS NOT NULL")
            db.execSQL("UPDATE `goal` SET `target_amount` = ROUND(`target_amount`, 2) WHERE `target_amount` IS NOT NULL")
            db.execSQL("UPDATE `goal` SET `starting_capital` = ROUND(`starting_capital`, 2) WHERE `starting_capital` IS NOT NULL")
            db.execSQL("UPDATE `goal` SET `monthly_contribution` = ROUND(`monthly_contribution`, 2) WHERE `monthly_contribution` IS NOT NULL")
            db.execSQL("UPDATE `goal` SET `down_payment` = ROUND(`down_payment`, 2) WHERE `down_payment` IS NOT NULL")
            db.execSQL("UPDATE `budget` SET `amount` = ROUND(`amount`, 2) WHERE `amount` IS NOT NULL")
        }
    }

val MIGRATION_5_6 =
    object : Migration(5, 6) {
        private const val RATE_SNAPSHOT_MILLIS = 1781913600000L

        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "INSERT OR IGNORE INTO `currency` " +
                    "(`code`, `symbol`, `name`, `decimal_digits`, `is_active`, `sort_order`) " +
                    "VALUES ('KZT', '₸', 'Kazakhstani Tenge', 2, 1, 21)",
            )
            db.execSQL(
                "INSERT OR IGNORE INTO `currency` " +
                    "(`code`, `symbol`, `name`, `decimal_digits`, `is_active`, `sort_order`) " +
                    "VALUES ('AED', 'د.إ', 'UAE Dirham', 2, 1, 22)",
            )

            listOf(
                "USD" to 1.146893,
                "RUB" to 84.181245,
                "RSD" to 117.390388,
                "KZT" to 559.417885,
                "AED" to 4.211961,
            ).forEach { (toCode, rate) ->
                db.execSQL(
                    "INSERT OR IGNORE INTO `currency_rate` " +
                        "(`from_currency_id`, `to_currency_id`, `rate`, `updated_at`) " +
                        "SELECT base.`id`, target.`id`, $rate, $RATE_SNAPSHOT_MILLIS " +
                        "FROM `currency` base, `currency` target " +
                        "WHERE base.`code` = 'EUR' AND target.`code` = '$toCode'",
                )
            }
        }
    }
