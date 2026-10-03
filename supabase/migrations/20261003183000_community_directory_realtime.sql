-- Publish every base table that can change the public Communities directory.
-- community_walls_stats is a view over these relations and cannot itself emit
-- Postgres Changes events.
do $$
declare
    v_target record;
begin
    for v_target in
        select *
        from (values
            ('community_profiles', 'id'),
            ('community_walls', 'id'),
            ('community_members', 'wall_id, profile_id'),
            ('community_posts', 'id')
        ) as targets(table_name, published_columns)
    loop
        if to_regclass('public.' || v_target.table_name) is not null
           and not exists (
               select 1
               from pg_publication_tables
               where pubname = 'supabase_realtime'
                 and schemaname = 'public'
                 and tablename = v_target.table_name
           ) then
            execute format(
                'alter publication supabase_realtime add table public.%I (%s)',
                v_target.table_name,
                v_target.published_columns
            );
        end if;
    end loop;
end $$;
