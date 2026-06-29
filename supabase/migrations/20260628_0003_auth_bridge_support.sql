-- Optional support indexes for the Quata Supabase Auth bridge.
-- The chat schema already added community_profiles.auth_user_id.

do $$
begin
    if exists (
        select 1
        from information_schema.columns
        where table_schema = 'public'
          and table_name = 'community_profiles'
          and column_name = 'phone_local'
    ) then
        create index if not exists community_profiles_phone_local_idx
            on public.community_profiles(phone_local)
            where phone_local is not null;
    end if;

    if exists (
        select 1
        from information_schema.columns
        where table_schema = 'public'
          and table_name = 'community_profiles'
          and column_name = 'phone_normalized'
    ) then
        create index if not exists community_profiles_phone_normalized_idx
            on public.community_profiles(phone_normalized)
            where phone_normalized is not null;
    end if;

    if exists (
        select 1
        from information_schema.columns
        where table_schema = 'public'
          and table_name = 'community_profiles'
          and column_name = 'telefono'
    ) then
        create index if not exists community_profiles_telefono_idx
            on public.community_profiles(telefono)
            where telefono is not null;
    end if;

    if exists (
        select 1
        from information_schema.columns
        where table_schema = 'public'
          and table_name = 'community_profiles'
          and column_name = 'country_code'
    ) then
        create index if not exists community_profiles_country_code_idx
            on public.community_profiles(country_code)
            where country_code is not null;
    end if;

    if exists (
        select 1
        from information_schema.columns
        where table_schema = 'public'
          and table_name = 'community_profiles'
          and column_name = 'code'
    ) then
        create index if not exists community_profiles_code_idx
            on public.community_profiles(code)
            where code is not null;
    end if;
end $$;
