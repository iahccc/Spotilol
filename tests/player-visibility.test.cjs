const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '../app/src/main/java/com/project/lol/webview/injections');
const player = fs.readFileSync(path.join(root, 'SpotilolPlayer.kt'), 'utf8');
function visibilityHarness({hideEmpty = true, playing = false, title = 'No track',
                           snapshot = null, widgetMedia = false, progress = false} = {}) {
  const controls = new Set();
  const state = {hideEmpty,playing,title,snapshot,widgetMedia,progress};
  const native = {style: {display:'none'}};
  const custom = {
    __splReady: true,
    classList:{
      toggle: (name,enabled) => enabled ? controls.add(name) : controls.delete(name),
      contains: name => controls.has(name)
    }
  };
  const window = {
    __splHideEmpty: state.hideEmpty,
    splCurrentTrackSnapshot: () => state.snapshot,
    splIsPlaying: () => state.playing
  };
  const document = {
    getElementById: id => id === 'spl-track' ? {textContent:state.title} : null,
    querySelector: selector => {
      if(selector === '[data-testid="now-playing-widget"]') {
        if(!state.widgetMedia) return null;
        return {querySelector: () => ({})};
      }
      if(selector === '[data-testid="playback-progressbar"] input[type="range"]')
        return state.progress ? {} : null;
      return null;
    }
  };
  const start=player.indexOf('window.splApplyEmpty=function(){');
  const end=player.indexOf('window.splApplyHideTopBar=function(){',start);
  assert.ok(start>=0 && end>start, 'Custom player must expose its visibility logic');
  vm.runInNewContext(player.slice(start,end), {window,document,pl:custom,npb:native});
  return {window,state,native,custom,hidden:()=>custom.classList.contains('spl-empty')};
}

test('initially empty player honors HideEmptyPlayer without permanently hiding the native fallback', () => {
  const h=visibilityHarness();
  h.window.splApplyEmpty();
  assert.equal(h.hidden(), true);
  assert.equal(h.native.style.display,'');
});

test('playing audio always unhides custom player even when track ID and title are missing', () => {
  const h=visibilityHarness();
  h.window.splApplyEmpty();
  h.state.playing=true;
  h.window.splApplyEmpty();
  assert.equal(h.hidden(),false);
  assert.equal(h.native.style.display,'none');
});

test('player stays visible during transient metadata gaps after first playback', () => {
  const h=visibilityHarness({playing:true});
  h.window.splApplyEmpty();
  h.state.playing=false;
  h.state.snapshot=null;
  h.state.widgetMedia=false;
  h.window.splApplyEmpty();
  assert.equal(h.hidden(),false);
});

test('a song-list play request keeps the player visible while metadata is loading', () => {
  const h=visibilityHarness();
  h.window.__splPlayRequestedAt=Date.now();
  h.window.splApplyEmpty();
  assert.equal(h.hidden(),false);
  h.window.__splPlayRequestedAt=Date.now()-21000;
  h.window.splApplyEmpty();
  assert.equal(h.hidden(),true);
});

test('now-playing widget or progress bar unhides the player without a track title', () => {
  for(const opts of [{widgetMedia:true},{progress:true},{snapshot:{id:null,name:'Song B'}}]) {
    const h=visibilityHarness(opts);
    h.window.splApplyEmpty();
    assert.equal(h.hidden(),false,JSON.stringify(opts));
  }
});

test('player with HideEmptyPlayer disabled is always visible', () => {
  const h=visibilityHarness({hideEmpty:false});
  h.window.splApplyEmpty();
  assert.equal(h.hidden(),false);
  assert.equal(h.native.style.display,'none');
});

test('custom player does not require a native aside if Spotify playback controls exist', () => {
  assert.match(player,/if\(!npb && !document\.querySelector\('\[data-testid="now-playing-widget"\]/);
  assert.match(player,/pl\.__splReady=true;\s*\/\/ Re-check actual playback readiness/);
  assert.match(player,/var newId=snapshot\?\(snapshot\.key\|\|snapshot\.id\)/);
});

test('track snapshot falls back to current widget metadata without inheriting a previous track', () => {
  const media=fs.readFileSync(path.join(root,'MediaUpdater.kt'),'utf8');
  const match=media.match(/const val CONTENT\s*=\s*"""([\s\S]*?)"""/);
  assert.ok(match);
  let widget;
  const window={
    splTrackId:'AAAAAAAAAAAAAAAAAAAAAA',
    __curTrackId:'AAAAAAAAAAAAAAAAAAAAAA',
    __curTrackName:'Previous song',
    __curTrackArtist:'Previous artist',
    __curTrackCover:'previous-image',
    __splTrackMeta:{}
  };
  const document={
    querySelector: selector=>selector==='[data-testid="now-playing-widget"]'?widget:null
  };
  const context=vm.createContext({window,document,location:{pathname:'/playlist/x'},Date,Promise,JSON,setTimeout:()=>{}});
  vm.runInContext(match[1],context);
  widget={
    querySelector: selector=>{
      if(selector==='a[href*="/track/"]') return null;
      if(selector.includes('context-item-info-title')) return {textContent:'Fresh song'};
      if(selector==='a[data-testid="context-item-info-artist"]') return {textContent:'Fresh artist'};
      if(selector==='img[data-testid="cover-art-image"]') return {src:'fresh-image'};
      return null;
    }
  };
  const snap=window.splCurrentTrackSnapshot();
  assert.equal(snap.id,null);
  assert.equal(snap.name,'Fresh song');
  assert.equal(snap.artist,'Fresh artist');
  assert.equal(snap.cover,'fresh-image');
  assert.notEqual(snap.name,window.__curTrackName);
  // Podcasts are handled by their dedicated episode metadata path.
  const oldQuery=widget.querySelector;
  widget.querySelector=selector=>selector==='a[data-testid="context-item-info-show"]'
    ?{textContent:'Podcast'}:oldQuery(selector);
  assert.equal(window.splCurrentTrackSnapshot(),null);
});
