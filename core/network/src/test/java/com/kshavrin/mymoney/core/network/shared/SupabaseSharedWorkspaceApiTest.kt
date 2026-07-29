package com.kshavrin.mymoney.core.network.shared

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import java.time.Instant

class SupabaseSharedWorkspaceApiTest {

    // Nested fake — records every arg so tests can assert the API wired things correctly.
    private class FakeSharedWorkspaceRpc : SharedWorkspaceRpc {
        var createWorkspaceResult: Result<SharedWorkspace> = defaultFailure()
        var currentWorkspaceResult: Result<SharedWorkspace?> = defaultFailure()
        var listMembersResult: Result<List<WorkspaceMember>> = defaultFailure()
        var createInviteResult: Result<WorkspaceInvite> = defaultFailure()
        var joinWorkspaceResult: Result<SharedWorkspace> = defaultFailure()
        var revokeInviteResult: Result<Unit> = defaultFailure()
        var leaveWorkspaceResult: Result<Unit> = defaultFailure()
        var deleteWorkspaceResult: Result<Unit> = defaultFailure()

        var lastCreateWorkspaceName: String? = null
        var lastListMembersWorkspaceId: String? = null
        var lastCreateInviteWorkspaceId: String? = null
        var lastCreateInviteTokenHash: String? = null
        var lastJoinWorkspaceToken: String? = null
        var lastRevokeInviteId: String? = null
        var lastLeaveWorkspaceId: String? = null
        var lastDeleteWorkspaceId: String? = null

        override suspend fun createWorkspace(name: String): Result<SharedWorkspace> {
            lastCreateWorkspaceName = name
            return createWorkspaceResult
        }

        override suspend fun currentWorkspace(): Result<SharedWorkspace?> = currentWorkspaceResult

        override suspend fun listMembers(workspaceId: String): Result<List<WorkspaceMember>> {
            lastListMembersWorkspaceId = workspaceId
            return listMembersResult
        }

        override suspend fun createInvite(workspaceId: String, tokenHash: String): Result<WorkspaceInvite> {
            lastCreateInviteWorkspaceId = workspaceId
            lastCreateInviteTokenHash = tokenHash
            return createInviteResult
        }

        override suspend fun joinWorkspace(token: String): Result<SharedWorkspace> {
            lastJoinWorkspaceToken = token
            return joinWorkspaceResult
        }

        override suspend fun revokeInvite(inviteId: String): Result<Unit> {
            lastRevokeInviteId = inviteId
            return revokeInviteResult
        }

        override suspend fun leaveWorkspace(workspaceId: String): Result<Unit> {
            lastLeaveWorkspaceId = workspaceId
            return leaveWorkspaceResult
        }

        override suspend fun deleteWorkspace(workspaceId: String): Result<Unit> {
            lastDeleteWorkspaceId = workspaceId
            return deleteWorkspaceResult
        }

        private companion object {
            fun <T> defaultFailure(): Result<T> = Result.failure(NotImplementedError("stub not set"))
        }
    }

    private val fakeRpc = FakeSharedWorkspaceRpc()

    // Use a real InviteTokenFactory (SecureRandom); tests compute expected hashes from
    // the returned plaintext rather than requiring deterministic byte sequences.
    private val tokenFactory = InviteTokenFactory(SecureRandom())
    private val api = SupabaseSharedWorkspaceApi(rpc = fakeRpc, tokenFactory = tokenFactory)

    private val stubWorkspace = SharedWorkspace(
        id = "ws-1",
        name = "Test Workspace",
        ownerId = "user-1",
        createdAt = Instant.EPOCH,
    )

    private val stubInvite = WorkspaceInvite(
        id = "inv-1",
        workspaceId = "ws-1",
        role = WorkspaceRole.Editor,
        expiresAt = Instant.EPOCH.plusSeconds(86_400),
    )

    // --- createInvite: the critical hashed-invite invariant ---

    @Test
    fun `createInvite sends sha256 hash of returned plaintext to rpc`() = runTest {
        fakeRpc.createInviteResult = Result.success(stubInvite)

        val result = api.createInvite("ws-1").getOrThrow()

        // The value sent over the wire must equal SHA-256(plaintext returned to caller).
        // This is the seam that mirrors join_workspace()'s encode(digest(p_token,'sha256'),'hex').
        val expectedHash = tokenFactory.hash(result.token)
        assertEquals(expectedHash, fakeRpc.lastCreateInviteTokenHash)
    }

    @Test
    fun `createInvite does not send plaintext to rpc`() = runTest {
        fakeRpc.createInviteResult = Result.success(stubInvite)

        val result = api.createInvite("ws-1").getOrThrow()

        // The RPC must receive the hash, not the plaintext token.
        assertNotEquals(result.token, fakeRpc.lastCreateInviteTokenHash)
    }

    @Test
    fun `createInvite passes workspace id to rpc`() = runTest {
        fakeRpc.createInviteResult = Result.success(stubInvite)

        api.createInvite("ws-42")

        assertEquals("ws-42", fakeRpc.lastCreateInviteWorkspaceId)
    }

    @Test
    fun `createInvite wraps rpc invite in CreatedInvite preserving all fields`() = runTest {
        fakeRpc.createInviteResult = Result.success(stubInvite)

        val result = api.createInvite("ws-1").getOrThrow()

        assertEquals(stubInvite, result.invite)
    }

    @Test
    fun `createInvite propagates rpc failure without swallowing it`() = runTest {
        val error = RuntimeException("rpc network error")
        fakeRpc.createInviteResult = Result.failure(error)

        val result = api.createInvite("ws-1")

        assertTrue(result.isFailure)
        assertEquals(error, result.exceptionOrNull())
    }

    // --- pass-through delegation for every other method ---

    @Test
    fun `createWorkspace delegates to rpc and returns its result`() = runTest {
        fakeRpc.createWorkspaceResult = Result.success(stubWorkspace)

        val result = api.createWorkspace("My Workspace").getOrThrow()

        assertEquals(stubWorkspace, result)
        assertEquals("My Workspace", fakeRpc.lastCreateWorkspaceName)
    }

    @Test
    fun `currentWorkspace delegates to rpc and returns workspace when present`() = runTest {
        fakeRpc.currentWorkspaceResult = Result.success(stubWorkspace)

        val result = api.currentWorkspace().getOrThrow()

        assertEquals(stubWorkspace, result)
    }

    @Test
    fun `currentWorkspace delegates to rpc and returns null when user has no workspace`() = runTest {
        fakeRpc.currentWorkspaceResult = Result.success(null)

        val result = api.currentWorkspace().getOrThrow()

        assertEquals(null, result)
    }

    @Test
    fun `listMembers delegates to rpc with correct workspace id`() = runTest {
        val member = WorkspaceMember(
            userId = "user-1",
            email = "owner@example.com",
            role = WorkspaceRole.Owner,
            joinedAt = Instant.EPOCH,
            active = true,
        )
        fakeRpc.listMembersResult = Result.success(listOf(member))

        val result = api.listMembers("ws-1").getOrThrow()

        assertEquals(listOf(member), result)
        assertEquals("ws-1", fakeRpc.lastListMembersWorkspaceId)
    }

    @Test
    fun `joinWorkspace delegates to rpc passing plaintext token unchanged`() = runTest {
        fakeRpc.joinWorkspaceResult = Result.success(stubWorkspace)

        val result = api.joinWorkspace("plain-invite-token").getOrThrow()

        assertEquals(stubWorkspace, result)
        assertEquals("plain-invite-token", fakeRpc.lastJoinWorkspaceToken)
    }

    @Test
    fun `revokeInvite delegates to rpc with correct invite id`() = runTest {
        fakeRpc.revokeInviteResult = Result.success(Unit)

        api.revokeInvite("inv-99").getOrThrow()

        assertEquals("inv-99", fakeRpc.lastRevokeInviteId)
    }

    @Test
    fun `leaveWorkspace delegates to rpc with correct workspace id`() = runTest {
        fakeRpc.leaveWorkspaceResult = Result.success(Unit)

        api.leaveWorkspace("ws-1").getOrThrow()

        assertEquals("ws-1", fakeRpc.lastLeaveWorkspaceId)
    }

    @Test
    fun `deleteWorkspace delegates to rpc with correct workspace id`() = runTest {
        fakeRpc.deleteWorkspaceResult = Result.success(Unit)

        api.deleteWorkspace("ws-1").getOrThrow()

        assertEquals("ws-1", fakeRpc.lastDeleteWorkspaceId)
    }
}
