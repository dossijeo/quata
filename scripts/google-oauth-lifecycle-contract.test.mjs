import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');
const source = (path) => readFile(resolve(root, path), 'utf8');

test('Google sign-in reserves user-activation work before dispatching the suspendable exchange', async () => {
  const viewModel = await source('feature/auth/src/commonMain/kotlin/com/quata/feature/auth/presentation/login/LoginViewModel.kt');
  const body = viewModel.slice(viewModel.indexOf('private fun loginWithGoogle()'), viewModel.indexOf('fun close()'));
  assert.ok(body.indexOf('provider.beginSignIn()') < body.indexOf('scope.launch'));
  assert.match(body, /scope\.launch\(start = CoroutineStart\.UNDISPATCHED\)/);
  assert.match(viewModel, /GoogleCancel -> googleLoginJob\?\.cancel\(GoogleOAuthUserCancellation\(\)\)/);
  assert.match(viewModel, /failure is CancellationException\) throw failure/);
  assert.match(viewModel, /catch \(failure: TimeoutCancellationException\)[\s\S]*?failure is TimeoutCancellationException[\s\S]*?LoginEffect\.Failure/);

  const profile = await source('feature/profile/src/commonMain/kotlin/com/quata/feature/profile/presentation/ProfileScreenHost.kt');
  const click = profile.slice(profile.indexOf('if (isLinkingGoogle)'), profile.indexOf('add(ProfileManagementAction(strings.deactivate'));
  assert.match(click, /val linkIdentity = onLinkGoogleIdentity\(\)/);
  assert.ok(click.indexOf('val linkIdentity = onLinkGoogleIdentity()') < click.indexOf('scope.launch'));
  assert.match(click, /scope\.launch\(start = CoroutineStart\.UNDISPATCHED\)/);
  assert.match(click, /googleLinkJob\?\.cancel\(GoogleOAuthUserCancellation\(\)\)/);
  assert.match(click, /catch \(_: TimeoutCancellationException\)[\s\S]*?finally \{[\s\S]*?isLinkingGoogle = false/);
});

test('Web OAuth owns one pre-opened popup and cancels popup polling plus HTTP requests', async () => {
  const repository = await source('web/src/wasmJsMain/kotlin/com/quata/web/WebAuthRepository.kt');
  assert.match(repository, /beginSignIn\(\)[\s\S]*?reserveWebGoogleOAuthPopup\(\)[\s\S]*?signInWithGoogle\(popupToken\)/);
  assert.match(repository, /beginIdentityLink\(\)[\s\S]*?reserveWebGoogleOAuthPopup\(\)[\s\S]*?linkGoogleIdentity\(popupToken\)/);
  assert.match(repository, /globalThis\.open\('about:blank',[\s\S]*?__quataGoogleOAuthPopups/);
  assert.match(repository, /sessionStorage\.setItem\('quata_google_oauth_channel', token\)/);
  assert.match(repository, /new globalThis\.BroadcastChannel\(`quata-google-oauth-[\s\S]*?channel\.onmessage/);
  assert.doesNotMatch(repository, /timer = globalThis\.setInterval\(\(\) => \{\s*if \(popup\.closed\)/);
  assert.match(repository, /awaitWebGoogleOAuthCallback[\s\S]*?suspendCancellableCoroutine[\s\S]*?invokeOnCancellation \{ cancel\(\) \}/);
  assert.match(repository, /private fun browserGoogleOAuth[\s\S]*?return \(\) => \{[\s\S]*?cleanup\(true\)/);
  assert.match(repository, /isClosed = popup\.closed[\s\S]*?!canInspectLocation && isClosed === true[\s\S]*?isClosed === true && canInspectLocation && !detachedByCoop[\s\S]*?google_oauth_popup_closed/);
  assert.equal((repository.match(/private suspend fun web(?:Post|Get)Json[\s\S]*?suspendCancellableCoroutine/g) ?? []).length, 2);
  assert.ok((repository.match(/invokeOnCancellation \{ cancel\(\) \}/g) ?? []).length >= 3);
  assert.ok((repository.match(/controller\?\.abort\(\)/g) ?? []).length >= 4);
  assert.ok((repository.match(/onFailure \{ if \(it is CancellationException\) throw it \}/g) ?? []).length >= 2);
  assert.match(repository, /val retainedSession = sessionMutationMutex\.withLock \{ storedSessionOrNull\(\) \}/);
  assert.match(repository, /webGoogleOAuthSessionStillCurrent\(retainedSession, storedSessionOrNull\(\)\)/);

  const main = await source('web/src/wasmJsMain/kotlin/com/quata/web/Main.kt');
  assert.match(main, /if \(completeWebGoogleOAuthPopupCallback\(\)\) return/);
  assert.match(main, /location\.hash\.replace\(\/\^#\//);
  assert.match(main, /sessionStorage\?\.getItem\('quata_google_oauth_channel'\)[\s\S]*?BroadcastChannel[\s\S]*?postMessage/);
});

test('Android OAuth persists PKCE state before browser handoff and resumes an unclaimed callback', async () => {
  const helper = await source('app/src/main/java/com/quata/core/auth/GoogleAuthHelper.kt');
  const handoff = helper.slice(helper.indexOf('private suspend fun awaitAndComplete'), helper.indexOf('private suspend fun completePending'));
  assert.ok(handoff.indexOf('store.prepare(pending)') < handoff.indexOf('AndroidGoogleOAuthCallbackCoordinator.awaitCompletion'));
  assert.match(helper, /AndroidKeystorePreferenceValueCipher\(KeyAlias\)/);
  assert.match(helper, /putString\(PendingRecordKey, cipher\.encrypt\(plaintext\)\)\.commit\(\)/);
  assert.match(helper, /redirectUri\(callbackToken\)[\s\S]*?callbackToken = callbackToken/);
  assert.match(helper, /callbackMatchesPendingGoogleOAuth\(callback, pending\)[\s\S]*?google_oauth_callback_attempt_mismatch/);
  assert.match(helper, /shouldClearPendingGoogleOAuth\(failure\)[\s\S]*?failure is GoogleOAuthUserCancellation[\s\S]*?failure is TimeoutCancellationException[\s\S]*?failure !is CancellationException/);
  assert.match(helper, /suspend fun resumePending/);
  assert.match(helper, /fun complete\(attempt: AndroidGoogleOAuthPending, result: Result<AuthSession>\): Boolean/);
  assert.match(helper, /if \(waiter\.attempt != attempt \|\| !pending\.compareAndSet\(waiter, null\)\) return false/);
  assert.ok((helper.match(/onFailure \{ if \(it is CancellationException\) throw it \}/g) ?? []).length >= 3);

  const activity = await source('app/src/main/java/com/quata/MainActivity.kt');
  assert.match(activity, /AndroidGoogleOAuthCallbackCoordinator\.isCallback\(callback\)[\s\S]*?resumeGoogleOAuthCallback[\s\S]*?sourceIntent\.data = null/);

  const application = await source('app/src/main/java/com/quata/QuataApp.kt');
  assert.match(application, /googleOAuthResumeJob = appScope\.launch[\s\S]*?exchangeGoogleOAuthCallback\(callback\)[\s\S]*?val publication[\s\S]*?publishGoogleOAuthRecovery\(completion\)[\s\S]*?complete\([\s\S]*?callbackResult\.pending[\s\S]*?publication/);
  assert.match(application, /shouldPublishGoogleOAuthRecoveryNavigation\([\s\S]*?callbackExchange\?\.pending\?\.mode[\s\S]*?deliveredToActiveLogin[\s\S]*?publication\?\.isSuccess == true[\s\S]*?publishGoogleOAuthRecovery\(\)/);
  assert.match(application, /shouldPublishGoogleOAuthRecoveryNavigation[\s\S]*?mode == AndroidGoogleOAuthMode\.SIGN_IN[\s\S]*?!deliveredToActiveLogin[\s\S]*?publicationSucceeded/);

  const navigation = await source('app/src/main/java/com/quata/core/navigation/AppNavGraph.kt');
  assert.match(navigation, /googleOAuthRecoveries\.collect[\s\S]*?navigateAfterAuthentication\(\)/);

  const repository = await source('app/src/main/java/com/quata/feature/auth/data/AuthRepositoryImpl.kt');
  const signIn = repository.slice(repository.indexOf('private suspend fun signInWithGoogle'), repository.indexOf('override fun beginIdentityLink'));
  assert.doesNotMatch(signIn, /sessionManager\.setSession/);
  assert.match(repository, /publishGoogleOAuthRecovery[\s\S]*?consumePending[\s\S]*?AndroidGoogleOAuthMode\.SIGN_IN[\s\S]*?publishSessionIfAbsent/);
  assert.match(repository, /AndroidGoogleOAuthMode\.LINK_IDENTITY[\s\S]*?publishSessionIfActorMatches/);

  const iosRepository = await source('feature/auth/src/iosMain/kotlin/com/quata/feature/auth/data/IosAuthRepository.kt');
  assert.ok((iosRepository.match(/onFailure \{ if \(it is CancellationException\) throw it \}/g) ?? []).length >= 2);

  for (const path of ['app/src/main/res/xml/backup_rules.xml', 'app/src/main/res/xml/data_extraction_rules.xml']) {
    assert.match(await source(path), /quata_google_oauth_pending\.xml/);
  }
});
