package com.project.lol.webview.injections

object MediaUpdater {
    const val CONTENT = """
            window.splUpCoverUrl = function(url){
                if(!url) return null;
                return url.replace(/ab67616d0000[0-9a-f]{4}/i, 'ab67616d0000b273');
            };

            window.splPlayingTrackId = function(){
                try{
                    var w=document.querySelector('[data-testid="now-playing-widget"]');
                    if(w){
                        var a=w.querySelector('a[href*="/track/"]');
                        if(a){
                            var m=(a.getAttribute('href')||'').match(/\/track\/([a-zA-Z0-9]{22})/);
                            if(m) return m[1];
                        }
                        // The widget is being replaced or currently shows a podcast.
                        // Never reuse the previous track id in this transition.
                        return null;
                    }
                    return window.splTrackId || null;
                }catch(e){ return null; }
            };

            window.splMergeTrackMeta = function(id, patch){
                if(!id || !patch) return null;
                window.__splTrackMeta = window.__splTrackMeta || {};
                var cur = window.__splTrackMeta[id];
                if(!cur){ cur = { uri: 'spotify:track:' + id }; window.__splTrackMeta[id] = cur; }
                for(var k in patch){ if(patch[k] != null && cur[k] == null) cur[k] = patch[k]; }
                return cur;
            };

            window.splWidgetMeta = function(id){
                try{
                    var w = document.querySelector('[data-testid="now-playing-widget"]');
                    if(!w) return null;
                    var a = w.querySelector('a[href*="/track/"]');
                    var m = a ? ((a.getAttribute('href')||'').match(/\/track\/([a-zA-Z0-9]{22})/)) : null;
                    if(!m || m[1] !== id) return null;
                    var out = {};
                    var t = (a.textContent||'').trim();
                    if(t) out.name = t;
                    var al = w.querySelector('a[data-testid="context-item-info-artist"]');
                    if(al){ var s = (al.textContent||'').trim(); if(s) out.artist = s; }
                    var im = w.querySelector('img[data-testid="cover-art-image"]');
                    if(im && im.src) out.cover = window.splUpCoverUrl(im.src);
                    return out;
                }catch(e){ return null; }
            };

            // Spotify has multiple layouts. The now-playing widget may be absent even
            // while music is playing; only trusting its /track/ link causes "No track".
            // Each candidate carries its own title/artist/cover to avoid mixing songs.
            window.splNativeNowPlayingMeta = function(){
                try{
                    var bar=document.querySelector('aside[data-testid="now-playing-bar"], [data-testid="now-playing-bar"]');
                    var title=bar&&bar.querySelector(
                        'a[data-testid="context-item-link"], a[data-testid="context-item-info-title"], [data-testid="context-item-info-title"]');
                    if(!title){
                        // Legacy Spotify layout placed this link outside now-playing-widget.
                        // Only use the document-level fallback if unambiguous.
                        var candidates=document.querySelectorAll('a[data-testid="context-item-link"]');
                        if(candidates.length===1) title=candidates[0];
                    }
                    if(!title) return null;
                    var name=(title.textContent||'').trim();
                    if(!name || name==='No track') return null;
                    var container=bar||(title.closest&&title.closest(
                        '[data-testid="now-playing-widget"], [data-testid="now-playing-bar"], footer, aside'));
                    var get=function(sel){
                        return (container&&container.querySelector(sel)) ||
                            (!bar&&document.querySelectorAll(sel).length===1?document.querySelector(sel):null);
                    };
                    var artistNode=get('a[data-testid="context-item-info-artist"], a[data-testid="context-item-info-show"]');
                    var coverNode=get('img[data-testid="cover-art-image"]');
                    var href=title.getAttribute&&title.getAttribute('href')||'';
                    var match=href.match(/\/track\/([A-Za-z0-9]{22})/);
                    return {id:match?match[1]:null,name:name,
                        artist:(artistNode&&artistNode.textContent||'').trim(),
                        album:'',cover:(coverNode&&coverNode.src)||'',source:'native'};
                }catch(e){return null;}
            };

            window.splSessionNowPlayingMeta = function(){
                try{
                    var session=navigator.mediaSession;
                    var metadata=session&&session.metadata;
                    if(!metadata || !metadata.title) return null;
                    var arts=metadata.artwork||[],cover='';
                    for(var i=arts.length-1;i>=0;i--){
                        if(arts[i]&&/^https?:\/\//i.test(arts[i].src||'')){
                            cover=arts[i].src;break;
                        }
                    }
                    return {id:null,name:String(metadata.title).trim(),
                        artist:String(metadata.artist||'').trim(),
                        album:String(metadata.album||'').trim(),
                        cover:cover,source:'session'};
                }catch(e){return null;}
            };

            window.splLooseWidgetMeta = function(){
                try{
                    var w=document.querySelector('[data-testid="now-playing-widget"]');
                    if(!w || w.querySelector('a[data-testid="context-item-info-show"]')) return null;
                    var a=w.querySelector('a[data-testid="context-item-link"], a[data-testid="context-item-info-title"], [data-testid="context-item-info-title"]');
                    var name=(a&&a.textContent||'').trim();
                    if(!name || name==='No track') return null;
                    var ar=w.querySelector('a[data-testid="context-item-info-artist"]');
                    var im=w.querySelector('img[data-testid="cover-art-image"]');
                    return {id:null,name:name,artist:(ar&&ar.textContent||'').trim(),
                        cover:(im&&im.src)||'',album:'',source:'widget'};
                }catch(e){return null;}
            };

            window.splCurrentTrackSnapshot = function(){
                var id=window.splPlayingTrackId();
                var nativeMeta=window.splNativeNowPlayingMeta();
                var sessionMeta=window.splSessionNowPlayingMeta();
                var loose=window.splLooseWidgetMeta();
                if(!id && nativeMeta&&nativeMeta.id) id=nativeMeta.id;
                var widget=id?window.splWidgetMeta(id):null;
                var cached=(id&&window.__splTrackMeta&&window.__splTrackMeta[id])||null;
                var current=id&&id===window.__curTrackId ? {
                    name:window.__curTrackName,artist:window.__curTrackArtist,
                    album:window.__curTrackAlbum,cover:window.__curTrackCover
                }:null;
                // Do not trust a native link that explicitly identifies a DIFFERENT song.
                if(id && nativeMeta && nativeMeta.id && nativeMeta.id!==id) nativeMeta=null;

                // Prefer sources with a verified track ID. The Media Session supplies
                // the current song when Spotify hides or reworks its DOM.
                var main=(widget&&widget.name&&widget) ||
                    (cached&&cached.name&&cached) ||
                    (sessionMeta&&sessionMeta.name&&sessionMeta) ||
                    (nativeMeta&&nativeMeta.name&&nativeMeta) ||
                    (loose&&loose.name&&loose) ||
                    (current&&current.name&&current);
                if(!main || !main.name) return null;
                var name=String(main.name).trim(),artistName=String(main.artist||'').trim();
                var album=String(main.album||'').trim(),cover=String(main.cover||'').trim();
                // Enrich only from this exact track or from a source with matching title.
                var candidates=[widget,cached,current,nativeMeta,sessionMeta,loose];
                for(var i=0;i<candidates.length;i++){
                    var v=candidates[i];
                    if(!v || !v.name || String(v.name).trim()!==name) continue;
                    if(id && v.id && v.id!==id) continue;
                    if(!artistName&&v.artist) artistName=String(v.artist).trim();
                    if(!album&&v.album) album=String(v.album).trim();
                    if(!cover&&v.cover) cover=String(v.cover).trim();
                }
                var key=id||'visible:'+name+'|'+artistName;
                window.__splMediaSource=main.source||(main===cached?'cache':'id');
                return {id:id||null,key:key,name:name,artist:artistName,album:album,cover:cover};
            };

            window.splRowMeta = function(id){
                try{
                    var onAlbumPage = location.pathname.indexOf('/album/') !== -1;
                    var rows = document.querySelectorAll('div[data-testid="tracklist-row"]');
                    for(var i=0;i<rows.length;i++){
                        var row = rows[i];
                        if(row.closest && row.closest('.playlistRecommenderContainer, [data-testid="recommended-track"]')) continue;
                        var link = row.querySelector('a[data-testid="internal-track-link"]') || row.querySelector('a[href*="/track/"]');
                        if(!link) continue;
                        if((link.getAttribute('href')||'').indexOf(id) === -1) continue;
                        var out = {};
                        var t = (link.textContent||'').trim();
                        if(t) out.name = t;
                        var arts = [];
                        var as = row.querySelectorAll('a[href*="/artist/"]');
                        for(var j=0;j<as.length;j++){
                            var nm = (as[j].textContent||'').trim();
                            if(nm && arts.indexOf(nm) === -1) arts.push(nm);
                        }
                        if(arts.length) out.artist = arts.join(', ');
                        var al = row.querySelector('a[href*="/album/"]');
                        if(al){ var an = (al.textContent||'').trim(); if(an) out.album = an; }
                        var img = row.querySelector('img');
                        if(img && img.src) out.cover = window.splUpCoverUrl(img.src);
                        if(!out.album && onAlbumPage){
                            var h1 = document.querySelector('main h1');
                            if(h1){ var hn = (h1.textContent||'').trim(); if(hn) out.album = hn; }
                            if(!out.cover){
                                var og = document.querySelector('meta[property="og:image"]');
                                if(og){ var oc = og.getAttribute('content'); if(oc) out.cover = window.splUpCoverUrl(oc); }
                            }
                        }
                        return out;
                    }
                }catch(e){}
                return null;
            };

            window.splEnsureTrackMeta = function(id){
                if(!id) return Promise.resolve();
                window.__splMetaRuns = window.__splMetaRuns || {};
                window.__splTrackMeta = window.__splTrackMeta || {};
                var existing = window.__splTrackMeta[id] || null;
                if(existing && existing.album != null) return Promise.resolve();
                if(window.__splMetaRuns[id]) return window.__splMetaRuns[id];

                var sigOf = function(m){ return m ? [(m.name||''),(m.artist||''),(m.album||''),(m.cover||'')].join('|') : ''; };
                var lastSig = sigOf(existing);

                var promote = function(){
                    if(window.splPlayingTrackId() !== id) return;
                    var m = window.__splTrackMeta && window.__splTrackMeta[id];
                    var s = sigOf(m);
                    if(s === lastSig) return;
                    lastSig = s;
                    window.__curTrackId = id;
                    window.__curTrackUri = 'spotify:track:' + id;
                    if(m && m.name) window.__curTrackName = m.name;
                    if(m && m.artist) window.__curTrackArtist = m.artist;
                    if(m && m.album) window.__curTrackAlbum = m.album;
                    if(m && m.cover) window.__curTrackCover = m.cover;
                    window.lastState = null;
                    if(window.updMedia && typeof track !== 'undefined'){ try{ window.updMedia(); }catch(e){} }
                };

                var attempt = function(n, resolve){
                    try{
                        var wm = window.splWidgetMeta(id);
                        if(wm) window.splMergeTrackMeta(id, wm);
                        var rm = window.splRowMeta(id);
                        if(rm) window.splMergeTrackMeta(id, rm);
                    }catch(e){}
                    promote();
                    var m = window.__splTrackMeta && window.__splTrackMeta[id];
                    if((m && m.album != null) || n >= 3){
                        delete window.__splMetaRuns[id];
                        resolve();
                        return;
                    }
                    setTimeout(function(){ attempt(n + 1, resolve); }, [400, 600, 800][n]);
                };

                var p = new Promise(function(resolve){ attempt(0, resolve); });
                window.__splMetaRuns[id] = p;
                return p;
            };

            window.updMedia = function(){
                var id = (typeof window.splPlayingTrackId === 'function')
                    ? window.splPlayingTrackId()
                    : (window.splTrackId || window.__curTrackId || null);
                var snap=typeof window.splCurrentTrackSnapshot==='function' ? window.splCurrentTrackSnapshot() : null;
                var widget=document.querySelector('[data-testid="now-playing-widget"]');
                var isEpisode=!!(widget && widget.querySelector('a[data-testid="context-item-info-show"]'));
                // Ignore transient DOM states instead of sending old fields with a new id.
                if(!snap && !isEpisode) return;
                var name=snap ? snap.name : (track || '');
                var artistName=snap ? snap.artist : (artist || '');
                var album=snap ? snap.album : '';
                var artwork=snap ? snap.cover : (cover || '');
                var mediaId=snap ? (snap.key || snap.id) : 'episode';
                var restart=(typeof position==='number' && typeof lastPos==='number' && position<lastPos-2000);
                lastPos=position;
                if(restart) lastState=null;
                var currState=[mediaId,name,artistName,playing,repmode,isfav,shuffle,album,duration,artwork].join('|');
                if(currState!==lastState){
                    lastState=currState;
                    var values={id:mediaId,artist:artistName,track:name,album:album,
                        playing:playing,repeat:repmode,fav:isfav,shuffle:shuffle,
                        duration:duration,position:position,cover:artwork};
                    AndBridge.recMediaStatus(JSON.stringify(values));
                }else{
                    AndBridge.recMediaPosition(position);
                }
            };

            window.__splEnsureTrackMetaSafe = function(id){
                try{
                    var p = window.splEnsureTrackMeta(id);
                    if(p && typeof p.catch === 'function'){
                        p.catch(function(e){
                            try{ AndBridge.dbg('e','meta ensure failed: '+(e&&e.message||e)); }catch(_){}
                        });
                    }
                    return p;
                }catch(e){ return null; }
            };

            try {
                if(typeof window.splOnTrackChange === 'function'){
                    window.splOnTrackChange(function(uri, id){
                        if(!id) return;
                        if(window.__curTrackId === id){
                            window.__splEnsureTrackMetaSafe(id);
                            return;
                        }
                        var m = (window.__splTrackMeta && window.__splTrackMeta[id]) || null;
                        window.__curTrackId = id;
                        window.__curTrackUri = uri;
                        window.__curTrackName = m ? m.name : null;
                        window.__curTrackArtist = m ? m.artist : null;
                        window.__curTrackAlbum = m ? m.album : null;
                        window.__curTrackCover = m ? m.cover : null;
                        window.lastState = null;
                        window.__splEnsureTrackMetaSafe(id);
                        if(typeof track !== 'undefined'){ try{ window.updMedia(); }catch(e){} }
                    });
                }
            } catch(e){}
    """
}