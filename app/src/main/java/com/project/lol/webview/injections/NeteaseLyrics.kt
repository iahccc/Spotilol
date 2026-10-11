package com.project.lol.webview.injections

/*
 * Synchronized lyrics for the Spotilol player.
 *
 * Inspired by Hyun's "YouTube Music / Spotify Netease lyrics" userscript
 * (MIT licensed). This implementation uses the public Web API endpoints and
 * intentionally does not include the userscript's EAPI, CryptoJS, cookie jar,
 * translation, or romanization code.
 */
object NeteaseLyrics {
    const val CONTENT = """
        (function(){
            if(window.__spotilolNeteaseLyrics) return;

            var SEARCH_URL='https://music.163.com/api/search/get/web';
            var LYRIC_URL='https://music.163.com/api/song/lyric';
            var EMPTY_CACHE_MS=120000;
            var ERROR_RETRY_MS=30000;
            var MAX_LYRIC_CANDIDATES=5;
            var state={
                player:null,
                button:null,
                panel:null,
                scroller:null,
                list:null,
                lineElements:[],
                open:false,
                panelVisible:false,
                manualBrowse:false,
                manualTimer:null,
                modeActive:false,
                trackKey:'',
                lines:[],
                viewKind:'idle',
                activeIndex:-2,
                generation:0,
                cache:Object.create(null),
                retryAt:0,
                mountQueued:false
            };
            window.__spotilolNeteaseLyrics=state;

            function addStyles(){
                if(document.getElementById('spotilol-netease-lyrics-style')) return;
                var style=document.createElement('style');
                style.id='spotilol-netease-lyrics-style';
                style.textContent=''
                    +'#spotilolPlayerControls #spl-lyrics{display:flex!important;pointer-events:auto!important;opacity:1!important}'
                    +'#spotilolPlayerControls.spl-lyrics-open:not(.spl-mini){top:8px;bottom:max(8px,env(safe-area-inset-bottom));max-height:none;overflow:hidden}'
                    +'#spotilolPlayerControls.spl-lyrics-open:not(.spl-mini)>.spl-top,#spotilolPlayerControls.spl-lyrics-open:not(.spl-mini)>.spl-row2,#spotilolPlayerControls.spl-lyrics-open:not(.spl-mini)>.spl-bottom,#spotilolPlayerControls.spl-lyrics-open:not(.spl-mini)>.spl-transport{flex-shrink:0}'
                    +'#spotilolPlayerControls .spl-netease-lyrics{box-sizing:border-box;position:relative;flex:0 0 0;width:100%;min-height:0;max-height:0;opacity:0;overflow:hidden;margin:0;border-radius:12px;background:rgba(255,255,255,.035);transition:opacity .2s,margin .28s;pointer-events:none}'
                    +'#spotilolPlayerControls.spl-lyrics-open:not(.spl-mini) .spl-netease-lyrics{flex:1 1 auto;min-height:72px;max-height:none;opacity:1;margin:0 0 7px;pointer-events:auto}'
                    +'#spotilolPlayerControls .spl-netease-lyrics:before,#spotilolPlayerControls .spl-netease-lyrics:after{content:"";position:absolute;z-index:2;left:0;right:0;height:24px;pointer-events:none}'
                    +'#spotilolPlayerControls .spl-netease-lyrics:before{top:0;background:linear-gradient(to bottom,rgba(25,25,25,.96),rgba(25,25,25,0))}'
                    +'#spotilolPlayerControls .spl-netease-lyrics:after{bottom:0;background:linear-gradient(to top,rgba(25,25,25,.96),rgba(25,25,25,0))}'
                    +'#spotilolPlayerControls .spl-netease-scroll{box-sizing:border-box;width:100%;height:100%;min-height:0;overflow-x:hidden;overflow-y:auto;overscroll-behavior-y:contain;touch-action:pan-y;-webkit-overflow-scrolling:touch;scrollbar-width:thin;scrollbar-color:rgba(255,255,255,.2) transparent}'
                    +'#spotilolPlayerControls .spl-netease-scroll::-webkit-scrollbar{width:3px}'
                    +'#spotilolPlayerControls .spl-netease-scroll::-webkit-scrollbar-thumb{background:rgba(255,255,255,.2);border-radius:3px}'
                    +'#spotilolPlayerControls .spl-netease-list{box-sizing:border-box;min-height:100%;padding-left:12px;padding-right:12px}'
                    +'#spotilolPlayerControls .spl-netease-line{box-sizing:border-box;display:flex;align-items:center;justify-content:center;width:100%;min-height:44px;padding:9px 6px;line-height:1.35;overflow-wrap:anywhere;white-space:normal;text-align:center;color:rgba(255,255,255,.38);font-size:14px;transition:color .2s,opacity .2s,transform .2s}'
                    +'#spotilolPlayerControls .spl-netease-current{color:#fff;font-size:17px;font-weight:700;transform:scale(1.02)}'
                    +'#spotilolPlayerControls .spl-netease-list.spl-netease-has-status{display:flex;align-items:center;justify-content:center;padding-top:0!important;padding-bottom:0!important}'
                    +'#spotilolPlayerControls .spl-netease-status{min-height:44px;padding:10px 16px;text-align:center;color:rgba(255,255,255,.68);font-size:14px;font-weight:500}'
                    +'#spotilolPlayerControls.spl-mini .spl-netease-lyrics{flex-basis:0!important;min-height:0!important;max-height:0!important;opacity:0!important;margin:0!important;pointer-events:none!important}'
                    +'@media(max-width:420px){#spotilolPlayerControls.spl-lyrics-open:not(.spl-mini){top:8px;bottom:max(8px,env(safe-area-inset-bottom))}#spotilolPlayerControls .spl-netease-list{padding-left:8px;padding-right:8px}#spotilolPlayerControls .spl-netease-line{min-height:40px;padding:8px 4px;font-size:13px}#spotilolPlayerControls .spl-netease-current{font-size:16px}}'
                    +'@media(orientation:landscape) and (max-height:520px){#spotilolPlayerControls.spl-lyrics-open:not(.spl-mini){top:4px;bottom:max(4px,env(safe-area-inset-bottom));padding-top:6px;padding-bottom:6px}#spotilolPlayerControls.spl-lyrics-open:not(.spl-mini) .spl-netease-lyrics{min-height:56px;margin-bottom:3px}#spotilolPlayerControls .spl-netease-line{min-height:34px;padding:5px 4px;font-size:12px}#spotilolPlayerControls .spl-netease-current{font-size:14px}}';
                (document.head||document.documentElement).appendChild(style);
            }

            function makeLine(){
                var line=document.createElement('div');
                line.className='spl-netease-line';
                return line;
            }

            function stopPlayerDrag(event){
                event.stopPropagation();
            }

            function clearManualBrowse(){
                if(state.manualTimer){clearTimeout(state.manualTimer);state.manualTimer=null;}
                state.manualBrowse=false;
            }

            function isLyricsVisible(){
                return !!(state.open&&state.player&&state.player.isConnected&&!state.player.classList.contains('spl-mini')&&isPlayerActive());
            }

            function syncEdgePadding(){
                if(!state.scroller||!state.list||!state.lineElements.length) return;
                var height=state.scroller.clientHeight||0;
                if(height<=0) return;
                var first=state.lineElements[0];
                var last=state.lineElements[state.lineElements.length-1];
                state.list.style.paddingTop=Math.max(12,Math.floor((height-(first.offsetHeight||44))/2))+'px';
                state.list.style.paddingBottom=Math.max(12,Math.floor((height-(last.offsetHeight||44))/2))+'px';
            }

            function followCurrent(behavior){
                if(!isLyricsVisible()||state.manualBrowse||!state.scroller||!state.lineElements.length) return;
                syncEdgePadding();
                var index=state.activeIndex<0?0:Math.min(state.activeIndex,state.lineElements.length-1);
                var target=state.lineElements[index];
                if(!target) return;
                var top=target.offsetTop-(state.scroller.clientHeight-target.offsetHeight)/2;
                top=Math.max(0,Math.min(top,state.scroller.scrollHeight-state.scroller.clientHeight));
                if(typeof state.scroller.scrollTo==='function'){
                    try{state.scroller.scrollTo({top:top,behavior:behavior||'smooth'});return;}catch(e){}
                }
                state.scroller.scrollTop=top;
            }

            function resumeFollowingSoon(){
                if(!state.manualBrowse) return;
                if(state.manualTimer) clearTimeout(state.manualTimer);
                state.manualTimer=setTimeout(function(){
                    state.manualTimer=null;
                    state.manualBrowse=false;
                    followCurrent('smooth');
                },3000);
            }

            function beginManualBrowse(){
                if(!isLyricsVisible()||!state.lineElements.length) return;
                state.manualBrowse=true;
                if(state.manualTimer){clearTimeout(state.manualTimer);state.manualTimer=null;}
            }

            function bindScroller(scroller){
                if(!scroller||scroller.__splLyricsBound) return;
                scroller.__splLyricsBound=true;
                scroller.addEventListener('touchstart',function(event){stopPlayerDrag(event);beginManualBrowse();},{passive:true});
                scroller.addEventListener('touchmove',function(event){stopPlayerDrag(event);},{passive:true});
                scroller.addEventListener('touchend',function(event){stopPlayerDrag(event);resumeFollowingSoon();},{passive:true});
                scroller.addEventListener('touchcancel',function(event){stopPlayerDrag(event);resumeFollowingSoon();},{passive:true});
                scroller.addEventListener('wheel',function(event){stopPlayerDrag(event);beginManualBrowse();resumeFollowingSoon();},{passive:true});
                scroller.addEventListener('mousedown',function(event){stopPlayerDrag(event);beginManualBrowse();resumeFollowingSoon();});
                scroller.addEventListener('click',stopPlayerDrag);
                scroller.addEventListener('scroll',function(){if(state.manualBrowse)resumeFollowingSoon();},{passive:true});
                scroller.addEventListener('keydown',function(event){
                    if(/^(ArrowUp|ArrowDown|PageUp|PageDown|Home|End| )$/.test(event.key||'')){
                        beginManualBrowse();
                        resumeFollowingSoon();
                    }
                });
            }

            function setOpen(open,forceFollow){
                var changed=state.open!==!!open;
                var wasVisible=state.panelVisible;
                state.open=!!open;
                if(changed) clearManualBrowse();
                if(state.player) state.player.classList.toggle('spl-lyrics-open',state.open);
                if(state.panel) state.panel.setAttribute('aria-hidden',state.open?'false':'true');
                if(state.button){
                    state.button.classList.toggle('spl-active',state.open);
                    state.button.setAttribute('aria-expanded',state.open?'true':'false');
                }
                state.panelVisible=isLyricsVisible();
                if(state.panelVisible&&(changed||forceFollow||!wasVisible)){
                    requestAnimationFrame(function(){
                        syncEdgePadding();
                        renderReady(playbackPositionMs(),true);
                    });
                }
            }

            function mountPlayer(){
                addStyles();
                var player=document.getElementById('spotilolPlayerControls');
                if(!player) return false;
                var playerChanged=player!==state.player;

                var panel=player.querySelector('.spl-netease-lyrics');
                if(!panel){
                    panel=document.createElement('div');
                    panel.className='spl-netease-lyrics';
                    panel.setAttribute('aria-label','Synchronized lyrics');
                    panel.setAttribute('aria-live','off');
                    panel.setAttribute('aria-hidden','true');
                    var row2=player.querySelector('.spl-row2');
                    if(row2) row2.before(panel);
                    else player.appendChild(panel);
                }

                var scroller=panel.querySelector('.spl-netease-scroll');
                var list=scroller&&scroller.querySelector('.spl-netease-list');
                if(!scroller||!list){
                    while(panel.firstChild) panel.removeChild(panel.firstChild);
                    var scroller=document.createElement('div');
                    scroller.className='spl-netease-scroll';
                    scroller.setAttribute('tabindex','0');
                    var list=document.createElement('div');
                    list.className='spl-netease-list';
                    scroller.appendChild(list);
                    panel.appendChild(scroller);
                }
                bindScroller(scroller);

                var button=player.querySelector('#spl-lyrics');
                if(button){
                    button.onclick=function(event){
                        if(event){event.preventDefault();event.stopPropagation();}
                        setOpen(!state.open);
                    };
                    button.removeAttribute('disabled');
                    button.setAttribute('aria-disabled','false');
                    button.setAttribute('aria-label','Netease lyrics');
                }

                state.player=player;
                state.panel=panel;
                state.scroller=scroller;
                state.list=list;
                state.button=button;
                if(playerChanged){state.lineElements=[];state.activeIndex=-2;clearManualBrowse();}
                setOpen(state.open,playerChanged);
                renderCurrentState();
                return true;
            }

            function queueMount(){
                if(state.mountQueued) return;
                state.mountQueued=true;
                setTimeout(function(){
                    state.mountQueued=false;
                    mountPlayer();
                },0);
            }

            function isPlayerActive(){
                var player=state.player;
                if(!player||!player.isConnected) return false;
                if(player.style.display==='none') return false;
                return getComputedStyle(player).display!=='none';
            }

            function normalizeText(value){
                var text=String(value||'');
                try{text=text.normalize('NFKC');}catch(e){}
                text=text.toLowerCase();
                text=text.replace(/\s+(?:feat(?:uring)?\.?|ft\.?)\s+.*$/i,' ');
                text=text.replace(/\s*[-–—:]\s*(?:\d{4}\s*)?(?:remaster(?:ed)?|live|version|edit|mix|acoustic|instrumental|现场|重制|伴奏).*$/i,' ');
                text=text.replace(/[\(\[（【][^\)\]）】]*(?:feat(?:uring)?\.?|ft\.?|remaster(?:ed)?|live|version|edit|mix|acoustic|instrumental|现场|重制|伴奏)[^\)\]）】]*[\)\]）】]/gi,' ');
                try{return text.replace(/[\s\p{P}\p{S}]+/gu,'');}
                catch(e){return text.replace(/[\s\W_]+/g,'');}
            }

            function normalizeSearchText(value){
                var text=String(value||'');
                try{text=text.normalize('NFKC');}catch(e){}
                text=text.replace(/\s+(?:feat(?:uring)?\.?|ft\.?)\s+.*$/i,' ');
                text=text.replace(/\s*[-–—:]\s*(?:\d{4}\s*)?(?:remaster(?:ed)?|live|version|edit|mix|acoustic|instrumental|现场|重制|伴奏).*$/i,' ');
                text=text.replace(/[\(\[（【][^\)\]）】]*(?:feat(?:uring)?\.?|ft\.?|remaster(?:ed)?|live|version|edit|mix|acoustic|instrumental|现场|重制|伴奏)[^\)\]）】]*[\)\]）】]/gi,' ');
                return text.replace(/\s+/g,' ').trim();
            }

            function buildSearchQueries(title,artist){
                var rawTitle=String(title||'').trim();
                var cleanTitle=normalizeSearchText(rawTitle)||rawTitle;
                var cleanArtist=String(artist||'').trim();
                try{cleanArtist=cleanArtist.normalize('NFKC');}catch(e){}
                var queries=[];
                function add(query){
                    query=String(query||'').replace(/\s+/g,' ').trim();
                    if(query&&queries.indexOf(query)===-1) queries.push(query);
                }
                add(cleanTitle+' '+cleanArtist);
                if(rawTitle!==cleanTitle) add(rawTitle+' '+cleanArtist);
                add(cleanTitle);
                if(rawTitle!==cleanTitle) add(rawTitle);
                return queries;
            }

            function playbackDurationMs(){
                var value=Number(window.duration);
                if(!isFinite(value)||value<=0) return 0;
                return value<10000?value*1000:value;
            }

            function playbackPositionMs(){
                var value=Number(window.position);
                if(!isFinite(value)||value<0) return 0;
                var rawDuration=Number(window.duration);
                return isFinite(rawDuration)&&rawDuration>0&&rawDuration<10000?value*1000:value;
            }

            function similarity(a,b){
                a=normalizeText(a);
                b=normalizeText(b);
                if(a===b) return a?1:0;
                if(!a||!b) return 0;
                var previous=new Array(b.length+1);
                var current=new Array(b.length+1);
                var i,j;
                for(j=0;j<=b.length;j++) previous[j]=j;
                for(i=1;i<=a.length;i++){
                    current[0]=i;
                    for(j=1;j<=b.length;j++){
                        var cost=a.charAt(i-1)===b.charAt(j-1)?0:1;
                        current[j]=Math.min(current[j-1]+1,previous[j]+1,previous[j-1]+cost);
                    }
                    var swap=previous;previous=current;current=swap;
                }
                return 1-previous[b.length]/Math.max(a.length,b.length);
            }

            function artistSimilarity(target,candidates){
                var targetParts=String(target||'').split(/[,/&、·;]+/).filter(function(part){return normalizeText(part);});
                var candidateParts=(candidates||[]).map(function(item){return item&&item.name?item.name:item;}).filter(function(part){return normalizeText(part);});
                if(!targetParts.length||!candidateParts.length) return null;
                var best=0;
                targetParts.forEach(function(left){
                    candidateParts.forEach(function(right){
                        best=Math.max(best,similarity(left,right));
                    });
                });
                best=Math.max(best,similarity(targetParts.join(' '),candidateParts.join(' ')));
                return best;
            }

            function durationSimilarity(target,candidate){
                target=Number(target);candidate=Number(candidate);
                if(!isFinite(target)||target<=0||!isFinite(candidate)||candidate<=0) return null;
                var difference=Math.abs(target-candidate);
                if(difference<=2000) return 1;
                return Math.max(0,1-(difference-2000)/13000);
            }

            function scoreSong(song,title,artist,duration){
                var titleScore=similarity(title,song&&song.name);
                var songArtists=song&&(song.artists||song.ar)||[];
                var artistScore=artistSimilarity(artist,songArtists);
                var songDuration=song&&(song.duration||song.dt);
                var durationScore=durationSimilarity(duration,songDuration);
                var weighted=titleScore*.65;
                var weight=.65;
                if(artistScore!==null){weighted+=artistScore*.25;weight+=.25;}
                if(durationScore!==null){weighted+=durationScore*.10;weight+=.10;}
                return {
                    song:song,
                    title:titleScore,
                    artist:artistScore,
                    duration:durationScore,
                    total:weight?weighted/weight:0
                };
            }

            function nativeJson(url){
                return Promise.resolve().then(function(){
                    if(!window.AndBridge||typeof AndBridge.nFetch!=='function') throw new Error('Native bridge unavailable');
                    var raw=AndBridge.nFetch(url,JSON.stringify({method:'GET',headers:{Accept:'application/json'}}));
                    var response=JSON.parse(raw);
                    if(!response||response.status<200||response.status>=300) throw new Error('HTTP '+(response?response.status:0));
                    return JSON.parse(response.body||'{}');
                });
            }

            function searchSongs(query){
                var url=SEARCH_URL+'?csrf_token=&s='+encodeURIComponent(query)+'&type=1&offset=0&total=true&limit=10';
                return nativeJson(url).then(function(data){
                    return data&&data.result&&Array.isArray(data.result.songs)?data.result.songs:[];
                });
            }

            function rankSongs(songs,title,artist,duration){
                var ranked=(songs||[]).map(function(song){return scoreSong(song,title,artist,duration);});
                ranked.sort(function(a,b){return b.total-a.total;});
                var accepted=ranked.filter(function(candidate){
                    if(candidate.title>=.65&&candidate.total>=.72) return true;
                    var targetTitle=normalizeText(title);
                    var candidateTitle=normalizeText(candidate.song&&candidate.song.name);
                    return !!(
                        targetTitle&&candidateTitle&&targetTitle.length===candidateTitle.length&&
                        candidate.artist!==null&&candidate.artist>=.9&&
                        candidate.duration!==null&&candidate.duration>=.9
                    );
                });
                return accepted.slice(0,MAX_LYRIC_CANDIDATES).map(function(candidate){return candidate.song;});
            }

            function parseLrc(text){
                var parsed=[];
                String(text||'').split(/\r?\n/).forEach(function(rawLine){
                    var timePattern=/\[(\d{1,3}):(\d{2})(?:[\.:](\d{1,3}))?\]/g;
                    var times=[];
                    var match;
                    while((match=timePattern.exec(rawLine))!==null){
                        var fraction=match[3]||'0';
                        var milliseconds=fraction.length===1?Number(fraction)*100:fraction.length===2?Number(fraction)*10:Number(fraction.substring(0,3));
                        times.push(Number(match[1])*60000+Number(match[2])*1000+milliseconds);
                    }
                    if(!times.length) return;
                    var content=rawLine.replace(/\[(\d{1,3}):(\d{2})(?:[\.:](\d{1,3}))?\]/g,'').trim();
                    if(!content) return;
                    times.forEach(function(time){parsed.push({time:time,text:content});});
                });
                parsed.sort(function(a,b){return a.time-b.time;});
                var seen=Object.create(null);
                return parsed.filter(function(line){
                    var key=line.time+'|'+line.text;
                    if(seen[key]) return false;
                    seen[key]=true;
                    return true;
                });
            }

            function fetchLyrics(id){
                var url=LYRIC_URL+'?id='+encodeURIComponent(id)+'&lv=1&kv=1&tv=-1';
                return nativeJson(url).then(function(data){
                    if(data&&(data.nolyric===true||data.sfy===true)) return [];
                    return parseLrc(data&&data.lrc&&data.lrc.lyric);
                });
            }

            function findSongs(title,artist,duration){
                var queries=buildSearchQueries(title,artist);
                var songs=[];
                var seen=Object.create(null);
                var successfulSearches=0;
                var sequence=Promise.resolve();
                queries.forEach(function(query){
                    sequence=sequence.then(function(){
                        return searchSongs(query).then(function(results){
                            successfulSearches++;
                            results.forEach(function(song){
                                var id=song&&(song.id||song.resourceId);
                                if(!id||seen[id]) return;
                                seen[id]=true;
                                songs.push(song);
                            });
                        }).catch(function(){});
                    });
                });
                return sequence.then(function(){
                    if(!successfulSearches) throw new Error('All lyric searches failed');
                    return rankSongs(songs,title,artist,duration);
                });
            }

            function fetchFirstLyrics(candidates){
                var limit=Math.min((candidates||[]).length,MAX_LYRIC_CANDIDATES);
                var successfulResponses=0;
                var failedResponses=0;
                function next(index){
                    if(index>=limit){
                        if(!successfulResponses&&failedResponses) throw new Error('All lyric requests failed');
                        return Promise.resolve([]);
                    }
                    var song=candidates[index];
                    return fetchLyrics(song&&(song.id||song.resourceId)).then(
                        function(lines){
                            successfulResponses++;
                            return lines.length?lines:next(index+1);
                        },
                        function(){
                            failedResponses++;
                            return next(index+1);
                        }
                    );
                }
                return next(0);
            }

            function clearList(){
                if(!state.list) return;
                while(state.list.firstChild) state.list.removeChild(state.list.firstChild);
                state.list.classList.remove('spl-netease-has-status');
                state.list.style.paddingTop='';
                state.list.style.paddingBottom='';
                state.lineElements=[];
            }

            function renderLyricsList(){
                if(!state.list) return;
                clearList();
                state.viewKind='ready';
                var fragment=document.createDocumentFragment();
                state.lines.forEach(function(item,index){
                    var element=makeLine();
                    element.textContent=item.text||'';
                    element.setAttribute('data-index',String(index));
                    fragment.appendChild(element);
                    state.lineElements.push(element);
                });
                state.list.appendChild(fragment);
                requestAnimationFrame(syncEdgePadding);
            }

            function showStatus(kind){
                clearManualBrowse();
                state.lines=[];
                state.activeIndex=-2;
                state.viewKind=kind||'idle';
                var text='';
                if(kind==='loading') text='Searching lyrics...';
                else if(kind==='empty') text='No synced lyrics found';
                else if(kind==='error') text='Lyrics unavailable';
                else text='Play a track to load lyrics';
                clearList();
                if(!state.list) return;
                state.list.classList.add('spl-netease-has-status');
                var status=document.createElement('div');
                status.className='spl-netease-status';
                status.textContent=text;
                state.list.appendChild(status);
            }

            function renderReady(positionMs,forceFollow){
                if(!state.lines.length){showStatus('empty');return;}
                if(state.lineElements.length!==state.lines.length||!state.lineElements[0]||!state.lineElements[0].isConnected){
                    renderLyricsList();
                }
                var low=0,high=state.lines.length-1,index=-1;
                while(low<=high){
                    var middle=(low+high)>>1;
                    if(state.lines[middle].time<=positionMs){index=middle;low=middle+1;}
                    else high=middle-1;
                }
                var changed=index!==state.activeIndex;
                if(!changed&&!forceFollow) return;
                if(changed&&state.activeIndex>=0&&state.lineElements[state.activeIndex]){
                    state.lineElements[state.activeIndex].classList.remove('spl-netease-current');
                    state.lineElements[state.activeIndex].removeAttribute('aria-current');
                }
                state.activeIndex=index;
                if(index>=0&&state.lineElements[index]){
                    state.lineElements[index].classList.add('spl-netease-current');
                    state.lineElements[index].setAttribute('aria-current','true');
                }
                if((changed||forceFollow)&&!state.manualBrowse){
                    requestAnimationFrame(function(){followCurrent(forceFollow?'auto':'smooth');});
                }
            }

            function renderCurrentState(){
                if(!state.panel||!state.list) return;
                if(state.lines.length){
                    if(state.lineElements.length!==state.lines.length||!state.lineElements[0]||!state.lineElements[0].isConnected){
                        renderLyricsList();
                    }
                    renderReady(playbackPositionMs(),false);
                    return;
                }
                var cached=state.trackKey&&getCachedEntry(state.trackKey);
                showStatus(cached?cached.kind:(state.viewKind||'idle'));
            }

            function getCachedEntry(key){
                var entry=state.cache[key];
                if(entry&&entry.expiresAt&&entry.expiresAt<=Date.now()){
                    delete state.cache[key];
                    return null;
                }
                return entry||null;
            }

            function applyCached(entry){
                clearManualBrowse();
                state.lines=entry&&entry.kind==='ready'?entry.lines:[];
                state.retryAt=entry&&entry.kind==='error'&&entry.expiresAt?entry.expiresAt:0;
                state.activeIndex=-2;
                if(state.lines.length){
                    renderLyricsList();
                    renderReady(playbackPositionMs(),true);
                }
                else showStatus(entry?entry.kind:'idle');
            }

            function loadTrack(key,title,artist,duration){
                var cached=getCachedEntry(key);
                if(cached){applyCached(cached);return;}
                var request=++state.generation;
                state.retryAt=0;
                state.lines=[];
                state.activeIndex=-2;
                showStatus('loading');
                findSongs(title,artist,duration).then(function(candidates){
                    if(request!==state.generation||!state.modeActive||key!==state.trackKey) return null;
                    return fetchFirstLyrics(candidates);
                }).then(function(lines){
                    if(lines===null||request!==state.generation||!state.modeActive||key!==state.trackKey) return;
                    var entry=lines.length
                        ?{kind:'ready',lines:lines}
                        :{kind:'empty',lines:[],expiresAt:Date.now()+EMPTY_CACHE_MS};
                    state.cache[key]=entry;
                    applyCached(entry);
                }).catch(function(){
                    if(request!==state.generation||!state.modeActive||key!==state.trackKey) return;
                    var entry={kind:'error',lines:[],expiresAt:Date.now()+ERROR_RETRY_MS};
                    state.cache[key]=entry;
                    applyCached(entry);
                });
            }

            function update(){
                if(!state.player||!state.player.isConnected) mountPlayer();
                var active=isPlayerActive();
                if(!active){
                    if(state.panelVisible){state.panelVisible=false;clearManualBrowse();}
                    if(state.modeActive){
                        state.modeActive=false;
                        state.generation++;
                        state.trackKey='';
                        state.lines=[];
                        state.retryAt=0;
                        state.activeIndex=-2;
                    }
                    return;
                }

                if(!state.modeActive){
                    state.modeActive=true;
                    state.trackKey='';
                }

                var visible=isLyricsVisible();
                if(visible!==state.panelVisible){
                    state.panelVisible=visible;
                    clearManualBrowse();
                    if(visible){
                        requestAnimationFrame(function(){
                            syncEdgePadding();
                            renderReady(playbackPositionMs(),true);
                        });
                    }
                }

                var title=String(window.track||'').trim();
                var artist=String(window.artist||'').trim();
                var duration=playbackDurationMs();
                if(!title||!isFinite(duration)||duration<=0){
                    if(state.trackKey||state.lines.length||state.viewKind!=='idle'){
                        state.generation++;
                        state.trackKey='';
                        state.retryAt=0;
                        showStatus('idle');
                    }
                    return;
                }

                var key=normalizeText(title)+'|'+normalizeText(artist)+'|'+Math.round(duration);
                if(key!==state.trackKey){
                    state.trackKey=key;
                    loadTrack(key,title,artist,duration);
                    return;
                }
                if(state.retryAt&&Date.now()>=state.retryAt){
                    delete state.cache[key];
                    state.retryAt=0;
                    loadTrack(key,title,artist,duration);
                    return;
                }
                if(state.lines.length) renderReady(playbackPositionMs());
            }

            var observer=new MutationObserver(queueMount);
            observer.observe(document.documentElement,{childList:true,subtree:true});
            state.observer=observer;
            state.timer=setInterval(update,250);
            window.addEventListener('resize',function(){
                requestAnimationFrame(function(){
                    syncEdgePadding();
                    if(!state.manualBrowse)followCurrent('auto');
                });
            });
            mountPlayer();
            update();
        })();
    """
}
