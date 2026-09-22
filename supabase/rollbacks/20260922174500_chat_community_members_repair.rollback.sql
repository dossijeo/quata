-- Restore only the pre-deployment Community-key function definition.
-- Participant synchronization is not generically reversible; use the reviewed restore point
-- if those data changes require rollback.

CREATE OR REPLACE FUNCTION public.quata_chat_community_key(p_value text)
 RETURNS text
 LANGUAGE sql
 IMMUTABLE
AS $function$
    select nullif(
        regexp_replace(
            translate(
                lower(coalesce(p_value, '')),
                '??????????????????????????????????????????????????',
                'aaaaaaeeeeiiiiooooouuuunc'
            ),
            '[^a-z0-9]+',
            '',
            'g'
        ),
        ''
    )
$function$;
