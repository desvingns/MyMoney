create extension if not exists pgcrypto;

create table public.entitlements (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users (id) on delete cascade,
    entitlement text not null default 'plus' check (entitlement = 'plus'),
    provider text not null check (provider in ('google_play', 'admob_reward', 'whitelist', 'activation_code')),
    provider_reference text,
    starts_at timestamptz not null default now(),
    expires_at timestamptz,
    revoked_at timestamptz,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    check (expires_at is null or expires_at > starts_at),
    check (revoked_at is null or revoked_at >= starts_at)
);

create index ix_entitlements_user_period
    on public.entitlements (user_id, starts_at, expires_at)
    where revoked_at is null;

create unique index ux_entitlements_provider_reference
    on public.entitlements (provider, provider_reference)
    where provider_reference is not null;

create unique index ux_entitlements_active_whitelist
    on public.entitlements (user_id)
    where entitlement = 'plus'
      and provider = 'whitelist'
      and revoked_at is null;

create table public.supporters (
    user_id uuid primary key references auth.users (id) on delete cascade,
    provider text not null check (provider in ('google_play', 'activation_code', 'manual')),
    provider_reference text,
    granted_at timestamptz not null default now(),
    revoked_at timestamptz,
    metadata jsonb not null default '{}'::jsonb,
    check (revoked_at is null or revoked_at >= granted_at)
);

create unique index ux_supporters_provider_reference
    on public.supporters (provider, provider_reference)
    where provider_reference is not null;

create table public.ad_rewards (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users (id) on delete cascade,
    transaction_id text not null,
    reward_item text not null,
    reward_amount integer not null default 1 check (reward_amount > 0),
    custom_data text not null,
    rewarded_at timestamptz not null,
    verified_at timestamptz not null default now(),
    payload_hash text not null,
    entitlement_id uuid references public.entitlements (id) on delete set null,
    created_at timestamptz not null default now(),
    unique (transaction_id)
);

create index ix_ad_rewards_user_time
    on public.ad_rewards (user_id, rewarded_at desc);

create table public.provider_events (
    id uuid primary key default gen_random_uuid(),
    provider text not null check (provider in ('google_play_rtdn', 'admob_ssv')),
    event_key text not null,
    event_type text,
    payload jsonb not null default '{}'::jsonb,
    status text not null default 'received' check (status in ('received', 'processed', 'failed')),
    error_message text,
    received_at timestamptz not null default now(),
    processed_at timestamptz,
    unique (provider, event_key)
);

create index ix_provider_events_status
    on public.provider_events (provider, status, received_at);

create table public.activation_codes (
    id uuid primary key default gen_random_uuid(),
    code_hash text not null unique,
    code_hint text not null check (char_length(code_hint) between 1 and 16),
    entitlement text not null default 'plus' check (entitlement = 'plus'),
    duration_seconds integer check (duration_seconds is null or duration_seconds > 0),
    code_expires_at timestamptz,
    redeemed_at timestamptz,
    redeemed_by uuid references auth.users (id) on delete set null,
    redeemed_entitlement_id uuid references public.entitlements (id) on delete set null,
    created_at timestamptz not null default now(),
    check (redeemed_at is null or redeemed_by is not null),
    check (redeemed_at is null or redeemed_entitlement_id is not null)
);

create index ix_activation_codes_redeemed
    on public.activation_codes (redeemed_at, code_expires_at);

alter table public.entitlements enable row level security;
alter table public.supporters enable row level security;
alter table public.ad_rewards enable row level security;
alter table public.provider_events enable row level security;
alter table public.activation_codes enable row level security;

create policy entitlements_select_own on public.entitlements
    for select to authenticated
    using ((select auth.uid()) = user_id);

create policy supporters_select_own on public.supporters
    for select to authenticated
    using ((select auth.uid()) = user_id);

create policy ad_rewards_select_own on public.ad_rewards
    for select to authenticated
    using ((select auth.uid()) = user_id);

revoke all on table public.entitlements from public, anon, authenticated;
revoke all on table public.supporters from public, anon, authenticated;
revoke all on table public.ad_rewards from public, anon, authenticated;
revoke all on table public.provider_events from public, anon, authenticated;
revoke all on table public.activation_codes from public, anon, authenticated;

grant select on table public.entitlements to authenticated;
grant select on table public.supporters to authenticated;
grant select on table public.ad_rewards to authenticated;

create or replace function public.redeem_activation_code(p_code text)
    returns public.entitlements
    language plpgsql
    security definer
    set search_path = ''
as $$
declare
    v_user uuid := auth.uid();
    v_normalized text := upper(regexp_replace(coalesce(trim(p_code), ''), '[^a-zA-Z0-9]', '', 'g'));
    v_hash text;
    v_code public.activation_codes;
    v_entitlement public.entitlements;
    v_expires_at timestamptz;
begin
    if v_user is null then
        raise exception 'authentication required' using errcode = '28000';
    end if;

    if v_normalized = '' then
        raise exception 'activation code is empty' using errcode = '22023';
    end if;

    v_hash := encode(extensions.digest(v_normalized, 'sha256'::text), 'hex');

    select * into v_code
    from public.activation_codes
    where code_hash = v_hash
    for update;

    if v_code is null then
        raise exception 'invalid activation code' using errcode = 'P0002';
    end if;
    if v_code.redeemed_at is not null then
        raise exception 'activation code already redeemed' using errcode = 'P0001';
    end if;
    if v_code.code_expires_at is not null and v_code.code_expires_at <= now() then
        raise exception 'activation code expired' using errcode = 'P0001';
    end if;

    if v_code.duration_seconds is not null then
        v_expires_at := now() + make_interval(secs => v_code.duration_seconds);
    end if;

    insert into public.entitlements (
        user_id,
        entitlement,
        provider,
        provider_reference,
        starts_at,
        expires_at,
        metadata
    ) values (
        v_user,
        v_code.entitlement,
        'activation_code',
        v_code.id::text,
        now(),
        v_expires_at,
        jsonb_build_object('activation_code_id', v_code.id, 'code_hint', v_code.code_hint)
    ) returning * into v_entitlement;

    update public.activation_codes
    set redeemed_at = now(),
        redeemed_by = v_user,
        redeemed_entitlement_id = v_entitlement.id
    where id = v_code.id;

    return v_entitlement;
end;
$$;

revoke all on function public.redeem_activation_code(text) from public, anon;
grant execute on function public.redeem_activation_code(text) to authenticated;
