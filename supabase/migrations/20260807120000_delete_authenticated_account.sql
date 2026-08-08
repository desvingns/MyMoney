-- Authenticated account deletion is deliberately self-scoped. A caller with an
-- active workspace must first use the existing leave or sole-owner deletion flow,
-- so deleting a profile can never silently remove a shared workspace.

alter table public.operations
    drop constraint operations_author_id_fkey;

alter table public.operations
    alter column author_id drop not null;

alter table public.operations
    add constraint operations_author_id_fkey
    foreign key (author_id) references auth.users (id) on delete set null;

alter table public.workspace_invites
    drop constraint workspace_invites_created_by_fkey;

alter table public.workspace_invites
    alter column created_by drop not null;

alter table public.workspace_invites
    add constraint workspace_invites_created_by_fkey
    foreign key (created_by) references auth.users (id) on delete set null;

alter table public.conflicts
    drop constraint conflicts_resolver_id_fkey;

alter table public.conflicts
    add constraint conflicts_resolver_id_fkey
    foreign key (resolver_id) references auth.users (id) on delete set null;

create or replace function public.delete_my_account()
    returns void
    language plpgsql
    security definer
    set search_path = ''
as $$
declare
    v_user uuid := auth.uid();
    v_workspace_id uuid;
    v_active_member_count integer;
begin
    if v_user is null then
        raise exception 'authentication required' using errcode = '28000';
    end if;

    if exists (
        select 1
        from public.workspace_members
        where user_id = v_user
          and active
          and role = 'editor'
    ) or exists (
        select 1
        from public.workspace_members
        where user_id = v_user
          and active
          and role <> 'owner'
    ) then
        raise exception 'account deletion requires leaving or deleting the active workspace: leave it first'
            using errcode = 'P0001';
    end if;

    for v_workspace_id in
        select id
        from public.workspaces
        where owner_id = v_user
        for update
    loop
        select count(*) into v_active_member_count
        from public.workspace_members
        where workspace_id = v_workspace_id
          and active;

        if v_active_member_count <> 1 or not exists (
            select 1
            from public.workspace_members
            where workspace_id = v_workspace_id
              and user_id = v_user
              and active
              and role = 'owner'
        ) then
            raise exception 'account deletion requires leaving or deleting the active workspace: delete it only after removing other active members'
                using errcode = 'P0001';
        end if;

        delete from public.workspaces where id = v_workspace_id;
    end loop;

    delete from auth.users where id = v_user;
end;
$$;

revoke all on function public.delete_my_account() from public, anon;
grant execute on function public.delete_my_account() to authenticated;
