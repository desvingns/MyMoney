-- Keep the SECURITY DEFINER authorization helper outside the exposed public
-- schema and bind its user argument to the current authenticated principal.

create schema if not exists private;

revoke all on schema private from public, anon, authenticated;
grant usage on schema private to authenticated;

alter function public.can_receive_workspace_operation_notifications(text, uuid)
    set schema private;

create or replace function private.can_receive_workspace_operation_notifications(
    p_topic text,
    p_user uuid
) returns boolean
    language sql
    stable
    security definer
    set search_path = public
as $$
    select exists (
        select 1
        from public.workspace_members
        where user_id = p_user
          and p_user = (select auth.uid())
          and active
          and p_topic = 'workspace:' || workspace_id::text || ':operations'
    );
$$;

revoke all on function private.can_receive_workspace_operation_notifications(text, uuid)
    from public, anon, authenticated;
grant execute on function private.can_receive_workspace_operation_notifications(text, uuid)
    to authenticated;

drop policy if exists realtime_workspace_members_receive_operation_notifications
    on realtime.messages;

create policy realtime_workspace_members_receive_operation_notifications
    on realtime.messages
    for select
    to authenticated
    using (
        realtime.messages.extension = 'broadcast'
        and realtime.messages.private
        and private.can_receive_workspace_operation_notifications(
            (select realtime.topic()),
            (select auth.uid())
        )
    );
