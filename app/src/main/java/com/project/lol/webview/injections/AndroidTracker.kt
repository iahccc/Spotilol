package com.project.lol.webview.injections

object AndroidTracker {
    const val CONTENT = """
            window.addAndAuto = function(){
                if(aaint) {
                    if(typeof aaint.disconnect==='function') { try { aaint.disconnect(); } catch(e){} }
                    else { clearInterval(aaint); }
                }
                function readTrackState(){
                    var id = typeof window.splPlayingTrackId==='function' ? window.splPlayingTrackId() : window.splTrackId;
                    var meta = id && window.__splTrackMeta ? window.__splTrackMeta[id] : null;
                    var widget = id && typeof window.splWidgetMeta==='function' ? window.splWidgetMeta(id) : null;
                    var ta = document.querySelector('a[data-testid=context-item-link], [data-testid=now-playing-widget] a[href*="/track/"]');
                    track = ((ta && ta.textContent || '').trim() || (widget && widget.name) ||
                        (meta && meta.name) || (id && id===window.__curTrackId && window.__curTrackName) || '') || null;
                    var aa = document.querySelector('a[data-testid=context-item-info-artist]');
                    if(!aa) aa = document.querySelector('a[data-testid=context-item-info-show]');
                    artist = ((aa && aa.textContent || '').trim() || (widget && widget.artist) ||
                        (meta && meta.artist) || (id && id===window.__curTrackId && window.__curTrackArtist) || '');
                    var rr = document.querySelector('button[data-testid=control-button-repeat]');
                    if(rr) repmode=rr.getAttribute('aria-checked'); else repmode='false';
                    shuffle = (typeof window.splShuffleState === 'function') ? window.splShuffleState() : 'off';
                    var fb = document.querySelector('div[data-testid=now-playing-widget]>div:last-child>button');
                    if(fb && fb.getAttribute('aria-checked')==='true') isfav=true; else isfav=false;
                    playing=window.splIsPlayingSticky();
                    var rg = document.querySelector('div[data-testid=playback-progressbar] input[type=range]');
                    if(rg) { duration=parseInt(rg.getAttribute('max')); position=parseInt(rg.getAttribute('value')); }
                    else { duration=null; position=null; }
                    var im = document.querySelector('img[data-testid=cover-art-image]');
                    if(im) {
                        var s=im.src;
                        if(s.indexOf('i.scdn.co')!==-1) {
                            s=s.replace(/ab67616d0000[0-9a-f]{4}/,'ab67616d000082c1');
                            s=s.replace(/ab6761670000[0-9a-f]{4}/,'ab676167000082e8');
                        }
                        cover=s;
                    } else cover=null;
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
