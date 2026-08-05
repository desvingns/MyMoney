-- Supabase projects may grant EXECUTE explicitly to anon/authenticated when a
-- function is created. Revoking PUBLIC alone does not remove those grants.

revoke all on function public.can_receive_workspace_operation_notifications(text, uuid)
    from public, anon, authenticated;
grant execute on function public.can_receive_workspace_operation_notifications(text, uuid)
    to authenticated;

revoke all on function public.notify_workspace_operation_available()
    from public, anon, authenticated;
