#!/usr/bin/env node
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const source = await readFile(new URL('./supabase-e2e-sb07-post-forward.mjs', import.meta.url), 'utf8');
assert.doesNotMatch(source, /response\.body\?\.user\?\.id|authIds\.add\(/, 'bridge response must never select an auth user for deletion');
assert.match(source, /select id,auth_user_id from public\.community_profiles where id=any/, 'cleanup must derive auth IDs from fixture profiles');
assert.match(source, /profileMappingExact=.*links\.length===profiles\.length/, 'cleanup must require an exact fixture profile mapping');
assert.match(source, /select id,email from auth\.users where lower\(email\)=any/, 'partial profile mapping must use deterministic fixture emails only');
assert.match(source, /await writeFile\(process\.env\.QUATA_SB07_RECOVERY_FILE/, 'unresolved mapping must preserve a local recovery recipe');
assert.match(source, /residual_pending_auth_mapping/, 'partial mapping must remain recoverable rather than deleting profiles');
assert.match(source, /delete from auth\.users where id=any\(\$1::uuid\[\]\)/, 'only database-derived IDs may reach auth cleanup');
console.log('SB-07 post-forward cleanup contract passed');
