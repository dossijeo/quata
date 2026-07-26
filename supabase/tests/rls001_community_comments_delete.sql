\set ON_ERROR_STOP on
\set QUIET on

-- Run only against an isolated Supabase database. The transaction applies the
-- migration, creates three ephemeral auth/profile identities and rolls back
-- every schema/data change after assertions and explicit cleanup checks.
begin;
\ir ../migrations/20260726171001_community_comments_delete_rls.sql

insert into auth.users(id)
values
    ('00000000-0000-4000-8000-000000171001'),
    ('00000000-0000-4000-8000-000000171002'),
    ('00000000-0000-4000-8000-000000171003');

insert into public.community_profiles(
    id, auth_user_id, display_name, phone, pass_hash, phone_normalized, phone_local, is_admin
)
values
    ('10000000-0000-4000-8000-000000171001', '00000000-0000-4000-8000-000000171001', 'RLS owner', '+240555171001', 'e2e-not-a-login', '+240555171001', '555171001', false),
    ('10000000-0000-4000-8000-000000171002', '00000000-0000-4000-8000-000000171002', 'RLS outsider', '+240555171002', 'e2e-not-a-login', '+240555171002', '555171002', false),
    ('10000000-0000-4000-8000-000000171003', '00000000-0000-4000-8000-000000171003', 'RLS admin', '+240555171003', 'e2e-not-a-login', '+240555171003', '555171003', true);

insert into public.community_walls(id, slug, name)
values ('20000000-0000-4000-8000-000000171001', 'rls-001-ephemeral', 'RLS-001 ephemeral');

insert into public.community_posts(id, wall_id, profile_id)
values (
    '30000000-0000-4000-8000-000000171001',
    '20000000-0000-4000-8000-000000171001',
    '10000000-0000-4000-8000-000000171001'
);

insert into public.community_comments(id, post_id, profile_id, body)
values
    ('40000000-0000-4000-8000-000000171001', '30000000-0000-4000-8000-000000171001', '10000000-0000-4000-8000-000000171001', 'owner-delete'),
    ('40000000-0000-4000-8000-000000171002', '30000000-0000-4000-8000-000000171001', '10000000-0000-4000-8000-000000171001', 'admin-delete');

-- Anonymous and authenticated reads remain available.
set local role anon;
select set_config('request.jwt.claim.sub', '', true) as ignored
\gset
select count(*) = 2 as anon_read_preserved
from public.community_comments
where post_id = '30000000-0000-4000-8000-000000171001'
\gset
\if :anon_read_preserved
\else
    \echo 'RLS-001 regression: anonymous SELECT no longer sees public comments'
    \quit 1
\endif
reset role;

select not has_table_privilege('anon', 'public.community_comments', 'DELETE') as anon_delete_revoked
\gset
\if :anon_delete_revoked
\else
    \echo 'RLS-001 regression: anonymous role retained DELETE grant'
    \quit 1
\endif

-- An authenticated outsider matches the id filter but RLS returns zero rows.
set local role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000171002', true) as ignored
\gset
select count(*) = 2 as authenticated_read_preserved
from public.community_comments
where post_id = '30000000-0000-4000-8000-000000171001'
\gset
\if :authenticated_read_preserved
\else
    \echo 'RLS-001 regression: authenticated SELECT no longer sees public comments'
    \quit 1
\endif
with deleted as (
    delete from public.community_comments
    where id = '40000000-0000-4000-8000-000000171001'
    returning id
)
select count(*) = 0 as outsider_deleted_zero from deleted
\gset
\if :outsider_deleted_zero
\else
    \echo 'RLS-001 regression: outsider deleted the owner comment'
    \quit 1
\endif
reset role;

select count(*) = 1 as outsider_target_persisted
from public.community_comments
where id = '40000000-0000-4000-8000-000000171001'
\gset
\if :outsider_target_persisted
\else
    \echo 'RLS-001 regression: owner comment did not persist after outsider DELETE'
    \quit 1
\endif

-- The canonical owner can delete its own row with the existing id-only client contract.
set local role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000171001', true) as ignored
\gset
with deleted as (
    delete from public.community_comments
    where id = '40000000-0000-4000-8000-000000171001'
    returning id
)
select count(*) = 1 as owner_deleted_one from deleted
\gset
\if :owner_deleted_one
\else
    \echo 'RLS-001 regression: canonical owner could not delete own comment'
    \quit 1
\endif
reset role;

-- Admin deletion is allowed only through a real active canonical profile with is_admin=true.
set local role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000171003', true) as ignored
\gset
with deleted as (
    delete from public.community_comments
    where id = '40000000-0000-4000-8000-000000171002'
    returning id
)
select count(*) = 1 as admin_deleted_one from deleted
\gset
\if :admin_deleted_one
\else
    \echo 'RLS-001 regression: proven active admin could not delete comment'
    \quit 1
\endif
reset role;

-- The emergency rollback is executable and restores the exact observed
-- pre-migration DELETE policy/grants. Roll back this rehearsal immediately.
savepoint before_rollback_rehearsal;
\ir ../rollbacks/20260726171001_community_comments_delete_rls.rollback.sql
select (
    has_table_privilege('anon', 'public.community_comments', 'DELETE')
    and exists (
        select 1
        from pg_policies
        where schemaname = 'public'
          and tablename = 'community_comments'
          and policyname = 'public delete comments'
          and cmd = 'DELETE'
          and roles = array['public'::name]
          and qual = 'true'
    )
) as rollback_restored_observed_contract
\gset
\if :rollback_restored_observed_contract
\else
    \echo 'RLS-001 rollback rehearsal did not restore the observed contract'
    \quit 1
\endif
rollback to savepoint before_rollback_rehearsal;

-- Explicit cleanup is verified before the encompassing transaction rollback.
delete from public.community_posts where id = '30000000-0000-4000-8000-000000171001';
delete from public.community_walls where id = '20000000-0000-4000-8000-000000171001';
delete from public.community_profiles where id in (
    '10000000-0000-4000-8000-000000171001',
    '10000000-0000-4000-8000-000000171002',
    '10000000-0000-4000-8000-000000171003'
);
delete from auth.users where id in (
    '00000000-0000-4000-8000-000000171001',
    '00000000-0000-4000-8000-000000171002',
    '00000000-0000-4000-8000-000000171003'
);

select (
    not exists (select 1 from public.community_comments where id::text like '40000000-0000-4000-8000-00000017100%')
    and not exists (select 1 from public.community_posts where id = '30000000-0000-4000-8000-000000171001')
    and not exists (select 1 from public.community_walls where id = '20000000-0000-4000-8000-000000171001')
    and not exists (select 1 from public.community_profiles where id::text like '10000000-0000-4000-8000-00000017100%')
    and not exists (select 1 from auth.users where id::text like '00000000-0000-4000-8000-00000017100%')
) as cleanup_verified
\gset
\if :cleanup_verified
\else
    \echo 'RLS-001 cleanup verification failed'
    \quit 1
\endif

rollback;
\echo 'RLS-001 SQL/E2E passed: outsider=0+persisted, owner=1, admin=1, anon/auth-read=preserved, rollback=verified, cleanup=verified'
