alter table public.workspaces
    add column payer_user_id uuid references auth.users (id),
    add column billing_state text not null default 'active'
        check (billing_state in ('active', 'grace', 'expired')),
    add column billing_state_until timestamptz;

update public.workspaces
set payer_user_id = owner_id
where payer_user_id is null;

alter table public.workspaces
    alter column payer_user_id set not null;

create index ix_workspaces_payer_user_id on public.workspaces (payer_user_id);

create or replace function private.effective_entitlement(p_user uuid)
    returns table (
        source text,
        starts_at timestamptz,
        expires_at timestamptz,
        in_trial boolean
    )
    language sql
    stable
    security definer
    set search_path = ''
as $$
    select
        entitlement.provider,
        entitlement.starts_at,
        entitlement.expires_at,
        (entitlement.metadata ->> 'in_trial') = 'true'
    from public.entitlements as entitlement
    where entitlement.user_id = p_user
      and entitlement.entitlement = 'plus'
      and entitlement.revoked_at is null
      and entitlement.starts_at <= now()
      and (entitlement.expires_at is null or entitlement.expires_at > entitlement.starts_at)
    order by
        coalesce(
            entitlement.expires_at + case
                when entitlement.provider = 'google_play' then interval '7 days'
                else interval '0'
            end,
            'infinity'::timestamptz
        ) desc,
        case when entitlement.provider = 'google_play' then 1 else 0 end desc,
        entitlement.starts_at desc,
        entitlement.id desc
    limit 1;
$$;

create or replace function public.get_my_entitlement()
    returns json
    language plpgsql
    security definer
    set search_path = ''
as $$
declare
    v_user uuid := auth.uid();
    v_entitlement record;
begin
    if v_user is null then
        raise exception 'authentication required' using errcode = '28000';
    end if;

    select * into v_entitlement
    from private.effective_entitlement(v_user);

    if not found then
        return json_build_object(
            'source', null,
            'starts_at', null,
            'expires_at', null,
            'in_trial', false
        );
    end if;

    return json_build_object(
        'source', v_entitlement.source,
        'starts_at', v_entitlement.starts_at,
        'expires_at', v_entitlement.expires_at,
        'in_trial', v_entitlement.in_trial
    );
end;
$$;

create or replace function private.workspace_write_allowed(p_workspace uuid)
    returns boolean
    language sql
    stable
    security definer
    set search_path = ''
as $$
    select coalesce((
        select workspace.billing_state = 'active'
        from public.workspaces as workspace
        where workspace.id = p_workspace
    ), false);
$$;

create or replace function private.recompute_workspace_billing_state()
    returns void
    language plpgsql
    security definer
    set search_path = ''
as $$
begin
    with recomputed as (
        select
            workspace.id,
            case
                when entitlement.source is null then 'expired'
                when entitlement.google_play_state = 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD'
                    and entitlement.expires_at > now() then 'grace'
                when entitlement.google_play_state = 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD'
                    then 'expired'
                when entitlement.expires_at is null or entitlement.expires_at > now() then 'active'
                when entitlement.source = 'google_play' then 'grace'
                else 'expired'
            end as billing_state,
            case
                when entitlement.source is null then null
                when entitlement.google_play_state = 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD'
                    and entitlement.expires_at > now() then entitlement.expires_at
                when entitlement.expires_at is null or entitlement.expires_at > now()
                    then entitlement.expires_at
                when entitlement.source = 'google_play'
                    then entitlement.expires_at + interval '7 days'
                else null
            end as billing_state_until
        from public.workspaces as workspace
        left join lateral (
            select
                effective.source,
                effective.starts_at,
                effective.expires_at,
                source_record.metadata ->> 'google_play_state' as google_play_state
            from private.effective_entitlement(workspace.payer_user_id) as effective
            left join public.entitlements as source_record
                on source_record.user_id = workspace.payer_user_id
               and source_record.provider = effective.source
               and source_record.starts_at = effective.starts_at
               and source_record.expires_at is not distinct from effective.expires_at
               and source_record.revoked_at is null
            order by source_record.id desc
            limit 1
        ) as entitlement
            on true
    )
    update public.workspaces as workspace
    set billing_state = recomputed.billing_state,
        billing_state_until = recomputed.billing_state_until
    from recomputed
    where workspace.id = recomputed.id
      and (
          workspace.billing_state,
          workspace.billing_state_until
      ) is distinct from (
          recomputed.billing_state,
          recomputed.billing_state_until
      );
end;
$$;

create or replace function public.recompute_workspace_billing_state_from_rtdn()
    returns void
    language plpgsql
    security definer
    set search_path = ''
as $$
begin
    perform private.recompute_workspace_billing_state();
end;
$$;

revoke all on function private.effective_entitlement(uuid) from public, anon, authenticated;
revoke all on function public.get_my_entitlement() from public, anon;
revoke all on function private.workspace_write_allowed(uuid) from public, anon, authenticated;
revoke all on function private.recompute_workspace_billing_state() from public, anon, authenticated;
revoke all on function public.recompute_workspace_billing_state_from_rtdn() from public, anon, authenticated;
grant execute on function public.get_my_entitlement() to authenticated;
grant execute on function public.recompute_workspace_billing_state_from_rtdn() to service_role;

create extension if not exists pg_cron;

do $$
begin
    if not exists (
        select 1
        from cron.job
        where jobname = 'recompute_workspace_billing_state'
    ) then
        perform cron.schedule(
            'recompute_workspace_billing_state',
            '* * * * *',
            'select private.recompute_workspace_billing_state();'
        );
    end if;
end;
$$;

create or replace function public.create_workspace(p_name text)
    returns public.workspaces
    language plpgsql
    security definer
    set search_path = ''
as $$
declare
    v_user uuid := auth.uid();
    v_workspace public.workspaces;
    v_entitlement record;
begin
    if v_user is null then
        raise exception 'authentication required' using errcode = '28000';
    end if;

    select * into v_entitlement
    from private.effective_entitlement(v_user);

    if not found then
        raise exception using errcode = 'P0001', message = 'entitlement_required';
    end if;
    if v_entitlement.expires_at is not null and v_entitlement.expires_at <= now() then
        raise exception using errcode = 'P0001', message = 'entitlement_required';
    end if;

    insert into public.workspaces (name, owner_id, payer_user_id)
    values (p_name, v_user, v_user)
    returning * into v_workspace;

    insert into public.workspace_members (workspace_id, user_id, role, active)
    values (v_workspace.id, v_user, 'owner', true);

    return v_workspace;
end;
$$;

create or replace function public.create_invite(p_workspace_id uuid, p_token_hash text)
    returns jsonb
    language plpgsql
    security definer
    set search_path = ''
as $$
declare
    v_user uuid := auth.uid();
    v_invite public.workspace_invites;
begin
    if not public.is_active_member(p_workspace_id, v_user) then
        raise exception 'not a workspace member' using errcode = '42501';
    end if;
    if not private.workspace_write_allowed(p_workspace_id) then
        raise exception using errcode = 'P0001', message = 'entitlement_required';
    end if;

    insert into public.workspace_invites (workspace_id, token_hash, created_by)
    values (p_workspace_id, p_token_hash, v_user)
    returning * into v_invite;

    return jsonb_build_object(
        'id', v_invite.id,
        'workspace_id', v_invite.workspace_id,
        'role', v_invite.role,
        'expires_at', v_invite.expires_at
    );
end;
$$;

create or replace function public.revoke_invite(p_invite_id uuid)
    returns void
    language plpgsql
    security definer
    set search_path = ''
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
    if not private.workspace_write_allowed(v_workspace) then
        raise exception using errcode = 'P0001', message = 'entitlement_required';
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
    set search_path = ''
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

    select * into v_workspace
    from public.workspaces
    where id = v_invite.workspace_id
    for update;

    if not private.workspace_write_allowed(v_workspace.id) then
        raise exception using errcode = 'P0001', message = 'entitlement_required';
    end if;

    select count(*) into v_active_count
    from public.workspace_members
    where workspace_id = v_invite.workspace_id
      and active;

    if v_active_count >= 5 then
        raise exception 'workspace is full' using errcode = 'P0001';
    end if;
    if exists (
        select 1
        from public.workspace_members
        where workspace_id = v_invite.workspace_id
          and user_id = v_user
          and active
    ) then
        raise exception 'already an active member' using errcode = 'P0001';
    end if;

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
    set search_path = ''
as $$
declare
    v_user uuid := auth.uid();
    v_role public.workspace_role;
    v_new_owner uuid;
    v_promoted integer;
begin
    perform 1
    from public.workspaces
    where id = p_workspace_id
    for update;

    select role into v_role
    from public.workspace_members
    where workspace_id = p_workspace_id
      and user_id = v_user
      and active;

    if v_role is null then
        raise exception 'not a workspace member' using errcode = '42501';
    end if;
    if not private.workspace_write_allowed(p_workspace_id) then
        raise exception using errcode = 'P0001', message = 'entitlement_required';
    end if;

    if v_role = 'owner' then
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
          and user_id = v_new_owner
          and active = true;

        get diagnostics v_promoted = row_count;
        if v_promoted <> 1 then
            raise exception 'concurrent membership change, retry' using errcode = 'P0001';
        end if;

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
    set search_path = ''
as $$
declare
    v_user uuid := auth.uid();
begin
    if not exists (
        select 1
        from public.workspaces
        where id = p_workspace_id
          and owner_id = v_user
    ) then
        raise exception 'only the owner may delete the workspace' using errcode = '42501';
    end if;
    if not private.workspace_write_allowed(p_workspace_id) then
        raise exception using errcode = 'P0001', message = 'entitlement_required';
    end if;

    delete from public.workspaces where id = p_workspace_id;
end;
$$;

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
    set search_path = ''
as $$
declare
    v_user uuid := auth.uid();
    v_op public.operations;
    v_conflict_op public.operations;
begin
    if not public.is_active_member(p_workspace_id, v_user) then
        raise exception 'not a workspace member' using errcode = '42501';
    end if;
    if not private.workspace_write_allowed(p_workspace_id) then
        raise exception using errcode = 'P0001', message = 'entitlement_required';
    end if;

    insert into public.operations (
        workspace_id, idempotency_key, base_sequence, author_id, device_id,
        entity_kind, entity_id, payload, tombstone
    ) values (
        p_workspace_id, p_idempotency_key, p_base_sequence, v_user, p_device_id,
        p_entity_kind, p_entity_id, p_payload, p_tombstone
    ) on conflict (workspace_id, idempotency_key) do nothing
    returning * into v_op;

    if v_op is null then
        select * into v_op
        from public.operations
        where workspace_id = p_workspace_id
          and idempotency_key = p_idempotency_key;
        return v_op;
    end if;

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
            null;
        end;
    end if;

    return v_op;
end;
$$;

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
    set search_path = ''
as $$
begin
    if not public.is_active_member(p_workspace_id, auth.uid()) then
        raise exception 'not a workspace member' using errcode = '42501';
    end if;
    if not exists (
        select 1
        from public.workspaces
        where id = p_workspace_id
          and billing_state in ('active', 'grace')
    ) then
        raise exception using errcode = 'P0001', message = 'entitlement_required';
    end if;

    return query
    select
        operation.id,
        operation.workspace_id,
        operation.idempotency_key,
        operation.server_sequence,
        operation.base_sequence,
        operation.device_id,
        operation.entity_kind,
        operation.entity_id,
        operation.payload,
        operation.tombstone,
        operation.created_at
    from public.operations as operation
    where operation.workspace_id = p_workspace_id
      and operation.server_sequence > p_after_sequence
    order by operation.server_sequence asc
    limit p_limit;
end;
$$;

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
    set search_path = ''
as $$
begin
    if not public.is_active_member(p_workspace_id, auth.uid()) then
        raise exception 'not a workspace member' using errcode = '42501';
    end if;
    if not exists (
        select 1
        from public.workspaces
        where id = p_workspace_id
          and billing_state in ('active', 'grace')
    ) then
        raise exception using errcode = 'P0001', message = 'entitlement_required';
    end if;

    return query
    select
        conflict.id,
        conflict.workspace_id,
        conflict.entity_kind,
        conflict.entity_id,
        row_to_json(operation_a.*),
        row_to_json(operation_b.*),
        operation_a.author_id,
        operation_b.author_id,
        conflict.status,
        conflict.resolver_id,
        conflict.resolved_into_id,
        conflict.created_at,
        conflict.resolved_at
    from public.conflicts as conflict
    join public.operations as operation_a on operation_a.id = conflict.operation_a_id
    join public.operations as operation_b on operation_b.id = conflict.operation_b_id
    where conflict.workspace_id = p_workspace_id
      and conflict.status = 'pending'
    order by conflict.created_at asc;
end;
$$;

create or replace function public.resolve_conflict(
    p_conflict_id uuid,
    p_winner_operation_id uuid
) returns public.operations
    language plpgsql
    security definer
    set search_path = ''
as $$
declare
    v_user uuid := auth.uid();
    v_conflict public.conflicts;
    v_winner public.operations;
    v_max_seq bigint;
    v_superseding public.operations;
    v_resolution_key text := 'resolve:' || p_conflict_id::text;
begin
    select * into v_conflict
    from public.conflicts
    where id = p_conflict_id
    for update;

    if v_conflict is null then
        raise exception 'conflict not found' using errcode = 'P0002';
    end if;
    if not public.is_active_member(v_conflict.workspace_id, v_user) then
        raise exception 'not a workspace member' using errcode = '42501';
    end if;
    if not private.workspace_write_allowed(v_conflict.workspace_id) then
        raise exception using errcode = 'P0001', message = 'entitlement_required';
    end if;

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

    select * into v_winner
    from public.operations
    where id = p_winner_operation_id;

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

revoke all on function public.create_workspace(text) from public, anon;
revoke all on function public.create_invite(uuid, text) from public, anon;
revoke all on function public.revoke_invite(uuid) from public, anon;
revoke all on function public.join_workspace(text) from public, anon;
revoke all on function public.leave_workspace(uuid) from public, anon;
revoke all on function public.delete_workspace(uuid) from public, anon;
revoke all on function public.push_operation(uuid, text, bigint, text, public.entity_kind, uuid, jsonb, boolean) from public, anon;
revoke all on function public.pull_operations(uuid, bigint, integer) from public, anon;
revoke all on function public.list_pending_conflicts(uuid) from public, anon;
revoke all on function public.resolve_conflict(uuid, uuid) from public, anon;

grant execute on function public.create_workspace(text) to authenticated;
grant execute on function public.create_invite(uuid, text) to authenticated;
grant execute on function public.revoke_invite(uuid) to authenticated;
grant execute on function public.join_workspace(text) to authenticated;
grant execute on function public.leave_workspace(uuid) to authenticated;
grant execute on function public.delete_workspace(uuid) to authenticated;
grant execute on function public.push_operation(uuid, text, bigint, text, public.entity_kind, uuid, jsonb, boolean) to authenticated;
grant execute on function public.pull_operations(uuid, bigint, integer) to authenticated;
grant execute on function public.list_pending_conflicts(uuid) to authenticated;
grant execute on function public.resolve_conflict(uuid, uuid) to authenticated;
