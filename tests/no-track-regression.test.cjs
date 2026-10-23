const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');

const file=path.join(__dirname,'../app/src/main/java/com/project/lol/webview/injections/MediaUpdater.kt');
const kt=fs.readFileSync(file,'utf8');
const match=kt.match(/const val CONTENT\s*=\s*"""([\s\S]*?)"""/);
assert.ok(match,'MediaUpdater injection missing');
const code=match[1], A='AAAAAAAAAAAAAAAAAAAAAA', B='BBBBBBBBBBBBBBBBBBBBBB';

function link(title,id){
  return {textContent:title,getAttribute:(attr)=>attr==='href'?(id?'/track/'+id:'#'):null,closest:()=>null};
}
function makeFixture({widget=null,native=null,globalTitle=null,session=null,oldId=null}={}){
  const events=[];
  const bar=native && {
    querySelector: sel=>{
      if(sel.includes('context-item-link'))return native.link;
      if(sel.includes('context-item-info-artist')||sel.includes('context-item-info-show'))return native.artistNode||null;
      if(sel.includes('cover-art-image'))return native.imageNode||null;
      return null;
    }
  };
  const document={
    querySelector: sel=>{
      if(sel==='[data-testid="now-playing-widget"]')return widget;
      if(sel.startsWith('aside[data-testid="now-playing-bar"]'))return bar;
      if(sel==='a[data-testid="context-item-link"]')return globalTitle;
      return null;
    },
    querySelectorAll: sel=>{
      if(sel==='a[data-testid="context-item-link"]')return globalTitle?[globalTitle]:[];
      return [];
    }
  };
  const window={
    splTrackId:oldId,
    __curTrackId:oldId,
    __curTrackName:'Previous song',
    __curTrackArtist:'Previous artist',
    __curTrackCover:'https://image.invalid/previous.jpg',
    __splTrackMeta:oldId?{[oldId]:{name:'Previous song',artist:'Previous artist'}}:{}
  };
  const navigator={mediaSession:session?{metadata:session}:null};
  const ctx=vm.createContext({
    window,document,navigator,location:{pathname:'/playlist/one'},
    Promise,Date,JSON,track:null,artist:'',playing:true,repmode:'false',
    isfav:false,shuffle:'off',duration:200000,position:1000,lastPos:null,lastState:null,
    setTimeout:()=>0,AndBridge:{
      recMediaStatus:raw=>events.push(JSON.parse(raw)),
      recMediaPosition:()=>{}
    }
  });
  vm.runInContext(code,ctx);
  return {window,document,events};
}
function native(title,artist,id){
  return {
    link:link(title,id),
    artistNode:{textContent:artist},
    imageNode:{src:'https://image.invalid/new.jpg'}
  };
}
function widget(id,title,artist){
  return {
    querySelector:sel=>{
      if(sel==='a[href*="/track/"]')return link(title,id);
      if(sel==='a[data-testid="context-item-info-artist"]')return {textContent:artist};
      if(sel==='img[data-testid="cover-art-image"]')return {src:'https://image.invalid/current.jpg'};
      return null;
    }
  };
}
test('Spotify legacy native playing bar supplies No track metadata without now-playing-widget',()=>{
  const f=makeFixture({native:native('Correct song','Correct artist',B)});
  const s=f.window.splCurrentTrackSnapshot();
  assert.equal(s.id,B);
  assert.equal(s.name,'Correct song');
  assert.equal(s.artist,'Correct artist');
  assert.equal(s.cover,'https://image.invalid/new.jpg');
  f.window.updMedia();
  assert.equal(f.events.at(-1).track,'Correct song');
  assert.equal(f.events.at(-1).id,B);
});
test('unique legacy link outside the widget still supplies a song title',()=>{
  const f=makeFixture({globalTitle:link('Outside widget',null)});
  const s=f.window.splCurrentTrackSnapshot();
  assert.equal(s.name,'Outside widget');
  assert.equal(s.id,null);
});
test('Media Session supplies a real title when all Spotify DOM selectors are absent',()=>{
  const f=makeFixture({
    session:{title:'Media Session song',artist:'Session artist',album:'Session album',
      artwork:[{src:'https://image.invalid/session.jpg',sizes:'512x512'}]},
    oldId:A
  });
  const s=f.window.splCurrentTrackSnapshot();
  assert.equal(s.id,null,'Old observer id must not be reused without a widget');
  assert.equal(s.name,'Media Session song');
  assert.equal(s.artist,'Session artist');
  assert.equal(s.cover,'https://image.invalid/session.jpg');
  f.window.updMedia();
  assert.equal(f.events.at(-1).track,'Media Session song');
});
test('a verified now-playing track wins over stale Media Session metadata',()=>{
  const f=makeFixture({
    widget:widget(B,'New actual song','New artist'),
    session:{title:'Previous song',artist:'Previous artist'},oldId:A
  });
  const s=f.window.splCurrentTrackSnapshot();
  assert.equal(s.id,B);
  assert.equal(s.name,'New actual song');
  assert.equal(s.artist,'New artist');
  assert.notEqual(s.artist,'Previous artist');
});
test('a widget with a track ID but no name can use metadata for the SAME track',()=>{
  const f=makeFixture({
    widget:widget(B,'',''),
    native:native('Matching native title','Native artist',B),
    oldId:A
  });
  const s=f.window.splCurrentTrackSnapshot();
  assert.equal(s.id,B);
  assert.equal(s.name,'Matching native title');
  assert.equal(s.artist,'Native artist');
});
test('old song cache alone cannot fill No track when the player has no active source',()=>{
  const f=makeFixture({oldId:A});
  assert.equal(f.window.splCurrentTrackSnapshot(),null);
  f.window.updMedia();
  assert.equal(f.events.length,0);
});
test('native link for different song must not mix its artist with current widget',()=>{
  const f=makeFixture({
    widget:widget(B,'New song',''),
    native:native('Old song','Old artist',A)
  });
  const s=f.window.splCurrentTrackSnapshot();
  assert.equal(s.id,B);
  assert.equal(s.name,'New song');
  assert.equal(s.artist,'');
});

test('a single player render exception never stops future metadata refreshes',()=>{
  const player=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/project/lol/webview/injections/SpotilolPlayer.kt'),'utf8');
  const start=player.indexOf('var rafLastTime=0;');
  const end=player.indexOf('var splWasOpen=false;',start);
  assert.ok(start>0&&end>start);
  let frames=[],recovered=0;
  const window={
    __splBusy:false,__splBg:false,
    splUpdate:()=>{throw new Error('Temporary Spotify DOM missing');}
  };
  const ctx=vm.createContext({
    window,pl:{isConnected:true},Date,
    requestAnimationFrame:fn=>frames.push(fn),
    AndBridge:{dbg:()=>{}}
  });
  vm.runInContext(player.slice(start,end)+'\nrafUpdate(500);',ctx);
  assert.equal(frames.length,1,'Next refresh must be scheduled despite render error');
  assert.equal(window.__splPlayerRefreshFailureCount,1);
  window.splUpdate=()=>{recovered++};
  frames.shift()(800);
  assert.equal(recovered,1,'Player must recover on the following frame');
  assert.equal(frames.length,1);
});
