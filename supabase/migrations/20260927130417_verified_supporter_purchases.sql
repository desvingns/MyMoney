alter table public.supporter_purchases add column verified_at timestamptz;

revoke insert on table public.supporter_purchases from authenticated;
drop policy supporter_purchases_insert_own on public.supporter_purchases;
alter policy supporter_purchases_select_own on public.supporter_purchases
    using ((select auth.uid()) = user_id and verified_at is not null);

update public.supporters s set revoked_at = now()
where s.provider = 'google_play' and s.revoked_at is null
  and exists (select 1 from public.supporter_purchases p where p.user_id = s.user_id)
  and not exists (
      select 1 from public.supporter_purchases p
      where p.user_id = s.user_id and p.verified_at is not null
  );

create or replace function private.grant_supporter_from_purchase()
    returns trigger language plpgsql security definer set search_path = ''
as $$
begin
    if new.verified_at is null then return new; end if;
    insert into public.supporters (user_id, provider, provider_reference)
    values (new.user_id, 'google_play', new.purchase_token)
    on conflict (user_id) do update set
        revoked_at = null,
        provider_reference = excluded.provider_reference
    where supporters.provider = 'google_play';
    return new;
end;
$$;

drop trigger supporter_purchases_grant_supporter on public.supporter_purchases;
create trigger supporter_purchases_grant_supporter
    after insert or update of verified_at on public.supporter_purchases
    for each row execute function private.grant_supporter_from_purchase();

create function public.record_verified_supporter_purchase(
    p_user_id uuid, p_purchase_token text, p_product_id text, p_purchased_at timestamptz
) returns boolean language plpgsql security definer set search_path = ''
as $$
declare recorded_id uuid;
begin
    if p_user_id is null or p_purchase_token is null or length(p_purchase_token) not between 1 and 4096
        or p_product_id is null or p_product_id not in ('coffee_small', 'coffee_large')
        or p_purchased_at is null then
        raise exception 'invalid verified supporter purchase' using errcode = '22023';
    end if;
    insert into public.supporter_purchases (user_id, product_id, purchase_token, purchased_at, verified_at)
    values (p_user_id, p_product_id, p_purchase_token, p_purchased_at, now())
    on conflict (purchase_token) do update set
        product_id = excluded.product_id,
        purchased_at = excluded.purchased_at,
        verified_at = coalesce(supporter_purchases.verified_at, excluded.verified_at)
    where supporter_purchases.user_id = excluded.user_id
    returning id into recorded_id;
    return recorded_id is not null;
end;
$$;

revoke all on function public.record_verified_supporter_purchase(uuid, text, text, timestamptz)
    from public, anon, authenticated;
grant execute on function public.record_verified_supporter_purchase(uuid, text, text, timestamptz)
    to service_role;
