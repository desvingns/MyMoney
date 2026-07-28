-- Workspace-scoped immutable operation journal and conflict queue.
-- Depends on 0001_shared_workspaces.sql (workspaces, workspace_members, is_active_member).

create type public.entity_kind as enum ('transaction', 'account', 'category');
create type public.conflict_status as enum ('pending', 'resolved');

-- Append-only journal. server_sequence is assigned by the DB identity column and is
-- monotonically increasing per Postgres instance, making cursor-based pull safe.
create table public.operations (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references public.workspaces (id) on delete cascade,
    idempotency_key text not null,
    server_sequence bigint not null generated always as identity,
    base_sequence bigint not null,
    author_id uuid not null references auth.users (id) on delete cascade,
    device_id text not null,
    entity_kind public.entity_kind not null,
    entity_id uuid not null,
    payload jsonb,
    tombstone boolean not null default false,
    created_at timestamptz not null default now(),
    unique (workspace_id, idempotency_key)
);

create index ix_operations_workspace_seq on public.operations (workspace_id, server_sequence);
create index ix_operations_entity on public.operations (workspace_id, entity_kind, entity_id);

-- Conflict queue. Historical operations are never mutated; resolution appends a
-- superseding operation and records its id in resolved_into_id.
create table public.conflicts (
    id uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references public.workspaces (id) on delete cascade,
    entity_kind public.entity_kind not null,
    entity_id uuid not null,
    operation_a_id uuid not null references public.operations (id),
    operation_b_id uuid not null references public.operations (id),
    status public.conflict_status not null default 'pending',
    resolver_id uuid references auth.users (id),
    resolved_into_id uuid references public.operations (id),
    created_at timestamptz not null default now(),
    resolved_at timestamptz
);

create index ix_conflicts_workspace_pending on public.conflicts (workspace_id)
    where status = 'pending';
create index ix_conflicts_entity on public.conflicts (workspace_id, entity_kind, entity_id);

-- Enforces at most one pending conflict per entity at the DB level. push_operation
-- treats a unique_violation on this index as a no-op: a concurrent pusher's conflict
-- row already captures the divergence.
create unique index ux_one_pending_conflict_per_entity
    on public.conflicts (workspace_id, entity_kind, entity_id)
    where status = 'pending';

alter table public.operations enable row level security;
alter table public.conflicts enable row level security;

-- Cross-workspace reads are blocked by the membership check. All writes go through
-- SECURITY DEFINER RPCs; no direct INSERT/UPDATE/DELETE policies are granted.
create policy operations_select_members on public.operations
    for select using (public.is_active_member(workspace_id, auth.uid()));

create policy conflicts_select_members on public.conflicts
    for select using (public.is_active_member(workspace_id, auth.uid()));

-- Idempotent push: the INSERT ... ON CONFLICT DO NOTHING is atomic, so two
-- concurrent retries on the same (workspace_id, idempotency_key) cannot both
-- insert; the second sees zero RETURNING rows and falls back to a SELECT.
-- Conflict detection: when a concurrent edit on the same entity landed after the
-- caller's base_sequence, open a conflict row. The new operation is still accepted
-- (non-blocking). ux_one_pending_conflict_per_entity enforces the one-pending-per-
-- entity invariant at the DB level; a unique_violation from a concurrent push is
-- treated as a no-op.
create or replace function public.push_operation(
    p_workspace_id uuid,
    p_idempotency_key text,
    p_base_sequence bigint,
    p_device_id text,
    p_entity_kind public.entity_kind,
    p_entity_id uuid,
    p_payload jsonb,
    p_tombstone boolean
) returns public.operations
    language plpgsql
    security definer
    set search_path = public
as $$
declare
    v_user uuid := auth.uid();
    v_op public.operations;
    v_conflict_op public.operations;
begin
    if not public.is_active_member(p_workspace_id, v_user) then
        raise exception 'not a workspace member' using errcode = '42501';
    end if;

    -- Atomic idempotent insert: if the key already exists, DO NOTHING and RETURNING
    -- produces zero rows (v_op stays NULL).
    insert into public.operations (
        workspace_id, idempotency_key, base_sequence, author_id, device_id,
        entity_kind, entity_id, payload, tombstone
    ) values (
        p_workspace_id, p_idempotency_key, p_base_sequence, v_user, p_device_id,
        p_entity_kind, p_entity_id, p_payload, p_tombstone
    ) on conflict (workspace_id, idempotency_key) do nothing
    returning * into v_op;

    if v_op is null then
        -- The row was inserted by an earlier call (or a concurrent retry that won
        -- the race); return the already-accepted operation.
        select * into v_op
        from public.operations
        where workspace_id = p_workspace_id
          and idempotency_key = p_idempotency_key;
        return v_op;
    end if;

    -- Conflict detection: find the most recent accepted operation on the same entity
    -- that landed after the caller's base_sequence.
    select * into v_conflict_op
    from public.operations
    where workspace_id = p_workspace_id
      and entity_kind = p_entity_kind
      and entity_id = p_entity_id
      and id <> v_op.id
      and server_sequence > p_base_sequence
    order by server_sequence desc
    limit 1;

    if v_conflict_op is not null then
        begin
            insert into public.conflicts (
                workspace_id, entity_kind, entity_id, operation_a_id, operation_b_id
            ) values (
                p_workspace_id, p_entity_kind, p_entity_id, v_conflict_op.id, v_op.id
            );
        exception when unique_violation then
            -- A concurrent push already created the pending conflict for this entity.
            null;
        end;
    end if;

    return v_op;
end;
$$;

-- Cursor-paginated pull ordered by server_sequence ascending. author_id is
-- intentionally excluded: author attribution is exposed only to the conflict UI.
create or replace function public.pull_operations(
    p_workspace_id uuid,
    p_after_sequence bigint,
    p_limit integer default 100
) returns table (
    id uuid,
    workspace_id uuid,
    idempotency_key text,
    server_sequence bigint,
    base_sequence bigint,
    device_id text,
    entity_kind public.entity_kind,
    entity_id uuid,
    payload jsonb,
    tombstone boolean,
    created_at timestamptz
)
    language plpgsql
    security definer
    set search_path = public
as $$
begin
    if not public.is_active_member(p_workspace_id, auth.uid()) then
        raise exception 'not a workspace member' using errcode = '42501';
    end if;

    return query
    select
        o.id,
        o.workspace_id,
        o.idempotency_key,
        o.server_sequence,
        o.base_sequence,
        o.device_id,
        o.entity_kind,
        o.entity_id,
        o.payload,
        o.tombstone,
        o.created_at
    from public.operations o
    where o.workspace_id = p_workspace_id
      and o.server_sequence > p_after_sequence
    order by o.server_sequence asc
    limit p_limit;
end;
$$;

-- Returns pending conflicts with both participant operations embedded as JSON and
-- their author ids as separate top-level columns. author_id is surfaced here because
-- this path feeds the conflict-resolution UI — the only place attribution is shown.
create or replace function public.list_pending_conflicts(
    p_workspace_id uuid
) returns table (
    id uuid,
    workspace_id uuid,
    entity_kind public.entity_kind,
    entity_id uuid,
    operation_a json,
    operation_b json,
    author_a_id uuid,
    author_b_id uuid,
    status public.conflict_status,
    resolver_id uuid,
    resolved_into_id uuid,
    created_at timestamptz,
    resolved_at timestamptz
)
    language plpgsql
    security definer
    set search_path = public
as $$
begin
    if not public.is_active_member(p_workspace_id, auth.uid()) then
        raise exception 'not a workspace member' using errcode = '42501';
    end if;

    return query
    select
        c.id,
        c.workspace_id,
        c.entity_kind,
        c.entity_id,
        row_to_json(a.*),
        row_to_json(b.*),
        a.author_id,
        b.author_id,
        c.status,
        c.resolver_id,
        c.resolved_into_id,
        c.created_at,
        c.resolved_at
    from public.conflicts c
    join public.operations a on a.id = c.operation_a_id
    join public.operations b on b.id = c.operation_b_id
    where c.workspace_id = p_workspace_id
      and c.status = 'pending'
    order by c.created_at asc;
end;
$$;

-- Membership-authorized resolution. Appends a superseding operation carrying the
-- winning side's payload so the winner becomes the latest accepted state for the
-- entity. The conflict row is closed; historical operations are never mutated.
-- The idempotency check runs before the status guard so a network retry after a
-- successful commit returns the superseding op instead of raising.
create or replace function public.resolve_conflict(
    p_conflict_id uuid,
    p_winner_operation_id uuid
) returns public.operations
    language plpgsql
    security definer
    set search_path = public
as $$
declare
    v_user uuid := auth.uid();
    v_conflict public.conflicts;
    v_winner public.operations;
    v_max_seq bigint;
    v_superseding public.operations;
    v_resolution_key text := 'resolve:' || p_conflict_id::text;
begin
    select * into v_conflict from public.conflicts where id = p_conflict_id for update;

    if v_conflict is null then
        raise exception 'conflict not found' using errcode = 'P0002';
    end if;

    if not public.is_active_member(v_conflict.workspace_id, v_user) then
        raise exception 'not a workspace member' using errcode = '42501';
    end if;

    -- Idempotency probe runs BEFORE the status guard so a retry after a successful
    -- commit returns the superseding operation rather than raising 'already resolved'.
    select * into v_superseding
    from public.operations
    where workspace_id = v_conflict.workspace_id
      and idempotency_key = v_resolution_key;

    if v_superseding is not null then
        return v_superseding;
    end if;

    if v_conflict.status <> 'pending' then
        raise exception 'conflict already resolved' using errcode = 'P0001';
    end if;

    if p_winner_operation_id <> v_conflict.operation_a_id
        and p_winner_operation_id <> v_conflict.operation_b_id then
        raise exception 'winner must be one of the two conflict participants'
            using errcode = 'P0001';
    end if;

    select * into v_winner from public.operations where id = p_winner_operation_id;

    select coalesce(max(server_sequence), 0) into v_max_seq
    from public.operations
    where workspace_id = v_conflict.workspace_id;

    insert into public.operations (
        workspace_id, idempotency_key, base_sequence, author_id, device_id,
        entity_kind, entity_id, payload, tombstone
    ) values (
        v_conflict.workspace_id, v_resolution_key, v_max_seq, v_user,
        'conflict-resolution', v_conflict.entity_kind, v_conflict.entity_id,
        v_winner.payload, v_winner.tombstone
    ) returning * into v_superseding;

    update public.conflicts
    set status = 'resolved',
        resolver_id = v_user,
        resolved_into_id = v_superseding.id,
        resolved_at = now()
    where id = p_conflict_id;

    return v_superseding;
end;
$$;

grant execute on function public.push_operation(uuid, text, bigint, text, public.entity_kind, uuid, jsonb, boolean) to authenticated;
grant execute on function public.pull_operations(uuid, bigint, integer) to authenticated;
grant execute on function public.list_pending_conflicts(uuid) to authenticated;
grant execute on function public.resolve_conflict(uuid, uuid) to authenticated;
