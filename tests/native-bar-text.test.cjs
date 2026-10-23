const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');

const source=fs.readFileSync(path.join(__dirname,
  '../app/src/main/java/com/project/lol/webview/injections/MediaUpdater.kt'),'utf8');
const match=source.match(/const val CONTENT\s*=\s*"""([\s\S]*?)"""/);
assert.ok(match,'MediaUpdater JS injection missing');

const A='AAAAAAAAAAAAAAAAAAAAAA',B='BBBBBBBBBBBBBBBBBBBBBB';
function fixture({tokens=[],cached={},nativeText=null,innerText=null,link=null,playing=true}={}){
  const events=[];
  const bar={
    textContent:nativeText===null?tokens.join(''):nativeText,
    innerText:innerText===null?tokens.join('\n'):innerText,
    querySelector:sel=>{
      if(link && /context-item-link|\/track\//.test(sel))return link;
      if(link && /context-item-info-artist|\/artist\//.test(sel))
        return {textContent:link.artist};
      return null;
    }
  };
  const nodeParents=tokens.map(()=>({
    tagName:'SPAN',parentElement:bar,
    getAttribute:()=>null
  }));
  const entries=tokens.map((s,i)=>({
    nodeValue:s,parentElement:nodeParents[i]
  }));
  const document={
    querySelector:sel=>sel.includes('now-playing-bar')?bar:null,
    querySelectorAll:()=>[],
    createTreeWalker:(root,what)=>{
      assert.equal(root,bar);
      assert.equal(what,4);
      let i=0;
      return {nextNode:()=>entries[i++]||null};
    }
  };
  const window={
    __splTrackMeta:cached,splTrackId:null,__curTrackId:null
  };
  const ctx=vm.createContext({
    document,window,navigator:{mediaSession:null},
    location:{pathname:'/playlist/test'},
    Date,JSON,Promise,setTimeout:()=>{},
    track:null,artist:'',cover:null,playing,repmode:'false',
    shuffle:'off',isfav:false,duration:292000,position:1000,
    lastPos:null,lastState:null,
    AndBridge:{
      recMediaStatus:s=>events.push(JSON.parse(s)),
      recMediaPosition:()=>{}
    }
  });
  vm.runInContext(match[1],ctx);
  return {window,events,bar,ctx};
}

test('real device: anonymous native text nodes expose 山川 and 李榮浩 without any data-testid',()=>{
  const f=fixture({
    tokens:['山川','李榮浩','0:01','调整播放进度','4:52','调整音量'],
    nativeText:'山川李榮浩0:01调整播放进度4:52调整音量'
  });
  const s=f.window.splCurrentTrackSnapshot();
  assert.ok(s);
  assert.equal(s.name,'山川');
  assert.equal(s.artist,'李榮浩');
  assert.equal(s.id,null);
  f.window.updMedia();
  assert.equal(f.events.at(-1).track,'山川');
  assert.equal(f.events.at(-1).artist,'李榮浩');
});

test('single fused native text node matches cached title and artist before any playback controls',()=>{
  const f=fixture({
    tokens:['山川李榮浩0:01调整播放进度4:52调整音量'],
    cached:{[A]:{name:'山川',artist:'李榮浩',album:'Album',cover:'https://img.example/current.jpg'}},
    innerText:'山川李榮浩0:01调整播放进度4:52调整音量'
  });
  const s=f.window.splCurrentTrackSnapshot();
  assert.equal(s.id,A);
  assert.equal(s.name,'山川');
  assert.equal(s.artist,'李榮浩');
  assert.equal(s.album,'Album');
  assert.equal(s.cover,'https://img.example/current.jpg');
});

test('native progress and volume text alone do not create a fake song',()=>{
  const f=fixture({tokens:['0:01','调整播放进度','4:52','调整音量'],
    cached:{[A]:{name:'Previous song',artist:'Previous artist'}}});
  assert.equal(f.window.splCurrentTrackSnapshot(),null);
  f.window.updMedia();
  assert.equal(f.events.length,0);
});

test('fast switching to another anonymous native-bar song updates title and artist',()=>{
  const f=fixture({tokens:['山川','李榮浩','0:01']});
  f.window.updMedia();
  assert.equal(f.events.at(-1).track,'山川');
  f.bar.textContent='新歌新歌手0:12';
  f.bar.innerText='新歌\n新歌手\n0:12';
  f.ctx.document.createTreeWalker=()=>{
    const parts=['新歌','新歌手','0:12'].map(s=>({
      nodeValue:s,
      parentElement:{tagName:'SPAN',parentElement:f.bar,getAttribute:()=>null}
    }));
    let i=0;return {nextNode:()=>parts[i++]||null};
  };
  f.window.updMedia();
  assert.equal(f.events.at(-1).track,'新歌');
  assert.equal(f.events.at(-1).artist,'新歌手');
});

test('a stale cached song is not used when the native bar now displays a different song',()=>{
  const f=fixture({
    tokens:['Current track','New artist','0:01'],
    cached:{[A]:{name:'Stale track',artist:'Old artist'}}
  });
  const s=f.window.splCurrentTrackSnapshot();
  assert.equal(s.name,'Current track');
  assert.equal(s.artist,'New artist');
  assert.equal(s.id,null);
});

test('metadata cached for a different artist must not override the current native bar artist',()=>{
  const f=fixture({
    tokens:['山川','李榮浩','0:01'],
    cached:{[A]:{name:'山川',artist:'Different artist'}}
  });
  const s=f.window.splCurrentTrackSnapshot();
  assert.equal(s.name,'山川');
  assert.equal(s.artist,'李榮浩');
  assert.equal(s.id,null);
});

test('a cached substring of the visible title cannot claim a different song ID',()=>{
  const f=fixture({
    tokens:['山川','李榮浩','0:01'],
    cached:{[A]:{name:'川',artist:'李榮浩'}}
  });
  const s=f.window.splCurrentTrackSnapshot();
  assert.equal(s.name,'山川');
  assert.equal(s.artist,'李榮浩');
  assert.equal(s.id,null);
});

test('old native title links still work after adding the anonymous-text fallback',()=>{
  const a={
    textContent:'Legacy song',
    artist:'Legacy artist',
    getAttribute:k=>k==='href'?'/track/'+B:null
  };
  const f=fixture({link:a,tokens:[]});
  const s=f.window.splCurrentTrackSnapshot();
  assert.equal(s.id,B);
  assert.equal(s.name,'Legacy song');
  assert.equal(s.artist,'Legacy artist');
});
