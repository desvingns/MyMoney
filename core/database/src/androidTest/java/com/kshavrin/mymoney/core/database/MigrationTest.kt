package com.kshavrin.mymoney.core.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @Test
    fun v1_initial_no_migration_required() {
        // v1 is the initial schema. Migration scaffolding will arrive when v2 ships.
    }
}
