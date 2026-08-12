drop function public.create_invite(uuid, text);

create function public.create_invite(p_workspace_id uuid, p_token_hash text)
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

revoke all on function public.create_invite(uuid, text) from public, anon;
grant execute on function public.create_invite(uuid, text) to authenticated;
