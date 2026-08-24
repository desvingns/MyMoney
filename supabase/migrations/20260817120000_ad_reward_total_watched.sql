-- Adds a lifetime "totalWatched" count to get_ad_reward_state() -- every ad_rewards
-- row ever recorded for the caller, including rows excluded from progress via
-- counts_toward_reward = false (an already-active Plus entitlement at verification
-- time). This is a read-only addition; no existing field's value or meaning changes.

create or replace function public.get_ad_reward_state()
 returns jsonb
 language plpgsql
 stable security definer
 set search_path to ''
as $function$
declare
    v_user uuid := auth.uid();
    v_progress integer;
    v_total_watched integer;
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

    select count(*)::integer into v_total_watched
    from public.ad_rewards as reward
    where reward.user_id = v_user;

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
        'plusExpiresAt', v_plus_expires_at,
        'totalWatched', v_total_watched
    );
end;
$function$;
