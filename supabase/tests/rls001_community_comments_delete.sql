\set ON_ERROR_STOP on
\set QUIET on

-- Run only against an isolated Supabase database. The migration commits its
-- DDL; fixture data is then explicitly cleaned and verified before commit.
\ir ../migrations/20260726171001_community_comments_delete_rls.sql
begin;

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

-- Anonymous SELECT remains public, while anonymous INSERT/UPDATE/DELETE are
-- denied at the table privilege boundary (PostgREST reports 42501).
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
do $$
begin
    begin
        insert into public.community_comments(post_id, profile_id, body)
        values (
            '30000000-0000-4000-8000-000000171001',
            '10000000-0000-4000-8000-000000171002',
            'anonymous-spoof'
        );
        raise exception 'anonymous INSERT unexpectedly succeeded';
    exception when insufficient_privilege then null;
    end;
    begin
        update public.community_comments set profile_id = '10000000-0000-4000-8000-000000171002'
        where id = '40000000-0000-4000-8000-000000171001';
        raise exception 'anonymous UPDATE unexpectedly succeeded';
    exception when insufficient_privilege then null;
    end;
    begin
        delete from public.community_comments
        where id = '40000000-0000-4000-8000-000000171001';
        raise exception 'anonymous DELETE unexpectedly succeeded';
    exception when insufficient_privilege then null;
    end;
end
$$;
reset role;

select (
    not has_table_privilege('anon', 'public.community_comments', 'INSERT')
    and not has_table_privilege('anon', 'public.community_comments', 'UPDATE')
    and not has_table_privilege('anon', 'public.community_comments', 'DELETE')
) as anon_mutations_revoked
\gset
\if :anon_mutations_revoked
\else
    \echo 'RLS-001 regression: anonymous role retained a mutation grant'
    \quit 1
\endif

-- An authenticated outsider cannot spoof INSERT ownership and cannot UPDATE
-- ownership/post before chaining the formerly permissive DELETE.
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
do $$
begin
    begin
        insert into public.community_comments(post_id, profile_id, body)
        values (
            '30000000-0000-4000-8000-000000171001',
            '10000000-0000-4000-8000-000000171001',
            'authenticated-owner-spoof'
        );
        raise exception 'authenticated ownership spoof unexpectedly succeeded';
    exception when insufficient_privilege then null;
    end;
    begin
        update public.community_comments
        set profile_id = '10000000-0000-4000-8000-000000171002',
            post_id = '30000000-0000-4000-8000-000000171001'
        where id = '40000000-0000-4000-8000-000000171001';
        raise exception 'outsider UPDATE unexpectedly succeeded';
    exception when insufficient_privilege then null;
    end;
end
$$;
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

select (
    select profile_id = '10000000-0000-4000-8000-000000171001'
       and post_id = '30000000-0000-4000-8000-000000171001'
    from public.community_comments
    where id = '40000000-0000-4000-8000-000000171001'
) is true as outsider_target_persisted_immutable
\gset
\if :outsider_target_persisted_immutable
\else
    \echo 'RLS-001 regression: outsider changed/deleted owner comment'
    \quit 1
\endif

-- Existing Android contract: authenticated owner supplies its own profile_id
-- on INSERT and later deletes using only the comment id.
set local role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000171001', true) as ignored
\gset
insert into public.community_comments(id, post_id, profile_id, body)
values (
    '40000000-0000-4000-8000-000000171003',
    '30000000-0000-4000-8000-000000171001',
    '10000000-0000-4000-8000-000000171001',
    'android-id-only-delete'
);
with deleted as (
    delete from public.community_comments
    where id = '40000000-0000-4000-8000-000000171003'
    returning id
)
select count(*) = 1 as android_owner_deleted_one from deleted
\gset
\if :android_owner_deleted_one
\else
    \echo 'RLS-001 regression: Android owner id-only DELETE failed'
    \quit 1
\endif
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

-- is_admin alone is insufficient when the canonical profile is inactive.
update public.community_profiles
set account_status = 'deactivated'
where id = '10000000-0000-4000-8000-000000171003';
set local role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000171003', true) as ignored
\gset
with deleted as (
    delete from public.community_comments
    where id = '40000000-0000-4000-8000-000000171002'
    returning id
)
select count(*) = 0 as inactive_admin_deleted_zero from deleted
\gset
\if :inactive_admin_deleted_zero
\else
    \echo 'RLS-001 regression: inactive admin deleted a comment'
    \quit 1
\endif
reset role;
select count(*) = 1 as inactive_admin_target_persisted
from public.community_comments
where id = '40000000-0000-4000-8000-000000171002'
\gset
\if :inactive_admin_target_persisted
\else
    \echo 'RLS-001 regression: comment vanished after inactive-admin DELETE'
    \quit 1
\endif

-- A proven active admin can moderate the remaining comment.
update public.community_profiles
set account_status = 'active'
where id = '10000000-0000-4000-8000-000000171003';
set local role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000171003', true) as ignored
\gset
with deleted as (
    delete from public.community_comments
    where id = '40000000-0000-4000-8000-000000171002'
    returning id
)
select count(*) = 1 as active_admin_deleted_one from deleted
\gset
\if :active_admin_deleted_one
\else
    \echo 'RLS-001 regression: proven active admin could not delete comment'
    \quit 1
\endif
reset role;

-- Explicit fixture cleanup is committed, then absence is checked outside the
-- transaction so a false rollback-only cleanup cannot pass.
delete from public.community_posts where id = '30000000-0000-4000-8000-000000171001';
delete from public.community_walls where id = '20000000-0000-4000-8000-000000171001';
delete from public.community_profiles where id::text like '10000000-0000-4000-8000-00000017100%';
delete from auth.users where id::text like '00000000-0000-4000-8000-00000017100%';
commit;

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

-- Rehearse rollback in the isolated database, assert the exact observed
-- vulnerable contract, then immediately reapply the secured migration.
\ir ../rollbacks/20260726171001_community_comments_delete_rls.rollback.sql
select (
    has_table_privilege('anon', 'public.community_comments', 'DELETE')
    and has_table_privilege('anon', 'public.community_comments', 'INSERT')
    and has_table_privilege('anon', 'public.community_comments', 'UPDATE')
    and exists (
        select 1 from pg_policies
        where schemaname = 'public' and tablename = 'community_comments'
          and policyname = 'public delete comments' and cmd = 'DELETE'
          and roles = array['public'::name] and qual = 'true'
    )
) as rollback_restored_observed_contract
\gset
\if :rollback_restored_observed_contract
\else
    \echo 'RLS-001 rollback rehearsal did not restore the observed contract'
    \quit 1
\endif
\ir ../migrations/20260726171001_community_comments_delete_rls.sql

select (
    not has_table_privilege('anon', 'public.community_comments', 'DELETE')
    and not has_table_privilege('anon', 'public.community_comments', 'INSERT')
    and not has_table_privilege('anon', 'public.community_comments', 'UPDATE')
    and has_table_privilege('authenticated', 'public.community_comments', 'DELETE')
    and has_table_privilege('authenticated', 'public.community_comments', 'INSERT')
    and not has_table_privilege('authenticated', 'public.community_comments', 'UPDATE')
) as secured_contract_reapplied
\gset
\if :secured_contract_reapplied
\else
    \echo 'RLS-001 secured contract was not restored after rollback rehearsal'
    \quit 1
\endif

\echo 'RLS-001 SQL/E2E passed: outsider update/delete denied+immutable, insert spoof denied, owner Android flow=green, inactive admin=0, active admin=1, anon/auth SELECT, rollback+cleanup=verified'
