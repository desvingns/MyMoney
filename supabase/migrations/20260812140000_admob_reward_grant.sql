create index ix_activation_codes_redeemed_by
    on public.activation_codes (redeemed_by);

create index ix_activation_codes_redeemed_entitlement
    on public.activation_codes (redeemed_entitlement_id);

create index ix_ad_rewards_entitlement
    on public.ad_rewards (entitlement_id);

create or replace function private.grant_admob_plus_from_reward()
    returns trigger
    language plpgsql
    security definer
    set search_path = ''
as $$
declare
    v_entitlement public.entitlements;
    v_unassigned_count integer;
begin
    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(new.user_id::text, 0)
    );

    if exists (
        select 1
        from public.entitlements
        where user_id = new.user_id
          and entitlement = 'plus'
          and provider = 'admob_reward'
          and revoked_at is null
          and (expires_at is null or expires_at > now())
    ) then
        return new;
    end if;

    select count(*) into v_unassigned_count
    from public.ad_rewards
    where user_id = new.user_id
      and entitlement_id is null;

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

revoke all on function private.grant_admob_plus_from_reward() from public, anon, authenticated;

create trigger ad_rewards_grant_plus
    after insert on public.ad_rewards
    for each row
    execute function private.grant_admob_plus_from_reward();
