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
                        'a[data-testid="context-item-link"], a[data-testid="context-item-info-title"], [data-testid="context-item-info-title"], a[href*="/track/"]');
                    if(!title && !bar){
                        // Older Spotify layouts placed the link outside the native bar.
                        var matches=document.querySelectorAll('a[data-testid="context-item-link"]');
                        if(matches.length===1) title=matches[0];
                    }
                    if(!title && !bar) return null;
                    var container=bar||(title.closest&&title.closest(
                        '[data-testid="now-playing-widget"], [data-testid="now-playing-bar"], footer, aside'));
                    var name=(title&&title.textContent||'').trim();
                    var artistNode=container&&container.querySelector(
                        'a[data-testid="context-item-info-artist"], a[href*="/artist/"], a[data-testid="context-item-info-show"]');
                    var artistName=(artistNode&&artistNode.textContent||'').trim();

                    if(bar && (!name || !artistName)){
                        // Spotify's new native bar may use anonymous div/span text,
                        // with no song link, now-playing-widget or stable data-testid.
                        var labels=[];
                        if(typeof document.createTreeWalker==='function'){
                            var walker=document.createTreeWalker(bar,4),node,steps=0;
                            while((node=walker.nextNode()) && ++steps<=180 && labels.length<6){
                                var parent=node.parentElement,skip=false;
                                for(var e=parent;e&&e!==bar;e=e.parentElement){
                                    var tag=(e.tagName||'').toLowerCase();
                                    if(tag==='button'||tag==='input'||tag==='svg'||tag==='script'||
                                       tag==='style'||tag==='select'||tag==='textarea'||
                                       e.getAttribute&&(
                                           e.getAttribute('role')==='slider' ||
                                           e.getAttribute('role')==='progressbar' ||
                                           /progress|volume|seek/i.test(e.getAttribute('data-testid')||''))){
                                        skip=true;break;
                                    }
                                }
                                if(skip) continue;
                                var label=(node.nodeValue||'').trim();
                                if(!label || label.length>100 ||
                                   /^\d{1,3}:\d{2}(?::\d{2})?$/.test(label) ||
                                   /^(?:调整播放进度|调整音量|播放进度|音量|播放|暂停|下一首|上一首|播放队列|播放列表)$/i.test(label)) continue;
                                labels.push(label);
                            }
                        }
                        if(!name && labels.length) name=labels[0];
                        if(!artistName && labels.length>1){
                            artistName=labels[labels[0]===name?1:0]||'';
                        }
                        // innerText has line separators even when a browser cannot
                        // expose native bar text nodes through a TreeWalker.
                        if(!name && typeof bar.innerText==='string'){
                            var pieces=bar.innerText.split(/\n+/).map(function(s){return s.trim();}).filter(function(s){
                                return s && s.length<=100 && !/^\d{1,3}:\d{2}(?::\d{2})?$/.test(s) &&
                                    !/^(?:调整播放进度|调整音量|播放进度|音量|播放|暂停|下一首|上一首|播放队列|播放列表)$/i.test(s);
                            });
                            if(pieces.length>1){
                                name=pieces[0];
                                if(!artistName) artistName=pieces[1];
                            }
                        }
                    }

                    var href=title&&title.getAttribute&&title.getAttribute('href')||'';
                    var match=href.match(/\/track\/([A-Za-z0-9]{22})/);
                    var id=match?match[1]:null;
                    var coverNode=container&&container.querySelector(
                        'img[data-testid="cover-art-image"], img[src]');
                    var artwork=coverNode&&coverNode.src||'';

                    if(bar){
                        // The real device reports "山川李榮浩0:01..." as one
                        // textContent value. Match name AND artist contiguously
                        // against cached metadata rather than treating that whole
                        // string (including controls) as a song title.
                        var visible=(bar.textContent||'').replace(/\s+/g,'');
                        var cache=window.__splTrackMeta||{},keys=Object.keys(cache);
                        var best=null,bestScore=0;
                        for(var i=0;i<keys.length&&i<500;i++){
                            var meta=cache[keys[i]]||{};
                            var tn=String(meta.name||'').replace(/\s+/g,'');
                            var an=String(meta.artist||'').replace(/\s+/g,'');
                            if(!tn||!an) continue;
                            var index=visible.indexOf(tn);
                            if(index<0 || index>40) continue;
                            var tail=visible.slice(index+tn.length,index+tn.length+an.length+10);
                            var artistPos=tail.indexOf(an);
                            if(artistPos<0 || artistPos>10) continue;
                            var score=tn.length+an.length-artistPos;
                            if(score>bestScore){
                                bestScore=score;
                                best={id:keys[i],name:meta.name,artist:meta.artist,
                                    album:meta.album||'',cover:meta.cover||''};
                            }
                        }
                        if(best && (!id || id===best.id)){
                            // Use cached song ID only when BOTH title and artist
                            // are confirmed against the currently visible native bar.
                            // With no link, the DOM may contain one fused text node
                            // ("titleartist0:01..."); prefer the verified pair.
                            if(!id) id=best.id;
                            if(!title){
                                name=best.name;
                                artistName=best.artist;
                            }else{
                                if(!name) name=best.name;
                                if(!artistName) artistName=best.artist;
                            }
                            if(!artwork) artwork=best.cover;
                        }
                    }
                    if(!name || name==='No track') return null;
                    return {id:id,name:name,artist:artistName,album:'',cover:artwork,
                        source:title?'native-link':(bar?'native-bar-text':'native')};
                }catch(e){
                    window.__splNativeMetaError=String(e&&e.message||e).slice(0,120);
                    return null;
                }
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
                var activeWidget=document.querySelector('[data-testid="now-playing-widget"]');
                // An observer may still remember the PREVIOUS ID after the widget was removed.
                // Only accept its ID when the widget exists, or from a visible native link.
                var id=activeWidget?window.splPlayingTrackId():null;
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
                    // A native link identifying the verified track is more reliable
                    // than a Media Session that may still describe the previous song.
                    (nativeMeta&&nativeMeta.id===id&&nativeMeta.name&&nativeMeta) ||
                    (loose&&loose.name&&loose) ||
                    (sessionMeta&&sessionMeta.name&&sessionMeta) ||
                    (nativeMeta&&nativeMeta.name&&nativeMeta) ||
                    (current&&current.name&&current);
                if(!main || !main.name){
                    // A new verified track ID is already known, but its metadata is still
                    // loading: clear the old song without inventing a title.
                    if(!id) return null;
                    window.__splMediaSource='pending-id';
                    return {id:id,key:id,name:'',artist:'',album:'',cover:''};
                }
                var name=String(main.name).trim(),artistName=String(main.artist||'').trim();
                var album=String(main.album||'').trim(),cover=String(main.cover||'').trim();
                // Enrich only from this exact track or from a source with matching title.
                var candidates=[widget,cached,current,nativeMeta,sessionMeta,loose];
                for(var i=0;i<candidates.length;i++){
                    var v=candidates[i];
                    if(!v) continue;
                    // Metadata cached under the verified ID belongs to this track,
                    // even if it contains an album but not yet a title.
                    var sameTrack=(id && (v===cached || v===current || v===widget));
                    if(!sameTrack && (!v.name || String(v.name).trim()!==name)) continue;
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