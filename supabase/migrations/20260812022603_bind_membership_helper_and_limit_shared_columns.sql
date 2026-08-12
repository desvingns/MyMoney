create or replace function public.is_active_member(p_workspace uuid, p_user uuid)
    returns boolean
    language sql
    stable
    security definer
    set search_path = ''
as $$
    select
        p_user = (select auth.uid())
        and exists (
            select 1
            from public.workspace_members
            where workspace_id = p_workspace
              and user_id = p_user
              and active
        );
$$;

revoke select on table public.workspaces from authenticated;
revoke select on table public.workspace_members from authenticated;
revoke select on table public.workspace_invites from authenticated;

grant select (id, name, owner_id, created_at)
    on table public.workspaces to authenticated;
grant select (workspace_id, user_id, role, joined_at, active)
    on table public.workspace_members to authenticated;
grant select (
    id,
    workspace_id,
    role,
    created_by,
    created_at,
    expires_at,
    revoked_at,
    consumed_at,
    consumed_by
) on table public.workspace_invites to authenticated;
