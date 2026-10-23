package com.project.lol.webview.injections

object AndroidTracker {
    const val CONTENT = """
            window.addAndAuto = function(){
                if(aaint) {
                    if(typeof aaint.disconnect==='function') { try { aaint.disconnect(); } catch(e){} }
                    else { clearInterval(aaint); }
                }
                function readTrackState(){
                    var snapshot=typeof window.splCurrentTrackSnapshot==='function'
                        ?window.splCurrentTrackSnapshot():null;
                    var widget=document.querySelector('[data-testid="now-playing-widget"]');
                    var episode=widget&&widget.querySelector('a[data-testid="context-item-info-show"]');
                    // A missing track link during a React update does not mean the old song
                    // is still playing. Wait for a coherent snapshot instead of mixing fields.
                    if(!snapshot&&!episode) return;
                    if(snapshot){
                        window.__splTrackedId=snapshot.id;
                        track=snapshot.name || null;
                        artist=snapshot.artist || '';
                        cover=snapshot.cover || null;
                    }else{
                        window.__splTrackedId='episode';
                        var ta=widget.querySelector('a[data-testid="context-item-info-title"], a[data-testid="context-item-link"]');
                        track=ta?(ta.textContent||'').trim():null;
                        artist=(episode.textContent||'').trim();
                        var image=widget.querySelector('img[data-testid="cover-art-image"]');
                        cover=image&&image.src||null;
                    }
                    var rr = document.querySelector('button[data-testid=control-button-repeat]');
                    if(rr) repmode=rr.getAttribute('aria-checked'); else repmode='false';
                    shuffle = (typeof window.splShuffleState === 'function') ? window.splShuffleState() : 'off';
                    var fb = document.querySelector('div[data-testid=now-playing-widget]>div:last-child>button');
                    if(fb && fb.getAttribute('aria-checked')==='true') isfav=true; else isfav=false;
                    playing=window.splIsPlayingSticky();
                    var rg = document.querySelector('div[data-testid=playback-progressbar] input[type=range]');
                    if(rg) { duration=parseInt(rg.getAttribute('max')); position=parseInt(rg.getAttribute('value')); }
                    else { duration=null; position=null; }
                    updMedia();
                }
                try {
                    var npTarget = document.querySelector('aside[data-testid="now-playing-bar"]') || document.body;
                    var readTimeout;
                    var obs = new MutationObserver(function(){
                        clearTimeout(readTimeout);
                        readTimeout = setTimeout(readTrackState, 200);
                    });
                    obs.observe(npTarget, { childList: true, subtree: true, attributes: true, characterData: true });
                    aaint = obs;
                } catch(e) {
                    aaint = null;
                }
                // Native now-playing DOM may stop mutating after a Web Player update.
                // Poll at low frequency so notification state recovers without relying on the native button.
                if(window.__splStatusWatch) clearInterval(window.__splStatusWatch);
                window.__splStatusWatch = setInterval(function(){
                    try { readTrackState(); } catch(e){}
                }, 1500);
                if(window.__splStatusOff) { try { window.__splStatusOff(); } catch(e){} }
                if(typeof window.splOnTrackChange === 'function'){
                    window.__splStatusOff = window.splOnTrackChange(function(){
                        setTimeout(function(){ try { readTrackState(); } catch(e){} }, 0);
                    });
                }
                readTrackState();
            };
        
    """
}
