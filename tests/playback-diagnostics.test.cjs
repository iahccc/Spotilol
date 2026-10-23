const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const dir=path.resolve(__dirname,'../app/src/main/java/com/project/lol/webview/injections');
function injection(name){
  const source=fs.readFileSync(path.join(dir,name),'utf8');
  const match=source.match(/const val CONTENT\s*=\s*"""([\s\S]*?)"""/);
  assert.ok(match,name+' script missing');
  return match[1];
}
test('No track diagnostics are available independently of current metadata',()=>{
  let captured=null;
  const window={
    ffDone:true,
    __splBusy:false,
    __splBg:false,
    __splPlayerLastError:'querySelector failed',
    __splPlayerRefreshFailureCount:2,
    splUpdate:()=>{},
    splCurrentTrackSnapshot:()=>null,
    splNativeNowPlayingMeta:()=>null,
    splSessionNowPlayingMeta:()=>null,
    splLooseWidgetMeta:()=>null
  };
  const trackNode={textContent:'No track'};
  const widget={textContent:'',classList:{contains:()=>false},__splReady:true,isConnected:true};
  const document={
    readyState:'complete',visibilityState:'visible',
    getElementById:id=>id==='spl-track'?trackNode:id==='spotilolPlayerControls'?widget:null,
    querySelectorAll:sel=>sel==='[data-testid="now-playing-widget"]'?[]:
      sel==='a[data-testid="context-item-link"]'?[{textContent:'Example song'}]:[],
  };
  const AndBridge={copyPlaybackDiagnostics:json=>{captured=json}};
  vm.runInNewContext(injection('PlaybackDiagnostics.kt'),{
    window,document,AndBridge,Date,JSON,String,Number
  });
  window.splCopyPlaybackDiagnostics();
  assert.ok(captured);
  const obj=JSON.parse(captured);
  assert.equal(obj.type,'spl-player-v1');
  assert.equal(obj.playerTitle,'No track');
  assert.equal(obj.currentSnapshot,null);
  assert.equal(obj.nativeSongLink.firstText,'Example song');
  assert.equal(obj.renderErrorCount,2);
  assert.equal(obj.lastRenderError,'querySelector failed');
  assert.equal(obj.pageVisible,'visible');
});
test('diagnostic payload does not collect auth headers, cookies or URLs',()=>{
  const script=injection('PlaybackDiagnostics.kt');
  assert.ok(!script.includes('document.cookie'));
  assert.ok(!script.includes('localStorage'));
  assert.ok(!script.includes('spotAuthToken'));
  assert.ok(!script.includes('window.location.href'));
  assert.ok(!script.includes('window.fetch('));
});
test('copy control is rendered only when custom player has a missing title',()=>{
  const player=fs.readFileSync(path.join(dir,'SpotilolPlayer.kt'),'utf8');
  assert.ok(player.includes("diagButton.textContent='复制诊断'"));
  assert.ok(player.includes("!t.textContent||t.textContent==='No track'"));
  assert.ok(player.includes('window.splCopyPlaybackDiagnostics()'));
});
