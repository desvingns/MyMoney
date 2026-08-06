-- Shared Realtime uses a private Broadcast topic for each workspace. The join is
-- authorized by realtime.messages RLS before the channel is admitted; Broadcast
-- contains no operation data and only prompts the durable cursor pull.

alter publication supabase_realtime drop table public.operations;

create or replace function public.can_receive_workspace_operation_notifications(
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
          and active
          and p_topic = 'workspace:' || workspace_id::text || ':operations'
    );
$$;

revoke all on function public.can_receive_workspace_operation_notifications(text, uuid)
    from public, anon, authenticated;
grant execute on function public.can_receive_workspace_operation_notifications(text, uuid) to authenticated;

create policy realtime_workspace_members_receive_operation_notifications
    on realtime.messages
    for select
    to authenticated
    using (
        realtime.messages.extension = 'broadcast'
        and realtime.messages.private
        and public.can_receive_workspace_operation_notifications(
            (select realtime.topic()),
            (select auth.uid())
        )
    );

create or replace function public.notify_workspace_operation_available()
    returns trigger
    language plpgsql
    security definer
    set search_path = ''
as $$
begin
    perform realtime.send(
        '{}'::jsonb,
        'operation_available',
        'workspace:' || new.workspace_id::text || ':operations',
        true
    );
    return null;
end;
$$;

revoke all on function public.notify_workspace_operation_available()
    from public, anon, authenticated;

create trigger operations_notify_workspace_operation_available
    after insert on public.operations
    for each row
    execute function public.notify_workspace_operation_available();
