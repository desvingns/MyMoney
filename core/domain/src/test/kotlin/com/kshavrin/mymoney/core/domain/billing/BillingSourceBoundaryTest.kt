package com.kshavrin.mymoney.core.domain.billing

import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingSourceBoundaryTest {
    @Test
    fun `core domain billing sources do not import Play Billing`() {
        val sourceFiles = billingSourceFiles()
        val expectedNames = setOf(
            "BillingAvailability.kt",
            "BillingGateway.kt",
            "PurchaseOutcome.kt",
            "SupportProduct.kt",
        )

        assertEquals(expectedNames, sourceFiles.map { it.fileName.toString() }.toSet())

        sourceFiles.forEach { sourceFile ->
            val source = Files.readString(sourceFile)
            assertFalse(
                "${sourceFile.fileName} must not reference com.android.billingclient",
                source.contains("com.android.billingclient"),
            )
        }
    }

    private fun billingSourceFiles(): List<Path> {
        val relativeDirectory = Path.of(
            "core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/billing",
        )
        val repositoryRoot = generateSequence(Path.of("").toAbsolutePath().normalize()) { it.parent }
            .map { it.resolve(relativeDirectory) }
            .firstOrNull(Files::isDirectory)

        assertTrue("Could not locate core/domain billing sources", repositoryRoot != null)
        return Files.list(repositoryRoot!!).use { files ->
            files
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".kt") }
                .sorted()
                .collect(Collectors.toList())
        }
    }
}
