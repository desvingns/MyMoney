-- Realtime is only a foreground notification channel. Clients always reconcile
-- through pull_operations, which remains the authoritative cursor-based read path.

revoke all on table public.operations from anon;
revoke all on table public.operations from authenticated;
revoke all on table public.conflicts from anon;
revoke all on table public.conflicts from authenticated;

grant select on table public.workspaces to authenticated;
grant select on table public.workspace_members to authenticated;
grant select on table public.workspace_invites to authenticated;

grant select (
    id,
    workspace_id,
    idempotency_key,
    server_sequence,
    base_sequence,
    device_id,
    entity_kind,
    entity_id,
    payload,
    tombstone,
    created_at
) on table public.operations to authenticated;

alter publication supabase_realtime add table public.operations;
