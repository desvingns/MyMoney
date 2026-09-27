import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { pathToFileURL } from "node:url";

const modulePath = process.env.PGLITE_MODULE;
const { PGlite } = await import(modulePath ? pathToFileURL(modulePath).href : "@electric-sql/pglite");
const db = new PGlite();
const first = "00000000-0000-4000-8000-000000000001";
const second = "00000000-0000-4000-8000-000000000002";
await db.exec(`
  create role anon; create role authenticated; create role service_role;
  create schema auth; create schema private;
  create table auth.users(id uuid primary key);
  insert into auth.users values ('${first}'), ('${second}');
  create function auth.uid() returns uuid language sql as
    $$ select nullif(current_setting('app.user_id', true), '')::uuid $$;
  grant usage on schema auth to authenticated;
  create table public.supporters (
    user_id uuid primary key references auth.users(id), provider text not null,
    provider_reference text, granted_at timestamptz default now(), revoked_at timestamptz
  );
`);
await db.exec(await readFile(new URL("../migrations/20260813090000_supporter_purchases.sql", import.meta.url), "utf8"));
await db.exec(`insert into public.supporter_purchases(user_id, product_id, purchase_token, purchased_at)
  values ('${first}', 'forged-product', 'legacy-receipt', now());`);
await db.exec(await readFile(new URL("../migrations/20260927130417_verified_supporter_purchases.sql", import.meta.url), "utf8"));
assert.equal((await db.query("select count(*)::int as n from supporters where revoked_at is null")).rows[0].n, 0);
await db.exec(`set app.user_id = '${first}'; set role authenticated;`);
assert.equal((await db.query("select count(*)::int as n from supporter_purchases")).rows[0].n, 0);
await assert.rejects(db.exec(`insert into supporter_purchases(user_id, product_id, purchase_token, purchased_at)
  values ('${first}', 'coffee_small', 'forged-receipt', now());`), /permission denied/);
await assert.rejects(db.query(`select record_verified_supporter_purchase('${first}', 'forged', 'coffee_small', now())`), /permission denied/);
await db.exec("reset role; set role service_role;");
assert.equal((await db.query(`select record_verified_supporter_purchase('${first}', 'legacy-receipt', 'coffee_small', now()) as ok`)).rows[0].ok, true);
assert.equal((await db.query(`select record_verified_supporter_purchase('${first}', 'legacy-receipt', 'coffee_small', now()) as ok`)).rows[0].ok, true);
assert.equal((await db.query(`select record_verified_supporter_purchase('${second}', 'legacy-receipt', 'coffee_small', now()) as ok`)).rows[0].ok, false);
await db.exec("reset role;");
assert.equal((await db.query("select count(*)::int as n from supporters where revoked_at is null")).rows[0].n, 1);
assert.equal((await db.query("select count(*)::int as n from supporter_purchases where verified_at is not null")).rows[0].n, 1);
await db.exec(`set role authenticated; set app.user_id = '${second}';`);
assert.equal((await db.query("select count(*)::int as n from supporter_purchases")).rows[0].n, 0);
await db.exec(`set app.user_id = '${first}';`);
assert.equal((await db.query("select count(*)::int as n from supporter_purchases")).rows[0].n, 1);
await db.close();
console.log("Supporter SQL privileges, legacy quarantine, verified grant, replay and owner isolation: PASS");
