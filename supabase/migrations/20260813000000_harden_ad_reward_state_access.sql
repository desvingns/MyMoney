revoke all on table public.ad_rewards from public, anon, authenticated;

revoke all on function public.get_ad_reward_state() from public, anon, authenticated;
grant execute on function public.get_ad_reward_state() to authenticated;
