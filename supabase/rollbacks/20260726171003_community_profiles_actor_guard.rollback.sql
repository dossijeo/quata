begin;

drop policy if exists "authenticated update profiles" on public.community_profiles;
drop policy if exists "public insert profiles" on public.community_profiles;
drop policy if exists "public read profiles" on public.community_profiles;

create policy "public read profiles"
on public.community_profiles
for select
using (true);

create policy "public insert profiles"
on public.community_profiles
for insert
with check (true);

create policy "public update profiles"
on public.community_profiles
for update
using (true)
with check (true);

create policy "Users can view their own profile"
on public.community_profiles
for select
using (auth.uid() = id);

create policy "Users can insert their own profile"
on public.community_profiles
for insert
with check (auth.uid() = id);

create policy "Users can update their own profile"
on public.community_profiles
for update
using (auth.uid() = id);

create or replace function public.quata_guard_profile_roles()
returns trigger
language plpgsql
security definer
set search_path = public, auth
as $$
begin
    if tg_op = 'UPDATE'
       and (new.is_admin is distinct from old.is_admin
            or new.is_official is distinct from old.is_official) then
        if public.quata_current_role_is_service() then
            return new;
        end if;

        if not public.quata_current_profile_is_admin() then
            raise exception 'Only administrators can change official roles'
                using errcode = '42501';
        end if;
    end if;

    return new;
end;
$$;

drop trigger if exists quata_guard_profile_roles_trg on public.community_profiles;
create trigger quata_guard_profile_roles_trg
before update on public.community_profiles
for each row execute function public.quata_guard_profile_roles();

grant delete, insert, references, select, trigger, truncate, update
on public.community_profiles
to anon, authenticated;

commit;
