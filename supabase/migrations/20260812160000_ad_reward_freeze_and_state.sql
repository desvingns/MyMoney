alter table public.ad_rewards
    add column if not exists counts_toward_reward boolean not null default true,
    add column if not exists exclusion_reason text;

create or replace function private.grant_admob_plus_from_reward()
    returns trigger
    language plpgsql
    security definer
    set search_path = ''
as $$
declare
    v_entitlement public.entitlements;
    v_unassigned_count integer;
    v_active_provider text;
begin
    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(new.user_id::text, 0)
    );

    select entitlement.provider into v_active_provider
    from public.entitlements as entitlement
    where entitlement.user_id = new.user_id
      and entitlement.entitlement = 'plus'
      and entitlement.revoked_at is null
      and entitlement.starts_at <= new.verified_at
      and (entitlement.expires_at is null or entitlement.expires_at > new.verified_at)
    order by entitlement.starts_at desc, entitlement.id desc
    limit 1;

    if v_active_provider is not null then
        update public.ad_rewards
        set counts_toward_reward = false,
            exclusion_reason = 'plus_active:' || v_active_provider
        where id = new.id;

        return new;
    end if;

    select count(*) into v_unassigned_count
    from public.ad_rewards
    where user_id = new.user_id
      and entitlement_id is null
      and counts_toward_reward = true;

    if v_unassigned_count < 5 then
        return new;
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
        new.user_id,
        'plus',
        'admob_reward',
        'admob_batch:' || new.id::text,
        now(),
        now() + interval '24 hours',
        jsonb_build_object('reward_count', 5, 'trigger_reward_id', new.id)
    ) returning * into v_entitlement;

    with batch as (
        select id
        from public.ad_rewards
        where user_id = new.user_id
          and entitlement_id is null
          and counts_toward_reward = true
        order by rewarded_at asc, created_at asc, id asc
        limit 5
    )
    update public.ad_rewards rewards
    set entitlement_id = v_entitlement.id
    from batch
    where rewards.id = batch.id;

    return new;
end;
$$;

create or replace function public.get_ad_reward_state()
    returns jsonb
    language plpgsql
    stable
    security definer
    set search_path = ''
as $$
declare
    v_user uuid := auth.uid();
    v_progress integer;
    v_plus_provider text;
    v_plus_expires_at timestamptz;
begin
    if v_user is null then
        raise exception 'authentication required' using errcode = '28000';
    end if;

    select entitlement.provider, entitlement.expires_at
    into v_plus_provider, v_plus_expires_at
    from public.entitlements as entitlement
    where entitlement.user_id = v_user
      and entitlement.entitlement = 'plus'
      and entitlement.revoked_at is null
      and entitlement.starts_at <= now()
      and (entitlement.expires_at is null or entitlement.expires_at > now())
    order by entitlement.starts_at desc, entitlement.id desc
    limit 1;

    select count(*)::integer into v_progress
    from public.ad_rewards as reward
    where reward.user_id = v_user
      and reward.entitlement_id is null
      and reward.counts_toward_reward = true;

    return jsonb_build_object(
        'progress', v_progress,
        'required', 5,
        'frozen', v_plus_provider is not null,
        'frozenReason', case
            when v_plus_provider is null then null
            else 'plus_active:' || v_plus_provider
        end,
        'plusActive', v_plus_provider is not null,
        'plusProvider', v_plus_provider,
        'plusExpiresAt', v_plus_expires_at
    );
end;
$$;

revoke all on function public.get_ad_reward_state() from public, anon, authenticated;
grant execute on function public.get_ad_reward_state() to authenticated;
