-- A workspace owner may delete only an otherwise-empty workspace. This keeps the
-- Android sole-owner recovery path from accidentally removing other members when
-- membership changes race its confirmation dialog.

create or replace function public.delete_workspace(p_workspace_id uuid)
    returns void
    language plpgsql
    security definer
    set search_path = public
as $$
declare
    v_user uuid := auth.uid();
    v_active_members integer;
begin
    perform 1
    from public.workspaces
    where id = p_workspace_id
      and owner_id = v_user
    for update;

    if not found then
        raise exception 'only the owner may delete the workspace' using errcode = '42501';
    end if;

    select count(*) into v_active_members
    from public.workspace_members
    where workspace_id = p_workspace_id
      and active;

    if v_active_members <> 1 then
        raise exception 'only a sole owner may delete the workspace' using errcode = 'P0001';
    end if;

    delete from public.workspaces where id = p_workspace_id;
end;
$$;

grant execute on function public.delete_workspace(uuid) to authenticated;
