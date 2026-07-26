#!/usr/bin/env node
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const source = await readFile(new URL('./supabase-e2e-sb07-post-forward.mjs', import.meta.url), 'utf8');
assert.doesNotMatch(source, /response\.body\?\.user\?\.id|authIds\.add\(/, 'bridge response must never select an auth user for deletion');
assert.match(source, /select id,auth_user_id from public\.community_profiles where id=any/, 'cleanup must derive auth IDs from fixture profiles');
assert.match(source, /safeAuthCleanup=.*links\.length===profiles\.length/, 'cleanup must require an exact fixture profile mapping');
assert.match(source, /if \(!safeAuthCleanup\).*cleanup_auth_identity_unverified/s, 'uncertain auth identity must fail without auth deletion');
assert.match(source, /delete from auth\.users where id=any\(\$1::uuid\[\]\)/, 'only database-derived IDs may reach auth cleanup');
console.log('SB-07 post-forward cleanup contract passed');
