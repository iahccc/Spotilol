const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const injectionDir = path.resolve(__dirname, '../app/src/main/java/com/project/lol/webview/injections');
function injection(name) {
  const source = fs.readFileSync(path.join(injectionDir, name), 'utf8');
  const match = source.match(/const val CONTENT\s*=\s*"""([\s\S]*?)"""/);
  assert.ok(match, 'Missing script: ' + name);
  return match[1];
}
const ids = {
  a: 'AAAAAAAAAAAAAAAAAAAAAA',
  b: 'BBBBBBBBBBBBBBBBBBBBBB',
  c: 'CCCCCCCCCCCCCCCCCCCCCC'
};
function widget(id, title, artist, artwork) {
  const link = { isConnected:true, textContent:title,
    getAttribute: key => key === 'href' ? '/track/' + id : null };
  const artistNode = { textContent:artist };
  const imageNode = { src:artwork };
  const root = {
    link,
    contains: node => node === root.link,
    querySelector: selector => {
      if (selector === 'a[href*="/track/"]') return root.link;
      if (selector === 'a[data-testid="context-item-info-artist"]') return artistNode;
      if (selector === 'img[data-testid="cover-art-image"]') return imageNode;
      return null;
    }
  };
  return root;
}
function mediaRuntime(initialWidget) {
  let active = initialWidget;
  const events = [];
  const window = {
    __splTrackMeta: Object.create(null),
    splTrackId: initialWidget.link.getAttribute('href').split('/').pop(),
    splOnTrackChange: undefined
  };
  const document = { querySelector: selector => {
    if (selector === '[data-testid="now-playing-widget"]') return active;
    return null;
  }, querySelectorAll: () => [] };
  const AndBridge = {
    recMediaStatus: raw => events.push(JSON.parse(raw)),
    recMediaPosition: () => {}
  };
  const ctx = vm.createContext({
    window, document, AndBridge, location:{pathname:'/playlist/example'},
    track:'old global title',artist:'old global artist',cover:'old global cover',
    playing:true,repmode:'false',isfav:false,shuffle:'off',
    duration:100000,position:5000,lastPos:null,lastState:null,
    setTimeout:() => 0, Date, Promise, JSON
  });
  vm.runInContext(injection('MediaUpdater.kt'), ctx);
  return { window, ctx, events, updateWidget: w => { active = w; } };
}

test('media status pairs title, artist, artwork and id from the same current song', () => {
  const state = mediaRuntime(widget(ids.a,'Song A','Artist A','https://img/a'));
  state.window.updMedia();
  assert.equal(state.events.at(-1).id,ids.a);
  state.updateWidget(widget(ids.b,'Song B','Artist B','https://img/b'));
  state.window.__splTrackMeta[ids.b] = { album: 'Album B' };
  state.window.updMedia();
  const b=state.events.at(-1);
  assert.deepEqual({id:b.id,track:b.track,artist:b.artist,cover:b.cover,album:b.album},
    {id:ids.b,track:'Song B',artist:'Artist B',cover:'https://img/b',album:'Album B'});
});

test('during a transition never send old global title and cover as the new song', () => {
  const state = mediaRuntime(widget(ids.a,'Song A','Artist A','https://img/a'));
  state.window.updMedia();
  state.updateWidget(widget(ids.c,'','',''));
  state.window.updMedia();
  const c=state.events.at(-1);
  assert.equal(c.id,ids.c);
  assert.equal(c.track,'');
  assert.equal(c.artist,'');
  assert.equal(c.cover,'');
  assert.notEqual(c.track,'old global title');
});

test('background track metadata cannot change currently playing globals', async () => {
  let currentId=ids.a;
  const response = { canonical_uri:'spotify:track:'+ids.b,
    name:'Song B', artist:[{name:'Artist B'}],
    album:{name:'Album B',cover_group:{image:[]}} };
  const window = {
    fetch: async () => new Response(JSON.stringify(response)),
    splPlayingTrackId: () => currentId,
    __curTrackId: ids.a,
    __curTrackName:'Song A',
    __curTrackAlbum:'Album A'
  };
  const ctx=vm.createContext({window,Response,JSON,Promise,Date});
  vm.runInContext(injection('FetchOverride.kt'),ctx);
  const result=await window.fetch('https://api.spotify.com/metadata/4/track/'+ids.b);
  await result.text();
  await new Promise(r=>setTimeout(r,0));
  assert.equal(window.__curTrackId,ids.a);
  assert.equal(window.__curTrackName,'Song A');
  currentId=ids.b;
  assert.equal(window.__splTrackMeta[ids.b].name,'Song B');
});

test('track observer discards connected links moved out of the active widget', () => {
  const wA=widget(ids.a,'Song A','Artist A','https://img/a');
  const wB=widget(ids.b,'Song B','Artist B','https://img/b');
  const root=wA;
  let observer;
  const window={ dispatchEvent:() => {}, __splBusy:false };
  const AndBridge={dbg:() => {}};
  const document={querySelector: selector => selector === '[data-testid="now-playing-widget"]' ? root : null };
  class MutationObserver {
    constructor(cb) {this.cb=cb;observer=this;}
    observe() {}
    disconnect() {}
  }
  const ctx=vm.createContext({window,document,AndBridge,MutationObserver,
    performance,CustomEvent:class CustomEvent {},setInterval:() => 1,clearInterval:()=>{}});
  vm.runInContext(injection('TrackObserver.kt'),ctx);
  assert.equal(window.splTrackId,ids.a);
  // Spotify may retain the old DOM node in the widget during a transition.
  // The currently selected link is the new first link even while old is connected.
  root.link=wB.link;
  root.contains=() => true;
  observer.cb();
  assert.equal(window.splTrackId,ids.b);
});

test('custom player uses only the active song snapshot for its metadata', () => {
  const source=fs.readFileSync(path.join(injectionDir,'SpotilolPlayer.kt'),'utf8');
  assert.ok(source.includes('window.splCurrentTrackSnapshot'));
  assert.ok(source.includes('pl.__splDisplayedTrackId'));
  assert.ok(!source.includes("var trackEl=window.splQ('a[data-testid=context-item-link]')"));
});
