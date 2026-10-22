const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const sourcePath = (file) => path.resolve(__dirname,
  '../app/src/main/java/com/project/lol/webview/injections', file);
function injection(file) {
  const code = fs.readFileSync(sourcePath(file), 'utf8');
  const match = code.match(/const val CONTENT\s*=\s*"""([\s\S]*?)"""/);
  assert.ok(match, file + ' should contain a JavaScript injection');
  return match[1];
}
function setup() {
  const requests = [], cancelled = [];
  const AndBridge = {
    nFetchAsync: (id, url, opts) => requests.push({ id, url, opts: JSON.parse(opts) }),
    cancelFetch: id => cancelled.push(id)
  };
  const window = { AndBridge };
  const context = vm.createContext({
    window, AndBridge, Request, Headers, Response, AbortController,
    DOMException, Uint8Array, atob, TextEncoder, performance,
    setTimeout, clearTimeout, Date, Promise, JSON
  });
  vm.runInContext(injection('ClassicBridge.kt'), context);
  return { window, requests, cancelled };
}

test('native Fetch returns immediately and resolves to a cloneable Response', async () => {
  const { window, requests } = setup();
  const promise = window.mngFetch('https://gew4-spclient.spotify.com/connect-state', {
    method:'POST', headers:{'Content-Type':'application/json'}, body:'{"command":{}}'
  });
  assert.equal(requests.length, 1);
  assert.equal(requests[0].opts.method, 'POST');
  assert.equal(requests[0].opts.body, '{"command":{}}');
  window.__splNativeFetchDone(requests[0].id, JSON.stringify({
    status:200, body:'{"ok":true}', headers:{'content-type':'application/json'}
  }));
  const response = await promise;
  assert.equal(response.ok,true);
  assert.equal(response.url,'https://gew4-spclient.spotify.com/connect-state');
  const copy = response.clone();
  assert.deepEqual(await response.json(),{ok:true});
  assert.equal(await copy.text(), '{"ok":true}');
});

test('http errors resolve with their real status', async () => {
  const { window, requests } = setup();
  const pending = window.mngFetch('https://music.163.com/api/search/get/web');
  window.__splNativeFetchDone(requests[0].id, JSON.stringify({status:404,body:'missing',headers:{}}));
  const response = await pending;
  assert.equal(response.status,404);
  assert.equal(response.ok,false);
});

test('binary payload survives native bridge transport', async () => {
  const { window, requests } = setup();
  const pending = window.mngFetch('https://api.spotify.com/test');
  window.__splNativeFetchDone(requests[0].id, JSON.stringify({
    status:200,body:'',bodyBase64:'AP8B',headers:{'content-type':'application/octet-stream'}
  }));
  const response = await pending;
  assert.deepEqual(Array.from(new Uint8Array(await response.arrayBuffer())),[0,255,1]);
});

test('AbortSignal cancels the native request', async () => {
  const { window, requests, cancelled } = setup();
  const controller=new AbortController();
  const promise=window.mngFetch('https://api.spotify.com/test',{signal:controller.signal});
  controller.abort();
  await assert.rejects(promise, error=>error.name==='AbortError');
  assert.deepEqual(cancelled,[requests[0].id]);
});

test('spicy and netease lyrics have no synchronous JS network bridge calls', () => {
  assert.ok(!injection('SpicyLyrics.kt').includes('AndBridge.spicyLyrics('));
  assert.ok(injection('SpicyLyrics.kt').includes('AndBridge.spicyLyricsAsync('));
  assert.ok(!injection('NeteaseLyrics.kt').includes('AndBridge.nFetch('));
  assert.ok(injection('NeteaseLyrics.kt').includes('window.mngFetch(url,options)'));
});

test('player and Android notification trackers initialize independently', () => {
  const main = injection('MainLoop.kt');
  const tracker = injection('AndroidTracker.kt');
  const playerSrc = fs.readFileSync(sourcePath('SpotilolPlayer.kt'),'utf8');
  assert.ok(main.includes('setTimeout(function(){'));
  assert.ok(main.includes('addAndAuto();'));
  assert.ok(main.indexOf('addAndAuto();') < main.indexOf('window.wirePlayBtn'));
  assert.ok(tracker.includes('__splStatusWatch = setInterval'));
  assert.ok(playerSrc.includes('pl.__splReady=true'));
  assert.ok(playerSrc.includes("if(npb) npb.style.display=''"));
});
