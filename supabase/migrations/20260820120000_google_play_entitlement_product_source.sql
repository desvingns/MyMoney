create or replace function public.get_my_entitlement()
    returns json
    language plpgsql
    security definer
    set search_path = ''
as $$
declare
    v_user uuid := auth.uid();
    v_entitlement record;
    v_google_play_product_id text;
begin
    if v_user is null then
        raise exception 'authentication required' using errcode = '28000';
    end if;

    select * into v_entitlement
    from private.effective_entitlement(v_user);

    if not found then
        return json_build_object(
            'source', null,
            'starts_at', null,
            'expires_at', null,
            'in_trial', false
        );
    end if;

    if v_entitlement.source = 'google_play' then
        select nullif(btrim(entitlement.metadata -> 'product_ids' ->> 0), '')
        into v_google_play_product_id
        from public.entitlements as entitlement
        where entitlement.user_id = v_user
          and entitlement.provider = 'google_play'
          and entitlement.entitlement = 'plus'
          and entitlement.revoked_at is null
          and entitlement.starts_at = v_entitlement.starts_at
          and entitlement.expires_at is not distinct from v_entitlement.expires_at
        order by entitlement.id desc
        limit 1;
    end if;

    return json_build_object(
        'source', case
            when v_entitlement.source = 'google_play'
                then coalesce(v_google_play_product_id, v_entitlement.source)
            else v_entitlement.source
        end,
        'starts_at', v_entitlement.starts_at,
        'expires_at', v_entitlement.expires_at,
        'in_trial', v_entitlement.in_trial
    );
end;
$$;

revoke all on function public.get_my_entitlement() from public, anon;
grant execute on function public.get_my_entitlement() to authenticated;
