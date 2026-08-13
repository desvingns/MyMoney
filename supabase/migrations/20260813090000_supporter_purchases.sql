create table public.supporter_purchases (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users (id) on delete cascade,
    product_id text not null,
    purchase_token text not null unique,
    purchased_at timestamptz not null,
    created_at timestamptz not null default now()
);

create index ix_supporter_purchases_user_purchased_at
    on public.supporter_purchases (user_id, purchased_at);

alter table public.supporter_purchases enable row level security;

create policy supporter_purchases_select_own on public.supporter_purchases
    for select to authenticated
    using ((select auth.uid()) = user_id);

create policy supporter_purchases_insert_own on public.supporter_purchases
    for insert to authenticated
    with check ((select auth.uid()) = user_id);

revoke all on table public.supporter_purchases from public, anon, authenticated;
grant select, insert on table public.supporter_purchases to authenticated;

create or replace function private.grant_supporter_from_purchase()
    returns trigger
    language plpgsql
    security definer
    set search_path = ''
as $$
begin
    insert into public.supporters (
        user_id,
        provider,
        provider_reference
    ) values (
        new.user_id,
        'google_play',
        new.purchase_token
    ) on conflict (user_id) do nothing;

    return new;
end;
$$;

revoke all on function private.grant_supporter_from_purchase() from public, anon, authenticated;

create trigger supporter_purchases_grant_supporter
    after insert on public.supporter_purchases
    for each row
    execute function private.grant_supporter_from_purchase();
