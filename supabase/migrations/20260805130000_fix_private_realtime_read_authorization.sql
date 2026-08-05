-- Realtime evaluates SELECT policies with a transient messages row that defaults
-- private to false. The Realtime server calls this policy only after a private
-- channel join, so membership and broadcast constraints remain the authorization boundary.

drop policy if exists realtime_workspace_members_receive_operation_notifications
    on realtime.messages;

create policy realtime_workspace_members_receive_operation_notifications
    on realtime.messages
    for select
    to authenticated
    using (
        realtime.messages.extension = 'broadcast'
        and private.can_receive_workspace_operation_notifications(
            (select realtime.topic()),
            (select auth.uid())
        )
    );
