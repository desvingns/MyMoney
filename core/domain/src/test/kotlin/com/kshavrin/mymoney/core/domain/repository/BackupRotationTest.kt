package com.kshavrin.mymoney.core.domain.repository

import com.kshavrin.mymoney.core.domain.model.BackupFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRotationTest {
    private fun backup(
        name: String,
        lastModifiedEpochMs: Long,
    ) =
        BackupFile(name = name, uriString = "content://backups/$name", lastModifiedEpochMs = lastModifiedEpochMs)

    @Test
    fun `keep newest is three`() {
        assertEquals(3, BackupRepository.KEEP_NEWEST)
    }

    @Test
    fun `empty list deletes nothing`() {
        assertTrue(BackupRepository.backupsToDelete(emptyList()).isEmpty())
    }

    @Test
    fun `one file deletes nothing`() {
        val files = listOf(backup("a", 100L))

        assertTrue(BackupRepository.backupsToDelete(files).isEmpty())
    }

    @Test
    fun `two files delete nothing`() {
        val files = listOf(backup("a", 100L), backup("b", 200L))

        assertTrue(BackupRepository.backupsToDelete(files).isEmpty())
    }

    @Test
    fun `three files delete nothing`() {
        val files = listOf(backup("a", 100L), backup("b", 200L), backup("c", 300L))

        assertTrue(BackupRepository.backupsToDelete(files).isEmpty())
    }

    @Test
    fun `four files delete the single oldest`() {
        val newest3 = listOf(backup("b", 200L), backup("c", 300L), backup("d", 400L))
        val oldest = backup("a", 100L)
        val files = listOf(newest3[1], oldest, newest3[2], newest3[0])

        val toDelete = BackupRepository.backupsToDelete(files)

        assertEquals(listOf(oldest), toDelete)
    }

    @Test
    fun `six files delete the three oldest`() {
        val oldest3 = listOf(backup("a", 100L), backup("b", 200L), backup("c", 300L))
        val newest3 = listOf(backup("d", 400L), backup("e", 500L), backup("f", 600L))
        val shuffled = listOf(newest3[1], oldest3[2], newest3[0], oldest3[0], newest3[2], oldest3[1])

        val toDelete = BackupRepository.backupsToDelete(shuffled)

        assertEquals(oldest3, toDelete)
    }

    @Test
    fun `selection is ordered by last modified not by input order`() {
        val files =
            listOf(
                backup("inserted-third", 999L),
                backup("inserted-first", 10L),
                backup("inserted-second", 500L),
                backup("inserted-fourth", 1L),
            )

        val toDelete = BackupRepository.backupsToDelete(files)

        assertEquals(listOf(backup("inserted-fourth", 1L)), toDelete)
    }

    @Test
    fun `retained files are exactly the three with the highest timestamps`() {
        val files =
            listOf(
                backup("a", 1L),
                backup("b", 9L),
                backup("c", 3L),
                backup("d", 7L),
                backup("e", 5L),
            )

        val deleted = BackupRepository.backupsToDelete(files).toSet()
        val retained = files.toSet() - deleted

        assertEquals(setOf(backup("b", 9L), backup("d", 7L), backup("e", 5L)), retained)
    }

    @Test
    fun `deleting then retaining covers the whole input exactly once`() {
        val files =
            listOf(
                backup("a", 100L),
                backup("b", 200L),
                backup("c", 300L),
                backup("d", 400L),
                backup("e", 500L),
            )

        val deleted = BackupRepository.backupsToDelete(files)
        val retained = files - deleted.toSet()

        assertEquals(2, deleted.size)
        assertEquals(BackupRepository.KEEP_NEWEST, retained.size)
        assertEquals(files.toSet(), deleted.toSet() + retained.toSet())
    }
}
