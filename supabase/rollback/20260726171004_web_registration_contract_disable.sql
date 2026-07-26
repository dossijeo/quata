begin;
-- Non-destructive incident rollback. Cleanup RPCs and audit data remain available.
revoke execute on function public.quata_claim_web_registration(text,text,text,text,text)
from service_role;
commit;
