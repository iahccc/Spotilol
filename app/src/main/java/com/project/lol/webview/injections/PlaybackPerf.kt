package com.project.lol.webview.injections

/** Lightweight, bounded playback responsiveness diagnostics (no URLs, tokens or track names). */
object PlaybackPerf {
    const val CONTENT = """
        (function(){
            if(window.__splPerf) return;
            var last=Object.create(null),lastClick=0;
            var samples=[];
            window.__splPerfEvents=samples;
            window.__splPerf=function(event,elapsed){
                var ms=Math.max(0,Math.min(120000,Number(elapsed)||0)),now=Date.now();
                if(event==='long-task'&&ms<50) return;
                if(event==='event-loop-lag'&&ms<250) return;
                if(event!=='play-click' && event!=='play-state' &&
                   last[event] && now-last[event]<1800) return;
                last[event]=now;
                samples.push({type:event,ms:Math.round(ms),at:now});
                if(samples.length>80) samples.shift();
                try{ AndBridge.playbackPerf(event,ms); }catch(e){}
            };
            document.addEventListener('click',function(e){
                var t=e.target;
                if(!t||typeof t.closest!=='function') return;
                if(t.closest('[data-testid="tracklist-row"],[data-testid="play-button"],[data-testid="play-button-container"]')){
                    lastClick=performance.now();
                    window.__splPerf('play-click',0);
                }
            },true);
            try{
                if(typeof PerformanceObserver==='function'){
                    var po=new PerformanceObserver(function(items){
                        items.getEntries().forEach(function(entry){
                            window.__splPerf('long-task',entry.duration);
                        });
                    });
                    po.observe({entryTypes:['longtask']});
                }
            }catch(e){}
            var prev=performance.now();
            setInterval(function(){
                var now=performance.now(),delta=now-prev;
                prev=now;
                if(!document.hidden && delta>650) window.__splPerf('event-loop-lag',delta-300);
            },300);
            document.addEventListener('visibilitychange',function(){prev=performance.now();});
            setTimeout(function(){
                if(typeof window.splOnTrackChange==='function'){
                    window.splOnTrackChange(function(){
                        if(lastClick){
                            window.__splPerf('play-state',performance.now()-lastClick);
                            lastClick=0;
                        }
                    });
                }
            },0);
        })();
    """
}
