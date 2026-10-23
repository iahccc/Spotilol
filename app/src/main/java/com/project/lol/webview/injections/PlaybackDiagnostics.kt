package com.project.lol.webview.injections

/**
 * A local, user-triggered diagnostic snapshot for "No track".
 * Never collects cookies, tokens, request headers, URLs, account identifiers or DOM HTML.
 */
object PlaybackDiagnostics {
    const val CONTENT = """
        (function(){
            if(window.splCollectPlaybackDiagnostics) return;
            window.splCollectPlaybackDiagnostics=function(){
                function shorten(value){
                    return String(value==null?'':value).trim().slice(0,100);
                }
                function safe(fn){
                    try{return fn();}catch(e){return 'error:'+shorten(e&&e.message||e);}
                }
                function query(sel){
                    return safe(function(){
                        var nodes=document.querySelectorAll(sel);
                        return {count:nodes.length,
                            firstText:nodes.length?shorten(nodes[0].textContent):''};
                    });
                }
                function info(fn){
                    return safe(function(){
                        if(typeof fn!=='function') return null;
                        var v=fn();
                        if(!v) return null;
                        return {
                            hasId:!!v.id,
                            name:shorten(v.name),
                            artist:shorten(v.artist),
                            hasCover:!!v.cover,
                            source:shorten(v.source),
                            keyType:v.key&&String(v.key).indexOf('visible:')===0?'visible':'id'
                        };
                    });
                }
                var player=document.getElementById('spotilolPlayerControls');
                return {
                    type:'spl-player-v1',
                    appVersion:'1.2.15',
                    createdAt:new Date().toISOString(),
                    pageReady:document.readyState,
                    pageVisible:document.visibilityState,
                    jsBusy:!!window.__splBusy,
                    jsBackground:!!window.__splBg,
                    trackerStarted:!!window.ffDone,
                    playerFound:!!player,
                    playerReady:!!(player&&player.__splReady),
                    playerConnected:!!(player&&player.isConnected),
                    playerTitle:shorten((document.getElementById('spl-track')||{}).textContent),
                    playerArtist:shorten((document.getElementById('spl-artist')||{}).textContent),
                    playerHidden:!!(player&&player.classList.contains('spl-empty')),
                    updateDefined:typeof window.splUpdate==='function',
                    snapshotDefined:typeof window.splCurrentTrackSnapshot==='function',
                    playing:safe(function(){return window.splIsPlaying&&window.splIsPlaying();}),
                    lastRenderError:shorten(window.__splPlayerLastError),
                    renderErrorCount:Number(window.__splPlayerRefreshFailureCount)||0,
                    mediaSource:shorten(window.__splMediaSource),
                    hasObserverTrackId:!!window.splTrackId,
                    hasMetadataTrackId:!!window.__curTrackId,
                    cachedMetaCount:safe(function(){return Object.keys(window.__splTrackMeta||{}).length;}),
                    currentSnapshot:info(window.splCurrentTrackSnapshot),
                    nativeMetadata:info(window.splNativeNowPlayingMeta),
                    mediaSessionMetadata:info(window.splSessionNowPlayingMeta),
                    looseWidgetMetadata:info(window.splLooseWidgetMeta),
                    nativeBar:query('aside[data-testid="now-playing-bar"], [data-testid="now-playing-bar"]'),
                    widget:query('[data-testid="now-playing-widget"]'),
                    nativeSongLink:query('a[data-testid="context-item-link"]'),
                    nativeSongTitle:query('[data-testid="context-item-info-title"]'),
                    artistLink:query('a[data-testid="context-item-info-artist"]'),
                    anyTrackLink:query('[data-testid="now-playing-widget"] a[href*="/track/"]'),
                    progressBar:query('[data-testid="playback-progressbar"]'),
                    audioCount:safe(function(){return document.querySelectorAll('audio').length;}),
                    videoCount:safe(function(){return document.querySelectorAll('video').length;})
                };
            };
            window.splCopyPlaybackDiagnostics=function(){
                try{
                    var report=JSON.stringify(window.splCollectPlaybackDiagnostics());
                    AndBridge.copyPlaybackDiagnostics(report);
                }catch(e){
                    try{AndBridge.deferMessage('Diagnostic copy failed');}catch(_){}
                }
            };
        })();
    """
}
