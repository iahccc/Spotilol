package com.project.lol.webview.injections

object ClassicBridge {
    const val CONTENT = """
        (function(){
            if(window.__splNativeFetchPending) return;
            var pending = Object.create(null);
            var seq = 0;
            window.__splNativeFetchPending = pending;

            function asError(message){
                var e = new Error(message || 'Native network error');
                e.name = 'TypeError';
                return e;
            }
            function asAbortError(){
                try { return new DOMException('The operation was aborted.', 'AbortError'); }
                catch(e){ var ex = new Error('The operation was aborted.'); ex.name='AbortError'; return ex; }
            }
            function dataResponse(data,url){
                var headers = new Headers(data.headers || {});
                var body = data.body || '';
                if(typeof data.bodyBase64==='string'){
                    var decoded = atob(data.bodyBase64);
                    var bytes = new Uint8Array(decoded.length);
                    for(var i=0;i<decoded.length;i++) bytes[i]=decoded.charCodeAt(i);
                    body = bytes;
                }
                // Response forbids non-null bodies for 204, 205 and 304 responses.
                if(data.status===204 || data.status===205 || data.status===304) body=null;
                var response=new Response(body, {status:data.status,headers:headers});
                // Synthetic Responses lack a URL; preserve the native bridge's previous response.url.
                try{ Object.defineProperty(response,'url',{value:url,configurable:true}); }catch(e){}
                return response;
            }
            window.__splNativeFetchDone = function(id, raw){
                var p = pending[id];
                if(!p) return;
                delete pending[id];
                clearTimeout(p.timer);
                if(p.signal && p.abort) p.signal.removeEventListener('abort',p.abort);
                if(window.__splPerf) window.__splPerf('native-fetch',performance.now()-p.start);
                try{
                    var data=JSON.parse(raw);
                    if(!data || !data.status) throw asError(data && data.body);
                    p.resolve(dataResponse(data,p.url));
                }catch(e){ p.reject(e); }
            };

            window.mngFetch = async function(input, opts){
                var o=opts || {};
                var req = (typeof Request!=='undefined' && input instanceof Request) ? input : null;
                var url = req ? req.url : String(input);
                var method=o.method || (req && req.method) || 'GET';
                var h=new Headers(req ? req.headers : undefined);
                if(o.headers) new Headers(o.headers).forEach(function(v,k){ h.set(k,v); });
                var headers={};
                h.forEach(function(v,k){ headers[k]=v; });
                var body=null;
                if(Object.prototype.hasOwnProperty.call(o,'body') && o.body!==undefined && o.body!==null){
                    body=typeof o.body==='string' ? o.body : JSON.stringify(o.body);
                }else if(req && method!=='GET' && method!=='HEAD' && req.body){
                    body=await req.clone().text();
                }
                var signal=o.signal || (req && req.signal);
                if(signal && signal.aborted) throw asAbortError();
                if(Object.keys(pending).length>80) throw asError('Too many pending requests');
                return new Promise(function(resolve,reject){
                    var id='spl'+Date.now().toString(36)+(++seq).toString(36);
                    var start=performance.now();
                    var entry={resolve:resolve,reject:reject,signal:signal,start:start,url:url};
                    var abort=function(){
                        if(!pending[id]) return;
                        delete pending[id];
                        clearTimeout(entry.timer);
                        if(signal) signal.removeEventListener('abort',abort);
                        try{ AndBridge.cancelFetch(id); }catch(e){}
                        reject(asAbortError());
                    };
                    entry.abort=abort;
                    entry.timer=setTimeout(function(){
                        if(!pending[id]) return;
                        delete pending[id];
                        if(signal) signal.removeEventListener('abort',abort);
                        try{ AndBridge.cancelFetch(id); }catch(e){}
                        reject(asError('Native request timed out'));
                    },24000);
                    pending[id]=entry;
                    if(signal) signal.addEventListener('abort',abort,{once:true});
                    if(signal && signal.aborted){ abort(); return; }
                    try{
                        AndBridge.nFetchAsync(id,url,JSON.stringify({method:method,headers:headers,body:body}));
                    }catch(e){
                        delete pending[id];
                        clearTimeout(entry.timer);
                        if(signal) signal.removeEventListener('abort',abort);
                        reject(e);
                    }
                });
            };
        })();
    """
}
