begin;

create or replace function public.quata_legacy_android_v32_request_allowed()
returns boolean
language sql
stable
security definer
set search_path = pg_catalog, public
as $$
    with request_context as (
        select
            coalesce(
                nullif(current_setting('request.jwt.claim.role', true), ''),
                nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'role'
            ) as jwt_role,
            upper(coalesce(current_setting('request.method', true), '')) as method,
            coalesce(current_setting('request.path', true), '') as path,
            coalesce(nullif(current_setting('request.headers', true), '')::jsonb, '{}'::jsonb) as headers
    )
    select coalesce((
        select compatibility.enabled
           and context.jwt_role = 'anon'
           and context.method in ('PATCH', 'POST')
           and context.path = '/community_profiles'
           and lower(coalesce(context.headers ->> 'user-agent', '')) = 'okhttp/4.12.0'
           and context.headers ->> 'x-quata-client-generation' is null
           and context.headers ->> 'origin' is null
           and context.headers ->> 'referer' is null
           and lower(coalesce(context.headers ->> 'content-profile', 'public')) = 'public'
           and lower(coalesce(context.headers ->> 'prefer', '')) = 'return=representation'
           and coalesce(context.headers ->> 'apikey', '') <> ''
           and lower(coalesce(context.headers ->> 'authorization', '')) like 'bearer %'
        from public.quata_legacy_android_v32_compatibility compatibility
        cross join request_context context
        where compatibility.singleton
    ), false);
$$;

commit;
