-- Qualify the workspace id predicate because the return column `id` is also a PL/pgSQL variable.
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
        from public.workspaces as workspace
        where workspace.id = p_workspace_id
          and workspace.billing_state in ('active', 'grace')
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

revoke all on function public.pull_operations(uuid, bigint, integer) from public, anon;
grant execute on function public.pull_operations(uuid, bigint, integer) to authenticated;
