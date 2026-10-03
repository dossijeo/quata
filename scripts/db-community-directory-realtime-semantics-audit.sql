select jsonb_build_object(
    'publication', (
        select jsonb_agg(
            jsonb_build_object(
                'table', tablename,
                'columns', attnames,
                'rowFilter', rowfilter
            )
            order by tablename
        )
        from pg_publication_tables
        where pubname = 'supabase_realtime'
          and schemaname = 'public'
          and tablename in (
              'community_profiles',
              'community_walls',
              'community_members',
              'community_posts',
              'community_messages'
          )
    ),
    'newPublicationColumnsSafe', (
        select count(*) = 4
        from pg_publication_tables
        where pubname = 'supabase_realtime'
          and schemaname = 'public'
          and (
              (tablename in ('community_profiles', 'community_walls', 'community_posts') and attnames = array['id']::name[])
              or (tablename = 'community_members' and attnames = array['wall_id', 'profile_id']::name[])
          )
    ),
    'viewKind', (
        select relkind
        from pg_class
        where oid = 'public.community_walls_stats'::regclass
    ),
    'primaryKeys', (
        select jsonb_object_agg(c.relname, exists (
            select 1
            from pg_constraint k
            where k.conrelid = c.oid
              and k.contype = 'p'
        ))
        from pg_class c
        join pg_namespace n on n.oid = c.relnamespace
        where n.nspname = 'public'
          and c.relname in (
              'community_profiles',
              'community_walls',
              'community_members',
              'community_posts',
              'community_messages'
          )
    )
);
