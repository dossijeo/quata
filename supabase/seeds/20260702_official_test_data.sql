-- Test data for the official wall. Safe to rerun.
-- The account is clearly marked as a test account to avoid confusion with real institutions.

insert into public.community_profiles (
    id,
    display_name,
    phone,
    pass_hash,
    phone_normalized,
    country_code,
    phone_local,
    phone_e164,
    barrio,
    barrio_normalized,
    neighborhood,
    nombre,
    telefono,
    avatar_url,
    is_admin,
    is_official
) values (
    '0f1c1a10-0000-4a11-8ff1-000000000001'::uuid,
    'Cuenta Oficial de Prueba',
    '+240000000001',
    'official-test-disabled',
    '240000000001',
    '+240',
    '000000001',
    '+240000000001',
    'Malabo',
    'malabo',
    'Malabo',
    'Cuenta Oficial de Prueba',
    '+240000000001',
    'https://flagcdn.com/w320/gq.png',
    false,
    true
)
on conflict (id) do update
set display_name = excluded.display_name,
    phone = excluded.phone,
    phone_normalized = excluded.phone_normalized,
    country_code = excluded.country_code,
    phone_local = excluded.phone_local,
    phone_e164 = excluded.phone_e164,
    barrio = excluded.barrio,
    barrio_normalized = excluded.barrio_normalized,
    neighborhood = excluded.neighborhood,
    nombre = excluded.nombre,
    telefono = excluded.telefono,
    avatar_url = excluded.avatar_url,
    is_official = true;

insert into public.official_posts (
    id,
    profile_id,
    title,
    summary,
    post_type,
    content_html,
    media_url,
    media_type,
    link_url,
    is_live,
    published_at
) values
(
    '0f1c1a10-0000-4a11-8ff1-000000000101'::uuid,
    '0f1c1a10-0000-4a11-8ff1-000000000001'::uuid,
    'Prueba: responsabilidad fiscal en obras publicas',
    'El Gobierno ha revisado la gestion de empresas de mantenimiento de obras publicas y ha pedido mayor rigor fiscal.',
    'announcement',
    '<h2>Responsabilidad fiscal</h2><p>Publicacion de prueba basada en una noticia reciente sobre la revision de empresas de mantenimiento de obras publicas.</p><blockquote>Contenido de muestra para validar el muro oficial de Quata.</blockquote>',
    null,
    null,
    'https://www.guineaecuatorialpress.com/noticias/el_gobierno_exige_responsabilidad_fiscal_y_rigor_en_la_gestion_de_las_empresas_de_mantenimiento_de_obras_publicas',
    false,
    now() - interval '2 hours'
),
(
    '0f1c1a10-0000-4a11-8ff1-000000000102'::uuid,
    '0f1c1a10-0000-4a11-8ff1-000000000001'::uuid,
    'Prueba: reestructuracion de INSESO',
    'El Gobierno prioriza la reestructuracion institucional antes de nuevas contrataciones.',
    'news',
    '<h2>Reestructuracion institucional</h2><p>Publicacion con imagen para probar el formato visual del muro oficial.</p><ul><li>Resumen breve.</li><li>Enlace a informacion ampliada.</li></ul>',
    'https://upload.wikimedia.org/wikipedia/commons/thumb/3/31/Flag_of_Equatorial_Guinea.svg/640px-Flag_of_Equatorial_Guinea.svg.png',
    'image',
    'https://www.guineaecuatorialpress.com/',
    false,
    now() - interval '90 minutes'
),
(
    '0f1c1a10-0000-4a11-8ff1-000000000103'::uuid,
    '0f1c1a10-0000-4a11-8ff1-000000000001'::uuid,
    'Prueba: talleres de verano 2026',
    'Convocatoria cultural de verano usada para validar publicaciones oficiales con video.',
    'event',
    '<h2>Talleres de verano</h2><p>Publicacion de prueba con video para comprobar reproduccion, resumen y detalle.</p><p>El contenido puede ampliarse desde el modo de lectura completa.</p>',
    'https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4',
    'video',
    'https://www.guineaecuatorialpress.com/noticias/abierta_la_convocatoria_para_participar_en_los_talleres_de_verano_2026',
    true,
    now() - interval '45 minutes'
)
on conflict (id) do update
set title = excluded.title,
    summary = excluded.summary,
    post_type = excluded.post_type,
    content_html = excluded.content_html,
    media_url = excluded.media_url,
    media_type = excluded.media_type,
    link_url = excluded.link_url,
    is_live = excluded.is_live,
    is_published = true,
    deleted_at = null,
    published_at = excluded.published_at,
    updated_at = now();
