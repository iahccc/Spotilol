const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '../app/src/main/java/com/project/lol/');
const client = fs.readFileSync(path.join(root, 'webview/SpotifyWebViewClient.kt'), 'utf8');
const ads = fs.readFileSync(path.join(root, 'webview/helpers/AdBlocker.kt'), 'utf8');

test('ordinary Spotify /audio/ paths are not intercepted as ads', () => {
  const code = client.slice(client.indexOf('override fun shouldInterceptRequest('),
    client.indexOf('private fun isGoogleAuthUrl('));
  assert.ok(code.includes('if (!isGoogleAuthUrl(url)) return null'),
    'non-Google requests must pass back to WebView unless explicitly identified');
  assert.ok(!code.includes('isAdAudioUrl(url)'),
    'generic CDN audio paths must never trigger native ad sniffing');
  assert.ok(!code.includes('contentType == "audio/mpeg"'),
    'MIME audio/mpeg is not proof of an advertisement');
});

test('only explicit ad patterns and state-identified ad IDs can produce silence', () => {
  const code = client.slice(client.indexOf('override fun shouldInterceptRequest('),
    client.indexOf('private fun isGoogleAuthUrl('));
  assert.ok(code.includes('AdIdStore.matches(url)'));
  assert.ok(code.includes('matchAdCdn(url)'));
  assert.ok(code.includes('WebResourceResponse("audio/mpeg", null, silent)'));
  assert.ok(ads.includes('"scdn.co/mp3-ad/"'));
  assert.ok(!/AD_CDN_PATTERNS\s*=\s*listOf\([\s\S]*?"scdn.co\/audio\/"/.test(ads),
    'generic Spotify audio CDN path must not be treated as an explicit ad path');
});

test('normal Spotify media continues to use the WebView network stack', () => {
  const code = client.slice(client.indexOf('override fun shouldInterceptRequest('),
    client.indexOf('private fun isGoogleAuthUrl('));
  const explicit = code.indexOf('val adMatch = matchAdCdn(url)');
  const webview = code.indexOf('if (!isGoogleAuthUrl(url)) return null');
  assert.ok(explicit > 0 && webview > explicit);
  assert.ok(!code.includes('if (isAdAudioUrl(url))'));
});
