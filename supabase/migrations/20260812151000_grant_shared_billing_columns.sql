-- Keep the shared workspace read surface aligned with the billing-aware client query.
revoke select on table public.workspaces from authenticated;

grant select (
    id,
    name,
    owner_id,
    created_at,
    billing_state,
    billing_state_until
) on table public.workspaces to authenticated;
