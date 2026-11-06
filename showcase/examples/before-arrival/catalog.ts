import seed from "../../../shared/catalog.json";
export type Provider = { id:string; label:string; kind:string; suggestedUrl:string; urlPattern:string };
export type ContentItem = { id:string; sourceId:string; title:string; publishedAt:string|null; publishedPrecision?:string; originalUrl:string|null; summary:string; estimatedSeconds:number; deadlineAt:string|null; provenance:string; fetchedAt:string|null };
export type RegisteredSource = {id:string; name:string; provider:string; kind:string; registrationUrl:string|null; status:string; enabled:boolean; items:ContentItem[]; lastAttemptAt?:string|null; lastSuccessAt?:string|null; refreshError?:string|null};
export type SavedItem = ContentItem & {reviewed?:boolean};
export type Registry = {schemaVersion:2; sources:RegisteredSource[]; savedItems:SavedItem[]};
export const providers = seed.providers as Provider[];
export const registryKey = "before-arrival-sources-v2";
export const oldKey = "before-arrival-showcase-v1";
const clone = <T,>(value:T):T => JSON.parse(JSON.stringify(value));
export const defaults = ():Registry => ({schemaVersion:2,sources:clone(seed.sources) as RegisteredSource[],savedItems:[]});
export function canonicalUrl(value:string) {
  const url=new URL(value);
  if(url.protocol!=="https:"||url.username||url.password||url.hash||value.length>512) throw new Error("공식 HTTPS 주소를 확인해 주세요.");
  const steam=url.hostname==="store.steampowered.com"&&url.pathname.match(/^\/(?:app|news\/app)\/([0-9]+)(?:\/|$)/);
  if(steam) {const id=Number(steam[1]);if(!Number.isInteger(id)||id<1||id>4294967295)throw new Error("게임 주소를 확인해 주세요.");return `https://store.steampowered.com/news/app/${id}`;}
  return url.href.replace(/\/$/,"");
}
function validSteamArticleId(value:unknown):value is string {
  return typeof value==="string"&&/^[1-9][0-9]{0,19}$/.test(value)&&BigInt(value)<=18446744073709551615n;
}
export function officialSteamUrl(value:string,registrationUrl?:string|null,articleId?:string):boolean {
  if(typeof value!=="string"||value.length>512||(articleId!==undefined&&!validSteamArticleId(articleId))) return false;
  const cdn=value.match(/^https:\/\/steamstore-a\.akamaihd\.net\/news\/externalpost\/steam_community_announcements\/([1-9][0-9]{0,19})$/);
  const community=value.match(/^https:\/\/steamcommunity\.com\/games\/[A-Za-z0-9_-]+\/announcements\/detail\/([1-9][0-9]{0,19})\/?$/);
  const store=value.match(/^https:\/\/store\.steampowered\.com\/news\/app\/([1-9][0-9]*)\/view\/([1-9][0-9]{0,19})\/?$/);
  const found=cdn?.[1]||community?.[1]||store?.[2];
  if(!validSteamArticleId(found)||(articleId!==undefined&&found!==articleId))return false;
  const registeredApp=registrationUrl?.match(/\/(?:app|news\/app)\/([0-9]+)/)?.[1];
  return !store||!registrationUrl||Boolean(registeredApp&&store[1]===registeredApp);
}
export function officialUrl(provider:string,value:string,registrationUrl?:string|null):boolean {
  try {
    if(provider==="steam")return officialSteamUrl(value,registrationUrl);
    const url=new URL(value);
    if(url.protocol!=="https:"||url.username||url.password||url.hash||value.length>512) return false;
    return Boolean(providers.find(p=>p.id===provider)&&new RegExp(providers.find(p=>p.id===provider)!.urlPattern).test(url.href));
  } catch {return false;}
}
export function addSource(state:Registry,providerId:string,name:string,rawUrl:string):Registry {
  const provider=providers.find(p=>p.id===providerId);
  if(!provider||!new RegExp(provider.urlPattern).test(rawUrl.trim())) throw new Error("선택한 서비스의 공식 채널·게시판 주소를 입력해 주세요.");
  const url=canonicalUrl(rawUrl.trim());
  if(!name.trim()||name.trim().length>80) throw new Error("출처 이름을 1~80자로 입력해 주세요.");
  if(state.sources.some(s=>s.registrationUrl&&canonicalUrl(s.registrationUrl)===url)) throw new Error("이미 등록한 출처예요.");
  const id="url:"+url;
  const snapshot=seed.snapshots.find(s=>s.provider===providerId&&canonicalUrl(s.registrationUrl)===url);
  const items=snapshot ? clone(snapshot.items).map(item=>({...item,sourceId:id})) as ContentItem[] : [];
  const source:RegisteredSource={id,name:name.trim(),provider:providerId,kind:provider.kind,registrationUrl:url,status:items.length?"connected_snapshot":["youtube","chzzk","navercafe"].includes(providerId)?"setup_needed":"link_saved",enabled:true,items};
  return {...state,sources:[...state.sources,source]};
}
export function moveSource(state:Registry,id:string,delta:number):Registry {
  const sources=[...state.sources],i=sources.findIndex(s=>s.id===id),j=i+delta;
  if(i<0||j<0||j>=sources.length) return state;
  [sources[i],sources[j]]=[sources[j],sources[i]];
  return {...state,sources};
}
export function toggleSource(state:Registry,id:string,enabled:boolean):Registry {
  return {...state,sources:state.sources.map(s=>s.id===id?{...s,enabled}:s)};
}
export function deduplicate(items:ContentItem[]):ContentItem[] {
  const seen=new Set<string>();
  return items.filter(item=>{const key=item.originalUrl||`${item.sourceId}:${item.id}`;if(seen.has(key)) return false;seen.add(key);return true;});
}
export function planRegistry(remainingSeconds:number|null,observedAt:string,sources:RegisteredSource[]) {
  const budget=Math.min(45,Math.floor(Math.max(0,(remainingSeconds??0)-180)/5));
  const candidates=deduplicate(sources.filter(s=>s.enabled).flatMap(s=>s.items));
  const urgent=(item:ContentItem)=>{const delta=item.deadlineAt?Date.parse(item.deadlineAt)-Date.parse(observedAt):-1;return delta>=0&&delta<=600000;};
  candidates.sort((a,b)=>Number(urgent(b))-Number(urgent(a)));
  const selected:ContentItem[]=[],deferred:ContentItem[]=[];let seconds=0;
  for(const item of candidates) {
    if(item.estimatedSeconds<=budget-seconds) {selected.push(item);seconds+=item.estimatedSeconds;}
    else deferred.push(item);
  }
  return {selected,deferred,budget,seconds};
}
export function saveItem(state:Registry,item:ContentItem):Registry {
  return {...state,savedItems:deduplicate([...state.savedItems,item])};
}
export function localSteamConnectionAvailable():boolean {
  return typeof document!=="undefined"&&typeof location!=="undefined"&&location.protocol==="http:"
    &&["127.0.0.1","localhost"].includes(location.hostname)
    &&document.querySelector('meta[name="before-arrival-local-app"]')?.getAttribute("content")==="steam-news-v1";
}
export function statusText(source:RegisteredSource) {
  if(source.provider==="steam"&&typeof document!=="undefined"&&!localSteamConnectionAvailable()) return source.items.length?"보관한 소식만 표시 · 앱 실행 안내 확인":"새 소식 연결은 앱 실행 안내를 확인해 주세요";
  if(source.refreshError) return source.items.length?"갱신 실패 · 이전 소식 보관 중":"소식을 가져오지 못했어요";
  if(source.status==="connected") return source.items.length?"공식 소식 연결":"새 소식 없음";
  if(source.status==="connected_snapshot") return "가져온 공식 소식 · 자동 갱신 아님";
  if(source.status==="demo_sample") return "예시 콘텐츠";
  if(source.status==="setup_needed") return "연결 설정 필요 · 아직 소식 없음";
  return "링크 저장 · 자동 연결 미확인";
}
export function sourceName(state:Registry,item:ContentItem) {return state.sources.find(s=>s.id===item.sourceId)?.name||"저장한 출처";}
function validItem(item:any,source:RegisteredSource) {
  return typeof item?.id==="string"&&item.sourceId===source.id&&typeof item.title==="string"&&item.title.length>0&&item.title.length<=500&&typeof item.summary==="string"&&item.summary.length<=2000&&Number.isInteger(item.estimatedSeconds)&&item.estimatedSeconds>0&&item.estimatedSeconds<=3600&&(!item.originalUrl||officialUrl(source.provider,item.originalUrl,source.registrationUrl));
}
export function readRegistry(storage:Pick<Storage,"getItem">):Registry {
  try {
    const value=JSON.parse(storage.getItem(registryKey)||"null");
    if(value?.schemaVersion===2&&Array.isArray(value.sources)&&value.sources.length<=100&&Array.isArray(value.savedItems)) {
      const valid=value.sources.every((s:any)=>typeof s?.id==="string"&&typeof s.name==="string"&&s.name.length<=80&&typeof s.enabled==="boolean"&&Array.isArray(s.items)&&s.items.length<=50&&(s.provider==="sample"?seed.sources.some(v=>v.id===s.id):providers.some(p=>p.id===s.provider&&typeof s.registrationUrl==="string"&&new RegExp(p.urlPattern).test(s.registrationUrl)))&&s.items.every((item:any)=>validItem(item,s)));
      if(valid) return {...value,savedItems:value.savedItems.filter((i:any)=>value.sources.some((s:RegisteredSource)=>s.id===i.sourceId&&validItem(i,s))).slice(0,200)};
    }
  } catch {}
  const state=defaults();
  try {
    const old=JSON.parse(storage.getItem(oldKey)||"null");
    if(!old||!["game","meeting","community"].every(k=>typeof old[k]==="boolean")) return state;
    const legacy:Record<string,string>={game:"sample-game",meeting:"sample-schedule",community:"sample-community"};
    state.sources.forEach(s=>{const key=Object.keys(legacy).find(k=>legacy[k]===s.id)!;s.enabled=old[key];});
    const first=state.sources.findIndex(s=>s.id===legacy[old.first]);
    if(first>=0) state.sources.unshift(...state.sources.splice(first,1));
    const saved=state.sources.flatMap(s=>s.items).find(i=>i.title===old.savedTitle);
    if(old.saved&&saved) state.savedItems=[saved];
    else if(old.saved&&typeof old.savedTitle==="string"&&old.savedTitle.trim()&&old.savedTitle.length<=500) {
      const sourceId=old.savedTitle.includes("회의")?"sample-schedule":old.savedTitle.includes("팟캐스트")?"sample-community":"sample-game";
      state.savedItems=[{id:"legacy-saved",sourceId,title:old.savedTitle,publishedAt:null,publishedPrecision:"unknown",originalUrl:null,summary:"이전 브라우저 시연에서 저장한 예시 항목이에요.",estimatedSeconds:8,deadlineAt:null,provenance:"demo_sample",fetchedAt:null}];
    }
  } catch {}
  return state;
}
export function applyFeed(state:Registry,sourceId:string,result:{items?:ContentItem[];error?:string},attemptedAt:string):Registry {
  return {...state,sources:state.sources.map(source=>{
    if(source.id!==sourceId) return source;
    if(result.error) return {...source,lastAttemptAt:attemptedAt,refreshError:"갱신 실패"};
    if((result.items||[]).some(i=>!validItem(i,source))) return {...source,lastAttemptAt:attemptedAt,refreshError:"갱신 실패"};
    const items=deduplicate(result.items||[]).slice(0,20);
    return {...source,items,status:"connected",lastAttemptAt:attemptedAt,lastSuccessAt:attemptedAt,refreshError:null};
  })};
}
export function parseSteam(body:any,source:RegisteredSource,observedAt:string):ContentItem[] {
  const appid=source.registrationUrl?.match(/\/(?:app|news\/app)\/([0-9]+)/)?.[1];
  if(!appid||String(body?.appnews?.appid)!==appid||!Array.isArray(body.appnews.newsitems)||body.appnews.newsitems.length>50) throw new Error("소식 내용을 확인하지 못했어요.");
  const clean=(text:string)=>text.replace(/<(script|style)[^>]*>[\s\S]*?<\/\1>/gi," ").replace(/<[^>]*>/g," ").replace(/\[\/?[A-Za-z][^\]]*\]/g," ").replace(/&amp;/g,"&").replace(/&lt;/g,"<").replace(/&gt;/g,">").replace(/&quot;/g,'"').replace(/&#39;/g,"'").replace(/&nbsp;/g," ").replace(/\s+/g," ").trim().slice(0,300);
  const items:ContentItem[]=[];
  for(const n of body.appnews.newsitems) {
    if(n?.feedname!=="steam_community_announcements") continue;
    if(typeof n.title!=="string"||!clean(n.title)||typeof n.contents!=="string"||!clean(n.contents)||(!validSteamArticleId(n.gid))||typeof n.url!=="string"||!officialSteamUrl(n.url,source.registrationUrl,n.gid)||(n.appid!==undefined&&String(n.appid)!==appid)||!Number.isFinite(n.date)||n.date<=0) throw new Error("공식 소식 내용을 확인하지 못했어요.");
    const summary=clean(n.contents);
    items.push({id:"steam:"+n.gid,sourceId:source.id,title:clean(n.title),publishedAt:new Date(n.date*1000).toISOString(),publishedPrecision:"instant",originalUrl:n.url,summary,estimatedSeconds:Math.min(30,Math.max(8,Math.ceil(summary.length/6))),deadlineAt:null,provenance:"official_runtime",fetchedAt:observedAt});
  }
  return deduplicate(items).slice(0,20);
}
export async function refreshSteam(source:RegisteredSource,signal:AbortSignal,fetcher:typeof fetch=fetch) {
  if(source.provider!=="steam") throw new Error("이 출처는 자동 갱신을 지원하지 않아요.");
  const id=source.registrationUrl?.match(/\/(?:app|news\/app)\/([0-9]+)/)?.[1];
  if(!id||!Number.isSafeInteger(Number(id))||Number(id)<1||Number(id)>4294967295) throw new Error("게임 주소를 확인해 주세요.");
  const inBrowser=typeof document!=="undefined";
  if(inBrowser&&!localSteamConnectionAvailable()) throw new Error("앱 실행 안내대로 열어 주세요. 보관한 내용은 그대로예요.");
  const url=inBrowser
    ? new URL(`/api/steam-news?appid=${id}`,location.origin).href
    : `https://api.steampowered.com/ISteamNews/GetNewsForApp/v2/?appid=${id}&count=5&maxlength=600&format=json&feeds=steam_community_announcements`;
  const response=await fetcher(url,{signal,redirect:"error",credentials:"omit",referrerPolicy:"no-referrer",cache:"no-store"});
  if(!response.ok) throw new Error("새 소식을 가져오지 못했어요.");
  const reader=response.body?.getReader();if(!reader) throw new Error("소식이 비어 있어요.");
  let size=0;const chunks:Uint8Array[]=[];
  while(true) {const {value,done}=await reader.read();if(done) break;size+=value.byteLength;if(size>262144){await reader.cancel();throw new Error("소식 용량을 확인해 주세요.");}chunks.push(value);}
  const bytes=new Uint8Array(size);let offset=0;for(const chunk of chunks){bytes.set(chunk,offset);offset+=chunk.length;}
  const fetchedAt=new Date().toISOString();
  return {items:parseSteam(JSON.parse(new TextDecoder().decode(bytes)),source,fetchedAt),fetchedAt};
}
