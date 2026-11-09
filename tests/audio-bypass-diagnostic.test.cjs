const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../app/src/main/java/com/project/lol');
const client = fs.readFileSync(path.join(root, 'webview/SpotifyWebViewClient.kt'), 'utf8');
const diag = fs.readFileSync(path.join(root, 'webview/injections/PlaybackDiagnostics.kt'), 'utf8');

test('diagnostic intercept returns null before matching any media or ad URL', () => {
  assert.match(client, /private const val AUDIO_BYPASS_DIAGNOSTIC = true/);
  const segment = client.slice(client.indexOf('override fun shouldInterceptRequest('),
                               client.indexOf('private fun isGoogleAuthUrl('));
  const bypass = segment.indexOf('if (AUDIO_BYPASS_DIAGNOSTIC) return null');
  const urlInspection = segment.indexOf('val url = request.url.toString()');
  assert.ok(bypass >= 0 && urlInspection > bypass);
});

test('diagnostic document-start injection skips all page overrides', () => {
  const segment = client.slice(client.indexOf('private fun buildEarlyJs('),
                               client.indexOf('private fun isWebPlayerUrl('));
  const bypass = segment.indexOf('if (AUDIO_BYPASS_DIAGNOSTIC)');
  const exit = segment.indexOf('return "window.__splAudioBypassDiagnostic=true;"');
  const firstOverride = segment.indexOf('add(if (isGoogle) GoogleSpoof.CONTENT else BrowserSpoof.CONTENT)');
  assert.ok(bypass >= 0 && exit > bypass && firstOverride > exit);
});

test('changing block-service-worker setting cannot reinstall bypassed hook', () => {
  const segment = client.slice(client.indexOf('"BlockServiceWorker" -> {'),
                               client.indexOf('"HideEmptyPlayer" -> {'));
  const guard = segment.indexOf('if (AUDIO_BYPASS_DIAGNOSTIC)');
  const hook = segment.indexOf('WorkerNeutralize.CONTENT');
  assert.ok(guard >= 0 && hook > guard);
  assert.ok(segment.includes('else if (prefs.getBoolean("BlockServiceWorker", true))'));
});

test('diagnostics identify bypass and inspect media without exposing URLs', () => {
  assert.match(diag, /appVersion:'1.2.18-audio-bypass'/);
  assert.match(diag, /audioBypassDiagnostic:!!window.__splAudioBypassDiagnostic/);
  assert.match(diag, /mediaElements:safe\(function\(\)/);
  assert.match(diag, /spotilolCanvas:el.id==='spl-canvas'/);
  assert.match(diag, /hasSource:!!\(el.currentSrc\|\|el.src\|\|el.srcObject\)/);
  assert.ok(!diag.includes('src:el.currentSrc'));
});
