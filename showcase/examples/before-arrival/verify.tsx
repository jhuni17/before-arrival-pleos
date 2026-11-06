import {toggleSavedReview,prepareSavedRemoval,prepareSavedRestore,savedIdentity} from "./saved-undo";
import assert from "node:assert/strict";
import actualSteamResponse from "../../../shared/evidence/steam-real-response.json";
import { renderToStaticMarkup } from "react-dom/server";
import { Showcase,frames,sequence } from "./index";
import {defaults,addSource,moveSource,toggleSource,planRegistry,saveItem,readRegistry,registryKey,oldKey,applyFeed,parseSteam,refreshSteam,canonicalUrl,officialSteamUrl} from "./catalog";
const render=(state:string)=>renderToStaticMarkup(<Showcase state={state}/>);
assert.match(render("opening"),/이번 주말, 협동 모드가 열려요/);
assert.match(render("opening"),/예상 32초 · 예산 45초/);
assert.match(render("priority"),/9시 팀 회의가 3층으로 바뀌었어요/);
for(const frame of ["restricted","unknown","arrived"])assert.match(render(frame),/class="primary" disabled=""/);
for(const frame of frames)assert.match(render(frame),/Android 앱\/다른 기기와 동기화 안 됨/);
const trace:unknown[]=[];await sequence({mark:(s:string)=>trace.push(s),wait:async()=>{},set:async(s:object)=>trace.push(s)} as any);
assert.deepEqual(trace,["18-minutes-game",{frame:"saved"},"save-simulation",{frame:"priority"},"4-minutes-meeting",{frame:"arrived"},"arrival-review"]);
const when="2026-11-04T23:40:00Z",short="2026-11-04T23:54:00Z";
let state=defaults();
const plan=()=>planRegistry(1080,when,state.sources);
assert.equal(plan().seconds,32);
state=addSource(state,"lol","내 리그 오브 레전드","https://www.leagueoflegends.com/ko-kr/news/game-updates/");
const lol=state.sources.at(-1)!;
assert.equal(lol.status,"connected_snapshot");assert.equal(lol.items.length,1);assert.equal(lol.items[0].provenance,"official_snapshot");
assert.equal(lol.items[0].publishedAt,"2026-11-03T19:00:00.000Z");
for(let i=0;i<3;i++)state=moveSource(state,lol.id,-1);
assert.equal(plan().selected[0].title,"리그 오브 레전드 26.22 패치 노트");
assert.equal(planRegistry(240,short,state.sources).selected[0].sourceId,"sample-schedule");
state=toggleSource(state,lol.id,false);assert.equal(plan().selected[0].sourceId,"sample-game");
state=toggleSource(state,lol.id,true);state=saveItem(state,lol.items[0]);state=saveItem(state,lol.items[0]);assert.equal(state.savedItems.length,1);
const storage={getItem:(key:string)=>key===registryKey?JSON.stringify(state):null};
assert.deepEqual(readRegistry(storage),state);
const migrated=readRegistry({getItem:key=>key===oldKey?JSON.stringify({game:false,meeting:true,community:true,first:"community",saved:true,savedTitle:"9시 팀 회의가 3층으로 바뀌었어요"}):null});
assert.equal(migrated.sources[0].id,"sample-community");assert.equal(migrated.sources.find(s=>s.id==="sample-game")!.enabled,false);assert.equal(migrated.savedItems.length,1);
const legacyLong=readRegistry({getItem:key=>key===oldKey?JSON.stringify({game:true,meeting:true,community:true,first:"game",saved:true,savedTitle:"길게 바꾼 회의 제목"}):null});assert.equal(legacyLong.savedItems[0].title,"길게 바꾼 회의 제목");
state=addSource(state,"youtube","내 채널","https://www.youtube.com/@OfficialChannel");assert.equal(state.sources.at(-1)!.items.length,0);assert.equal(state.sources.at(-1)!.status,"setup_needed");
assert.throws(()=>addSource(state,"lol","가짜","https://www.leagueoflegends.com.evil.test/ko-kr/news/game-updates/"));
assert.throws(()=>addSource(state,"steam","잘못된게임","https://store.steampowered.com/app/0"));
assert.equal(canonicalUrl("https://store.steampowered.com/app/00570/"),"https://store.steampowered.com/news/app/570");
assert.throws(()=>addSource(state,"lol","중복","https://www.leagueoflegends.com/ko-kr/news/game-updates/"));
let steamState=addSource(defaults(),"steam","Dota 2","https://store.steampowered.com/app/570/");const source=steamState.sources.at(-1)!;
const item={gid:"123",title:"Fixture official update",url:"https://steamcommunity.com/games/dota2/announcements/detail/123",contents:"<p>Fixture only, not fetched news.</p>",date:1793870400,feedname:"steam_community_announcements"};
const parsed=parseSteam({appnews:{appid:570,newsitems:[item,item,{...item,gid:"external",feedname:"pcgamer",url:"https://evil.test/news"}]}},source,"2026-11-05T12:00:00Z");assert.equal(parsed.length,1);
assert.throws(()=>parseSteam({appnews:{appid:570,newsitems:[{...item,url:"https://store.steampowered.com/news/app/730/view/123"}]}},source,when));
assert.throws(()=>parseSteam({appnews:{appid:730,newsitems:[item]}},source,when));
steamState=applyFeed(steamState,source.id,{items:parsed},"2026-11-05T12:00:00Z");
assert.throws(()=>parseSteam({appnews:{appid:570,newsitems:[{...item,title:""}]}},source,when));
const malformed=applyFeed(steamState,source.id,{items:[{...parsed[0],title:""}]},"2026-11-05T12:59:00Z");assert.deepEqual(malformed.sources.at(-1)!.items,parsed);assert(malformed.sources.at(-1)!.refreshError);
const failed=applyFeed(steamState,source.id,{error:"network"},"2026-11-05T13:00:00Z");assert.deepEqual(failed.sources.at(-1)!.items,parsed);assert.equal(failed.sources.at(-1)!.lastSuccessAt,"2026-11-05T12:00:00Z");
assert.equal(applyFeed(failed,source.id,{items:[]},"2026-11-05T13:01:00Z").sources.at(-1)!.items.length,0);
let requested="";const fetched=await refreshSteam(source,new AbortController().signal,async(url,options)=>{requested=String(url);assert.equal(options?.credentials,"omit");assert.equal(options?.redirect,"error");return new Response(JSON.stringify({appnews:{appid:570,newsitems:[item]}}),{status:200});});assert.match(requested,/^https:\/\/api.steampowered.com\/ISteamNews\/GetNewsForApp\/v2\//);assert.equal(fetched.items.length,1);
await assert.rejects(()=>refreshSteam(source,new AbortController().signal,async()=>new Response("denied",{status:403})));
await assert.rejects(()=>refreshSteam(source,new AbortController().signal,async()=>new Response("x".repeat(262145),{status:200})));
assert.equal(planRegistry(null,when,state.sources).selected.length,0);
console.log("PASS: shared catalog, legacy migration, add/order/enable→ETA plan,3 official snapshots provenance,date precision,local saved dedup,official URL allowlist,runtime Steam fixture dedup/failure/empty/size bounds. Fixtures do not establish live connection.");

const observedAt="2026-11-05T12:47:21Z";
const actualItems=parseSteam(actualSteamResponse,source,observedAt);
assert.equal(actualItems.length,5);
assert.equal(actualItems[0].title,"Inside the Trove of Terror");
assert.equal(actualItems[0].originalUrl,"https://steamstore-a.akamaihd.net/news/externalpost/steam_community_announcements/1846018067924633");
let actualState=applyFeed(addSource(defaults(),"steam","Dota 2 실제 응답 재생","https://store.steampowered.com/news/app/570"),source.id,{items:actualItems},observedAt);
for(let i=0;i<3;i++)actualState=moveSource(actualState,source.id,-1);
assert.equal(planRegistry(1080,when,actualState.sources).selected[0].title,"Inside the Trove of Terror");
assert.equal(planRegistry(240,short,actualState.sources).selected[0].sourceId,"sample-schedule");
actualState=saveItem(actualState,actualItems[0]);assert.equal(readRegistry({getItem:key=>key===registryKey?JSON.stringify(actualState):null}).savedItems[0].originalUrl,actualItems[0].originalUrl);
const realFirst=actualSteamResponse.appnews.newsitems[0];
const unsafeUrls=[
 "https://steamstore-a.akamaihd.net.evil.test/news/externalpost/steam_community_announcements/"+realFirst.gid,
 "https://other.steamstore-a.akamaihd.net/news/externalpost/steam_community_announcements/"+realFirst.gid,
 "https://steamstore-a.akamaihd.net/news/externalpost/pcgamer/"+realFirst.gid,
 "https://steamstore-a.akamaihd.net/news/externalpost/steam_community_announcements/%31"+realFirst.gid.slice(1),
 "https://steamstore-a.akamaihd.net/news/ignored/../externalpost/steam_community_announcements/"+realFirst.gid,
 realFirst.url+"?token=secret",realFirst.url+"#fragment",
 realFirst.url.replace(".net/",".net:443/"),realFirst.url.replace("https://","https://user@"),
 "https://steamstore-a.akamaihd.net/news/externalpost/steam_community_announcements/0",
 "https://steamstore-a.akamaihd.net/news/externalpost/steam_community_announcements/18446744073709551616",
];
for(const url of unsafeUrls){assert.equal(officialSteamUrl(url,source.registrationUrl,realFirst.gid),false);assert.throws(()=>parseSteam({appnews:{appid:570,newsitems:[{...realFirst,url}]}},source,observedAt));}
for(const delta of [{gid:"999"},{gid:"bad"},{appid:730},{url:realFirst.url.replace(realFirst.gid,"999")}])assert.throws(()=>parseSteam({appnews:{appid:570,newsitems:[{...realFirst,...delta}]}},source,observedAt));
assert.throws(()=>parseSteam({appnews:{appid:730,newsitems:[realFirst]}},source,observedAt));
assert.throws(()=>parseSteam({appnews:{appid:570,newsitems:[realFirst,{...realFirst,gid:"999"}]}},source,observedAt));
console.log("PASS: captured real Steam HTTP200 response replay:5 official CDN articles → ordered 18-minute briefing → 4-minute schedule priority → saved/reload original URL; strict host/path/gid/uint64/response+item-appid negatives. Replay is not live browser refresh.");

const undoBase=defaults();
const undoA={...undoBase.sources[0].items[0],id:"undo-a",originalUrl:"https://www.leagueoflegends.com/ko-kr/news/game-updates/a/"};
const undoB={...undoBase.sources[0].items[0],id:"undo-b",originalUrl:"https://www.leagueoflegends.com/ko-kr/news/game-updates/b/",summary:"original saved text"};
const undoC={...undoBase.sources[0].items[0],id:"undo-c",originalUrl:"https://www.leagueoflegends.com/ko-kr/news/game-updates/c/"};
const undoD={...undoBase.sources[0].items[0],id:"undo-d",originalUrl:null};
const removal=prepareSavedRemoval({...undoBase,savedItems:[undoA,undoB,undoC]},undoB)!;
assert.deepEqual(removal.next.savedItems.map(item=>item.id),["undo-a","undo-c"]);
assert.equal(prepareSavedRemoval(removal.next,undoB),null);
const latestUndoState={...removal.next,sources:[...removal.next.sources].reverse(),savedItems:[undoA,undoC,undoD]};
const restoredUndo=prepareSavedRestore(latestUndoState,removal.removal);
assert.deepEqual(restoredUndo.next.savedItems.map(item=>item.id),["undo-a","undo-b","undo-c","undo-d"]);
assert.equal(restoredUndo.next.sources,latestUndoState.sources);
assert.deepEqual(restoredUndo.next.savedItems[1],undoB);
const newerCopy={...undoB,originalUrl:undoB.originalUrl!.replace(/\/$/,""),summary:"newer saved copy"};
const alreadySaved={...latestUndoState,savedItems:[undoA,undoC,undoD,newerCopy]};
const duplicateUndo=prepareSavedRestore(alreadySaved,removal.removal);
assert.equal(duplicateUndo.alreadyPresent,true);assert.equal(duplicateUndo.next,alreadySaved);assert.equal(duplicateUndo.next.savedItems.at(-1)!.summary,"newer saved copy");
assert.equal(savedIdentity(newerCopy),savedIdentity(undoB));
console.log("PASS: saved undo merges only removed item at original relative position, preserves intervening state, missing targets and newer canonical duplicate copies.");

const reviewA={...undoA,originalUrl:null},reviewB={...undoB,originalUrl:null};
const reviewBase={...undoBase,savedItems:[reviewA,reviewB]};
const unread=(registry:typeof reviewBase)=>registry.savedItems.filter(item=>!(item as {reviewed?:boolean}).reviewed).length;
assert.equal(unread(reviewBase),2);
const reviewed=toggleSavedReview(reviewBase,reviewA);
assert.equal(unread(reviewed),1);
assert.deepEqual(reviewed.savedItems,[{...reviewA,reviewed:true},reviewB]);
const reviewReload=readRegistry({getItem:key=>key===registryKey?JSON.stringify(reviewed):null});
assert.deepEqual(reviewReload,reviewed);
assert.equal(unread(toggleSavedReview(reviewReload,reviewA)),2);
assert.deepEqual(saveItem(reviewReload,reviewA),reviewReload);
const reviewRemoval=prepareSavedRemoval(reviewReload,reviewA)!;
assert.deepEqual(prepareSavedRestore(reviewRemoval.next,reviewRemoval.removal).next,reviewReload);
assert.equal(toggleSavedReview(reviewReload,undoD),reviewReload);
console.log("PASS: existing saved items unread; explicit review 2→1, reload, revert, unchanged content/order, duplicate save, delete undo preserves review and missing target no-op.");
