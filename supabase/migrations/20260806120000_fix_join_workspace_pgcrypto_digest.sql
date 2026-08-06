create or replace function public.join_workspace(p_token text)
    returns public.workspaces
    language plpgsql
    security definer
    set search_path = public
as $$
declare
    v_user uuid := auth.uid();
    v_hash text := encode(extensions.digest(p_token, 'sha256'::text), 'hex');
    v_invite public.workspace_invites;
    v_workspace public.workspaces;
    v_active_count integer;
begin
    if v_user is null then
        raise exception 'authentication required' using errcode = '28000';
    end if;

    -- Locks the invite row so concurrent joins on the same token serialize.
    select * into v_invite
    from public.workspace_invites
    where token_hash = v_hash
    for update;

    if v_invite is null then
        raise exception 'invalid invite' using errcode = 'P0002';
    end if;
    if v_invite.revoked_at is not null then
        raise exception 'invite revoked' using errcode = 'P0001';
    end if;
    if v_invite.consumed_at is not null then
        raise exception 'invite already used' using errcode = 'P0001';
    end if;
    if v_invite.expires_at <= now() then
        raise exception 'invite expired' using errcode = 'P0001';
    end if;

    -- Locks the workspace so the five-member cap is race-safe under concurrent joins.
    select * into v_workspace
    from public.workspaces
    where id = v_invite.workspace_id
    for update;

    select count(*) into v_active_count
    from public.workspace_members
    where workspace_id = v_invite.workspace_id
      and active;

    if v_active_count >= 5 then
        raise exception 'workspace is full' using errcode = 'P0001';
    end if;

    -- Reject a caller who is already an active member (e.g. the owner replaying
    -- their own invite token). The ON CONFLICT branch below would otherwise
    -- overwrite their role to editor while workspaces.owner_id still points at
    -- them, orphaning the workspace on their next leave. Raise before touching
    -- workspace_members or consuming the invite, so nothing mutates.
    if exists (
        select 1
        from public.workspace_members
        where workspace_id = v_invite.workspace_id
          and user_id = v_user
          and active
    ) then
        raise exception 'already an active member' using errcode = 'P0001';
    end if;

    -- Reactivates a prior membership or inserts a new one; fails on
    -- ux_one_active_membership if the caller already belongs to another workspace.
    insert into public.workspace_members (workspace_id, user_id, role, active)
    values (v_invite.workspace_id, v_user, v_invite.role, true)
    on conflict (workspace_id, user_id)
    do update set active = true, role = excluded.role, joined_at = now();

    update public.workspace_invites
    set consumed_at = now(), consumed_by = v_user
    where id = v_invite.id;

    return v_workspace;
end;
$$;
