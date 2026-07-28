-- MyMoney shared workspaces: schema, RLS, and lifecycle RPCs.
-- Applied manually via the Supabase Dashboard SQL Editor (no CLI on the dev machine).
-- Constraints encoded here: owner/editor roles, max five active members, one active
-- workspace per user, hashed single-use invites valid 24h, TLS+RLS only (no E2EE).

create extension if not exists pgcrypto;

create type public.workspace_role as enum ('owner', 'editor');

create table public.workspaces (
    id uuid primary key default gen_random_uuid(),
    name text not null check (char_length(name) between 1 and 80),
    owner_id uuid not null references auth.users (id) on delete cascade,
    created_at timestamptz not null default now()
);

create table public.workspace_members (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references public.workspaces (id) on delete cascade,
    user_id uuid not null references auth.users (id) on delete cascade,
    role public.workspace_role not null default 'editor',
    joined_at timestamptz not null default now(),
    active boolean not null default true,
    unique (workspace_id, user_id)
);

-- One active workspace per user in the MVP.
create unique index ux_one_active_membership
    on public.workspace_members (user_id)
    where active;

create index ix_members_workspace_active
    on public.workspace_members (workspace_id)
    where active;

create table public.workspace_invites (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references public.workspaces (id) on delete cascade,
    token_hash text not null unique,
    role public.workspace_role not null default 'editor' check (role = 'editor'),
    created_by uuid not null references auth.users (id) on delete cascade,
    created_at timestamptz not null default now(),
    expires_at timestamptz not null default (now() + interval '24 hours'),
    revoked_at timestamptz,
    consumed_at timestamptz,
    consumed_by uuid references auth.users (id) on delete set null
);

create index ix_invites_workspace on public.workspace_invites (workspace_id);

-- SECURITY DEFINER membership probe used by RLS policies. Referencing
-- workspace_members directly inside its own policy would recurse; this bypasses RLS.
create or replace function public.is_active_member(p_workspace uuid, p_user uuid)
    returns boolean
    language sql
    stable
    security definer
    set search_path = public
as $$
    select exists (
        select 1
        from public.workspace_members
        where workspace_id = p_workspace
          and user_id = p_user
          and active
    );
$$;

alter table public.workspaces enable row level security;
alter table public.workspace_members enable row level security;
alter table public.workspace_invites enable row level security;

-- Reads are scoped to the caller's own workspace; all writes go through the
-- SECURITY DEFINER RPCs below, so no INSERT/UPDATE/DELETE policies are granted.
create policy workspaces_select_members on public.workspaces
    for select using (public.is_active_member(id, auth.uid()));

create policy members_select_co_members on public.workspace_members
    for select using (public.is_active_member(workspace_id, auth.uid()));

create policy invites_select_members on public.workspace_invites
    for select using (public.is_active_member(workspace_id, auth.uid()));

create or replace function public.create_workspace(p_name text)
    returns public.workspaces
    language plpgsql
    security definer
    set search_path = public
as $$
declare
    v_user uuid := auth.uid();
    v_workspace public.workspaces;
begin
    if v_user is null then
        raise exception 'authentication required' using errcode = '28000';
    end if;

    insert into public.workspaces (name, owner_id)
    values (p_name, v_user)
    returning * into v_workspace;

    -- Fails on ux_one_active_membership if the caller already has an active workspace.
    insert into public.workspace_members (workspace_id, user_id, role, active)
    values (v_workspace.id, v_user, 'owner', true);

    return v_workspace;
end;
$$;

create or replace function public.create_invite(p_workspace_id uuid, p_token_hash text)
    returns public.workspace_invites
    language plpgsql
    security definer
    set search_path = public
as $$
declare
    v_user uuid := auth.uid();
    v_invite public.workspace_invites;
begin
    if not public.is_active_member(p_workspace_id, v_user) then
        raise exception 'not a workspace member' using errcode = '42501';
    end if;

    insert into public.workspace_invites (workspace_id, token_hash, created_by)
    values (p_workspace_id, p_token_hash, v_user)
    returning * into v_invite;

    return v_invite;
end;
$$;

create or replace function public.revoke_invite(p_invite_id uuid)
    returns void
    language plpgsql
    security definer
    set search_path = public
as $$
declare
    v_user uuid := auth.uid();
    v_workspace uuid;
begin
    select workspace_id into v_workspace
    from public.workspace_invites
    where id = p_invite_id;

    if v_workspace is null then
        raise exception 'invite not found' using errcode = 'P0002';
    end if;

    if not public.is_active_member(v_workspace, v_user) then
        raise exception 'not a workspace member' using errcode = '42501';
    end if;

    update public.workspace_invites
    set revoked_at = now()
    where id = p_invite_id
      and consumed_at is null
      and revoked_at is null;
end;
$$;

create or replace function public.join_workspace(p_token text)
    returns public.workspaces
    language plpgsql
    security definer
    set search_path = public
as $$
declare
    v_user uuid := auth.uid();
    v_hash text := encode(digest(p_token, 'sha256'), 'hex');
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

create or replace function public.leave_workspace(p_workspace_id uuid)
    returns void
    language plpgsql
    security definer
    set search_path = public
as $$
declare
    v_user uuid := auth.uid();
    v_role public.workspace_role;
    v_new_owner uuid;
begin
    select role into v_role
    from public.workspace_members
    where workspace_id = p_workspace_id
      and user_id = v_user
      and active;

    if v_role is null then
        raise exception 'not a workspace member' using errcode = '42501';
    end if;

    if v_role = 'owner' then
        -- Earliest-joined active editor inherits ownership.
        select user_id into v_new_owner
        from public.workspace_members
        where workspace_id = p_workspace_id
          and active
          and role = 'editor'
        order by joined_at asc, id asc
        limit 1;

        if v_new_owner is null then
            raise exception 'sole owner must delete the workspace instead of leaving'
                using errcode = 'P0001';
        end if;

        update public.workspace_members
        set role = 'owner'
        where workspace_id = p_workspace_id
          and user_id = v_new_owner;

        update public.workspaces
        set owner_id = v_new_owner
        where id = p_workspace_id;
    end if;

    update public.workspace_members
    set active = false
    where workspace_id = p_workspace_id
      and user_id = v_user;
end;
$$;

create or replace function public.delete_workspace(p_workspace_id uuid)
    returns void
    language plpgsql
    security definer
    set search_path = public
as $$
declare
    v_user uuid := auth.uid();
begin
    if not exists (
        select 1 from public.workspaces
        where id = p_workspace_id and owner_id = v_user
    ) then
        raise exception 'only the owner may delete the workspace' using errcode = '42501';
    end if;

    delete from public.workspaces where id = p_workspace_id;
end;
$$;
