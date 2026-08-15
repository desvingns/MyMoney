begin;

select plan(4);

select ok(
    exists (
        select 1
        from information_schema.column_privileges
        where table_schema = 'public'
          and table_name = 'workspaces'
          and grantee = 'authenticated'
          and column_name = 'billing_state'
          and privilege_type = 'SELECT'
    ),
    'authenticated can select the shared workspace billing state'
);

select ok(
    exists (
        select 1
        from information_schema.column_privileges
        where table_schema = 'public'
          and table_name = 'workspaces'
          and grantee = 'authenticated'
          and column_name = 'billing_state_until'
          and privilege_type = 'SELECT'
    ),
    'authenticated can select the shared workspace billing deadline'
);

select ok(
    exists (
        select 1
        from information_schema.column_privileges
        where table_schema = 'public'
          and table_name = 'workspaces'
          and grantee = 'authenticated'
          and column_name = 'owner_id'
          and privilege_type = 'SELECT'
    )
    and exists (
        select 1
        from information_schema.column_privileges
        where table_schema = 'public'
          and table_name = 'workspaces'
          and grantee = 'authenticated'
          and column_name = 'created_at'
          and privilege_type = 'SELECT'
    ),
    'billing-aware workspace reads retain the existing identity columns'
);

select ok(
    exists (
        select 1
        from pg_proc as procedure
        where procedure.oid = 'public.get_my_entitlement()'::regprocedure
          and procedure.prosecdef
    ),
    'entitlement state remains exposed through the authenticated RPC'
);

select * from finish();
rollback;
