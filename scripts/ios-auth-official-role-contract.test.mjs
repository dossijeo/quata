import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const iosAuth = await readFile(new URL('../feature/auth/src/iosMain/kotlin/com/quata/feature/auth/data/IosAuthRepository.kt', import.meta.url), 'utf8');

test('iOS auth restores the official role from the authenticated profile like Web', () => {
  assert.match(iosAuth, /interface IosAuthHttpTransport/);
  assert.match(iosAuth, /suspend fun get\(endpoint: String, headers: Map<String, String>\): IosAuthHttpResponse/);
  assert.match(iosAuth, /rawSession\.isOfficial \|\| fetchAuthenticatedProfileIsOfficial\(rawSession\.bearerToken, rawSession\.userId\)/);
  assert.match(iosAuth, /\/rest\/v1\/community_profiles\?select=is_official&id=eq\.\$profileId&limit=1/);
  assert.match(iosAuth, /"Authorization" to "Bearer \$accessToken"/);
  assert.match(iosAuth, /\.jsonArray[\s\S]*?booleanOrNull == true/);
  assert.match(iosAuth, /setHTTPMethod\("GET"\)/);
  assert.doesNotMatch(iosAuth, /action", "web_login"/);
});
