begin;

select plan(25);

select ok(
    exists (
        select 1
        from pg_catalog.pg_attribute as attribute
        left join pg_catalog.pg_attrdef as default_value
            on default_value.adrelid = attribute.attrelid
           and default_value.adnum = attribute.attnum
        where attribute.attrelid = 'public.ad_rewards'::pg_catalog.regclass
          and attribute.attname = 'counts_toward_reward'
          and attribute.attnotnull
          and pg_catalog.pg_get_expr(default_value.adbin, default_value.adrelid) = 'true'
    )
    and exists (
        select 1
        from pg_catalog.pg_attribute as attribute
        where attribute.attrelid = 'public.ad_rewards'::pg_catalog.regclass
          and attribute.attname = 'exclusion_reason'
          and not attribute.attnotnull
    ),
    'ad_rewards stores the additive reward-counting freeze state'
);

with providers(provider) as (
    values
        ('google_play'::text),
        ('admob_reward'::text),
        ('whitelist'::text),
        ('activation_code'::text)
), contract as (
    select
        coalesce((
            select pg_catalog.string_agg(pg_catalog.pg_get_constraintdef(constraint.oid), E'\n')
            from pg_catalog.pg_constraint as constraint
            where constraint.conrelid = 'public.entitlements'::pg_catalog.regclass
              and constraint.contype = 'c'
        ), '') as provider_checks,
        coalesce((
            select procedure.prosrc
            from pg_catalog.pg_proc as procedure
            where procedure.oid = 'private.grant_admob_plus_from_reward()'::pg_catalog.regprocedure
        ), '') as grant_source
)
select ok(
    pg_catalog.position(pg_catalog.quote_literal(providers.provider) in contract.provider_checks) > 0
    and pg_catalog.position('and entitlement.entitlement = ''plus''' in contract.grant_source) > 0
    and pg_catalog.position('and entitlement.revoked_at is null' in contract.grant_source) > 0
    and pg_catalog.position('and entitlement.starts_at <= new.verified_at' in contract.grant_source) > 0
    and pg_catalog.position(
        'and (entitlement.expires_at is null or entitlement.expires_at > new.verified_at)'
        in contract.grant_source
    ) > 0
    and contract.grant_source !~ 'provider[[:space:]]*=',
    pg_catalog.format('active Plus freezes rewards for %s', providers.provider)
)
from providers
cross join contract;

with providers(provider) as (
    values
        ('google_play'::text),
        ('admob_reward'::text),
        ('whitelist'::text),
        ('activation_code'::text)
), contract as (
    select
        coalesce((
            select pg_catalog.string_agg(pg_catalog.pg_get_constraintdef(constraint.oid), E'\n')
            from pg_catalog.pg_constraint as constraint
            where constraint.conrelid = 'public.entitlements'::pg_catalog.regclass
              and constraint.contype = 'c'
        ), '') as provider_checks,
        coalesce((
            select procedure.prosrc
            from pg_catalog.pg_proc as procedure
            where procedure.oid = 'private.grant_admob_plus_from_reward()'::pg_catalog.regprocedure
        ), '') as grant_source
)
select ok(
    pg_catalog.position(pg_catalog.quote_literal(providers.provider) in contract.provider_checks) > 0
    and contract.grant_source ~
        'set[[:space:]]+counts_toward_reward[[:space:]]*=[[:space:]]*false,[[:space:]]+exclusion_reason[[:space:]]*=[[:space:]]*''plus_active:''[[:space:]]*\|\|[[:space:]]*v_active_provider',
    pg_catalog.format(
        'active Plus writes counts_toward_reward=false and its exclusion reason for %s',
        providers.provider
    )
)
from providers
cross join contract;

with contract as (
    select coalesce((
        select procedure.prosrc
        from pg_catalog.pg_proc as procedure
        where procedure.oid = 'private.grant_admob_plus_from_reward()'::pg_catalog.regprocedure
    ), '') as grant_source
)
select ok(
    pg_catalog.position('if v_active_provider is not null then' in contract.grant_source) > 0
    and pg_catalog.position('return new;' in contract.grant_source) >
        pg_catalog.position('if v_active_provider is not null then' in contract.grant_source)
    and pg_catalog.position('return new;' in contract.grant_source) <
        pg_catalog.position('insert into public.entitlements' in contract.grant_source)
    and contract.grant_source !~ 'update[[:space:]]+public\.entitlements'
    and contract.grant_source ~
        $$'admob_reward',[[:space:]]*'admob_batch:'[[:space:]]*\|\|[[:space:]]*new\.id::text,[[:space:]]*now\(\),[[:space:]]*now\(\)[[:space:]]*\+[[:space:]]*interval[[:space:]]+'24 hours'$$,
    'an active ad-Plus returns before a new 24-hour entitlement can be granted'
)
from contract;

with contract as (
    select coalesce((
        select procedure.prosrc
        from pg_catalog.pg_proc as procedure
        where procedure.oid = 'private.grant_admob_plus_from_reward()'::pg_catalog.regprocedure
    ), '') as grant_source
)
select ok(
    contract.grant_source ~
        'with[[:space:]]+batch[[:space:]]+as[[:space:]]*\([[:space:]]*select[[:space:]]+id[[:space:]]+from[[:space:]]+public\.ad_rewards[[:space:]]+where[[:space:]]+user_id[[:space:]]*=[[:space:]]*new\.user_id[[:space:]]+and[[:space:]]+entitlement_id[[:space:]]+is[[:space:]]+null[[:space:]]+and[[:space:]]+counts_toward_reward[[:space:]]*=[[:space:]]*true[[:space:]]+order[[:space:]]+by[[:space:]]+rewarded_at[[:space:]]+asc,[[:space:]]+created_at[[:space:]]+asc,[[:space:]]+id[[:space:]]+asc[[:space:]]+limit[[:space:]]+5',
    'the five oldest counted unassigned rewards are grouped into one entitlement'
)
from contract;

with contract as (
    select
        coalesce((
            select pg_catalog.pg_get_constraintdef(constraint.oid)
            from pg_catalog.pg_constraint as constraint
            where constraint.conrelid = 'public.ad_rewards'::pg_catalog.regclass
              and constraint.contype = 'u'
              and pg_catalog.pg_get_constraintdef(constraint.oid) = 'UNIQUE (transaction_id)'
        ), '') as transaction_id_constraint,
        coalesce((
            select pg_catalog.pg_get_triggerdef(trigger.oid)
            from pg_catalog.pg_trigger as trigger
            where trigger.tgrelid = 'public.ad_rewards'::pg_catalog.regclass
              and trigger.tgname = 'ad_rewards_grant_plus'
              and not trigger.tgisinternal
        ), '') as trigger_definition
)
select ok(
    contract.transaction_id_constraint = 'UNIQUE (transaction_id)'
    and contract.trigger_definition ~
        'AFTER INSERT ON public\.ad_rewards.*EXECUTE FUNCTION private\.grant_admob_plus_from_reward\(\)',
    'duplicate transaction IDs are rejected before the AFTER INSERT trigger can mutate progress'
)
from contract;

with contract as (
    select coalesce((
        select procedure.prosrc
        from pg_catalog.pg_proc as procedure
        where procedure.oid = 'private.grant_admob_plus_from_reward()'::pg_catalog.regprocedure
    ), '') as grant_source
)
select ok(
    contract.grant_source ~
        'pg_catalog\.pg_advisory_xact_lock\([[:space:]]*pg_catalog\.hashtextextended\(new\.user_id::text,[[:space:]]*0\)',
    'reward grants serialize with a transaction-scoped advisory lock per user'
)
from contract;

select ok(
    'public.get_ad_reward_state()'::pg_catalog.regprocedure is not null,
    'get_ad_reward_state is a zero-argument public read RPC'
);

select ok(
    coalesce((
        select procedure.prosecdef
            and procedure.provolatile = 's'
            and pg_catalog.pg_get_functiondef(procedure.oid) ~ 'SET search_path TO '''''
            and pg_catalog.position('auth.uid()' in procedure.prosrc) > 0
            and pg_catalog.position('if v_user is null then' in procedure.prosrc) > 0
        from pg_catalog.pg_proc as procedure
        where procedure.oid = 'public.get_ad_reward_state()'::pg_catalog.regprocedure
    ), false),
    'get_ad_reward_state requires authentication through a stable security-definer RPC'
);

select ok(
    coalesce((
        select procedure.pronargs = 0
            and procedure.prosrc ~
                'entitlement\.user_id[[:space:]]*=[[:space:]]*v_user'
            and procedure.prosrc ~
                'reward\.user_id[[:space:]]*=[[:space:]]*v_user'
        from pg_catalog.pg_proc as procedure
        where procedure.oid = 'public.get_ad_reward_state()'::pg_catalog.regprocedure
    ), false),
    'get_ad_reward_state is self-scoped and accepts no caller-controlled user identifier'
);

select ok(
    coalesce((
        select procedure.provolatile = 's'
            and procedure.prosrc !~*
                '(insert|update|delete|merge)[[:space:]]+'
        from pg_catalog.pg_proc as procedure
        where procedure.oid = 'public.get_ad_reward_state()'::pg_catalog.regprocedure
    ), false),
    'get_ad_reward_state is read-only'
);

select ok(
    coalesce((
        select pg_catalog.position('return jsonb_build_object(' in procedure.prosrc) > 0
            and pg_catalog.position($$'progress'$$ in procedure.prosrc) > 0
            and pg_catalog.position($$'required'$$ in procedure.prosrc) > 0
            and pg_catalog.position($$'frozen'$$ in procedure.prosrc) > 0
            and pg_catalog.position($$'frozenReason'$$ in procedure.prosrc) > 0
            and pg_catalog.position($$'plusActive'$$ in procedure.prosrc) > 0
            and pg_catalog.position($$'plusProvider'$$ in procedure.prosrc) > 0
            and pg_catalog.position($$'plusExpiresAt'$$ in procedure.prosrc) > 0
            and pg_catalog.position($$'frozen', v_plus_provider is not null$$ in procedure.prosrc) > 0
            and pg_catalog.position($$'plusActive', v_plus_provider is not null$$ in procedure.prosrc) > 0
            and procedure.prosrc ~
                $$'frozenReason',[[:space:]]*case[[:space:]]+when[[:space:]]+v_plus_provider[[:space:]]+is[[:space:]]+null[[:space:]]+then[[:space:]]+null[[:space:]]+else[[:space:]]+'plus_active:'[[:space:]]*\|\|[[:space:]]*v_plus_provider[[:space:]]+end$$
            and procedure.prosrc ~
                'entitlement\.starts_at[[:space:]]*<=[[:space:]]*now\(\)'
            and procedure.prosrc ~
                'entitlement\.expires_at[[:space:]]+is[[:space:]]+null[[:space:]]+or[[:space:]]+entitlement\.expires_at[[:space:]]*>[[:space:]]*now\(\)'
            and procedure.prosrc ~
                'reward\.entitlement_id[[:space:]]+is[[:space:]]+null'
            and procedure.prosrc ~
                'reward\.counts_toward_reward[[:space:]]*=[[:space:]]*true'
        from pg_catalog.pg_proc as procedure
        where procedure.oid = 'public.get_ad_reward_state()'::pg_catalog.regprocedure
    ), false),
    'get_ad_reward_state returns the complete self-scoped state and counts only eligible rewards'
);

select ok(
    not exists (
        select 1
        from pg_catalog.pg_class as relation
        cross join lateral pg_catalog.aclexplode(
            coalesce(relation.relacl, pg_catalog.acldefault('r', relation.relowner))
        ) as privilege
        where relation.oid = 'public.ad_rewards'::pg_catalog.regclass
          and privilege.grantee = 0
          and privilege.privilege_type = 'SELECT'
    ),
    'public has no raw SELECT privilege on ad_rewards'
);

select ok(
    not pg_catalog.has_table_privilege(
        'anon',
        'public.ad_rewards'::pg_catalog.regclass,
        'SELECT'
    ),
    'anon has no raw SELECT privilege on ad_rewards'
);

select ok(
    not pg_catalog.has_table_privilege(
        'authenticated',
        'public.ad_rewards'::pg_catalog.regclass,
        'SELECT'
    ),
    'authenticated has no raw SELECT privilege on ad_rewards'
);

select ok(
    not exists (
        select 1
        from pg_catalog.pg_proc as procedure
        cross join lateral pg_catalog.aclexplode(
            coalesce(procedure.proacl, pg_catalog.acldefault('f', procedure.proowner))
        ) as privilege
        where procedure.oid = 'public.get_ad_reward_state()'::pg_catalog.regprocedure
          and privilege.grantee = 0
          and privilege.privilege_type = 'EXECUTE'
    ),
    'public has no EXECUTE privilege on get_ad_reward_state'
);

select ok(
    not pg_catalog.has_function_privilege(
        'anon',
        'public.get_ad_reward_state()'::pg_catalog.regprocedure,
        'EXECUTE'
    ),
    'anon has no EXECUTE privilege on get_ad_reward_state'
);

select ok(
    pg_catalog.has_function_privilege(
        'authenticated',
        'public.get_ad_reward_state()'::pg_catalog.regprocedure,
        'EXECUTE'
    ),
    'authenticated has EXECUTE privilege on get_ad_reward_state'
);

with execute_grantees as (
    select role.rolname
    from pg_catalog.pg_proc as procedure
    cross join lateral pg_catalog.aclexplode(
        coalesce(procedure.proacl, pg_catalog.acldefault('f', procedure.proowner))
    ) as privilege
    join pg_catalog.pg_roles as role
        on role.oid = privilege.grantee
    where procedure.oid = 'public.get_ad_reward_state()'::pg_catalog.regprocedure
      and privilege.privilege_type = 'EXECUTE'
      and privilege.grantee <> procedure.proowner
)
select ok(
    coalesce(
        (select pg_catalog.array_agg(execute_grantees.rolname order by execute_grantees.rolname)
         from execute_grantees),
        array[]::name[]
    ) = array['authenticated']::name[],
    'authenticated is the only explicit EXECUTE grantee for get_ad_reward_state'
);

select * from finish();

rollback;
