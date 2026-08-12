revoke all on table public.workspaces from public, anon, authenticated;
revoke all on table public.workspace_members from public, anon, authenticated;
revoke all on table public.workspace_invites from public, anon, authenticated;

grant select (id, name, owner_id, created_at)
    on table public.workspaces to authenticated;
grant select (workspace_id, user_id, role, joined_at, active)
    on table public.workspace_members to authenticated;

drop function public.create_invite(uuid, text);

create function public.create_invite(p_workspace_id uuid, p_token_hash text)
    returns table (
        id uuid,
        workspace_id uuid,
        role public.workspace_role,
        expires_at timestamptz
    )
    language plpgsql
    security definer
    set search_path = ''
as $$
declare
    v_user uuid := auth.uid();
begin
    if not public.is_active_member(p_workspace_id, v_user) then
        raise exception 'not a workspace member' using errcode = '42501';
    end if;

    return query
        insert into public.workspace_invites as invite (workspace_id, token_hash, created_by)
        values (p_workspace_id, p_token_hash, v_user)
        returning invite.id, invite.workspace_id, invite.role, invite.expires_at;
end;
$$;

revoke all on function public.create_invite(uuid, text) from public, anon;
grant execute on function public.create_invite(uuid, text) to authenticated;
