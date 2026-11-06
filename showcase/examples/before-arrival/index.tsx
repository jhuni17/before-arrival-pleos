import { createContext, useContext, useEffect, useRef, useState } from "react";
import {completedRefreshFeedback,failedRefreshFeedback} from "./refresh-feedback";
import {savedIdentity,toggleSavedReview,prepareSavedRemoval,prepareSavedRestore,type SavedRemoval} from "./saved-undo";
import { Stage } from "../../kit/stage";
import type { Act } from "../../kit/act";
import { providers,defaults,registryKey,readRegistry,addSource,moveSource,toggleSource,planRegistry,saveItem,statusText,sourceName,refreshSteam,applyFeed,officialUrl,localSteamConnectionAvailable,type Registry,type ContentItem,type RegisteredSource } from "./catalog";
import "./showcase.css";
export type Frame = "opening"|"saved"|"priority"|"arrived"|"settings"|"restricted"|"unknown";
export type Scene = {frame:Frame};
export const frames:Frame[]=["opening","saved","priority","arrived","settings","restricted","unknown"];
export async function sequence(act:Act<Scene>) {
 act.mark("18-minutes-game");await act.wait(5000);await act.set({frame:"saved"});
 act.mark("save-simulation");await act.wait(3500);await act.set({frame:"priority"});
 act.mark("4-minutes-meeting");await act.wait(5000);await act.set({frame:"arrived"});
 act.mark("arrival-review");await act.wait(4500);
}
const RefreshFeedbackContext=createContext<Record<string,string>>({});
const SavedFilterContext=createContext<{query:string;search:(value:string)=>void;unreadOnly:boolean;select:(value:boolean)=>void}>({query:"",search:()=>{},unreadOnly:false,select:()=>{}});
const SavedUndoContext=createContext<{reviewPending:boolean;reviewNotice:string;undoReview:()=>void;pending:SavedRemoval|null;notice:string;focusKey:string|null;review:(item:ContentItem)=>boolean;remove:(item:ContentItem)=>void;restore:()=>void;focusDone:()=>void}>({reviewPending:false,reviewNotice:"",undoReview:()=>{},pending:null,notice:"",focusKey:null,review:()=>false,remove:()=>{},restore:()=>{},focusDone:()=>{}});
const sampleTime=(short:boolean)=>short?"2026-11-04T23:54:00Z":"2026-11-04T23:40:00Z";
function Glyph({kind}:{kind:"play"|"save"|"check"|"pause"}) {return <svg viewBox="0 0 24 24" width="24" height="24" fill="none" aria-hidden="true">{kind==="play"?<path d="m9 5 11 7-11 7V5Z" fill="currentColor"/>:kind==="check"?<path d="m4 12 5 5L20 6" stroke="currentColor" strokeWidth="2.5"/>:kind==="pause"?<path d="M8 5v14M16 5v14" stroke="currentColor" strokeWidth="4"/>:<path d="M6 3h12v18l-6-4-6 4V3Z" stroke="currentColor" strokeWidth="2"/>}</svg>}
function Published({item}:{item:ContentItem}) {
 return <span className="item-provenance">{item.provenance==="demo_sample"?"예시 콘텐츠 · 게시 시각 없음":<>{item.provenance==="official_runtime"?"실제로 가져온 공식 소식 · 원문 발췌":"가져온 공식 소식 · 자동 갱신 아님"}<br/>게시 {item.publishedAt||"시각 미제공"}{item.publishedPrecision==="date"?" · 날짜만 제공":""}<br/>확인 {item.fetchedAt||"시각 미확인"}</>}</span>;
}
function Vehicle({frame,state,save,long}:{frame:Frame;state:Registry;save:(item:ContentItem)=>void;long:boolean}) {
 const blocked=frame==="restricted"||frame==="unknown",short=frame==="priority",arrived=frame==="arrived";
 const plan=planRegistry(arrived?0:short?240:1080,sampleTime(short),state.sources),lead=plan.selected[0];
 const [playing,setPlaying]=useState(false);
 useEffect(()=>setPlaying(false),[frame,state]);
 useEffect(()=>{if(!playing)return;const timer=setTimeout(()=>setPlaying(false),4000);return()=>clearTimeout(timer);},[playing]);
 const title=lead?(long?`${lead.title} · 오늘 확인할 주요 변경 사항과 출근 전에 알아둘 소식`:lead.title):"";
 const isSaved=lead&&state.savedItems.some(i=>(i.originalUrl||i.id)===(lead.originalUrl||lead.id));
 return <section className="vehicle" aria-label="차량 화면" data-blocked={blocked}>
  <div className="system-bar"><span>PLEOS CONNECT <small>시스템 UI 예시</small></span><span>{arrived?"09:00":short?"08:54":"08:40"} · {frame==="unknown"?"?":blocked?"D":"P"}</span></div>
  <div className="application-window" aria-label="애플리케이션 영역">
   <header className="app-bar"><b>도착 전에</b><span className="status">{frame==="unknown"?"차량 상태 미확인 · 조작·음성 중지":blocked?"주행 제한 · 조작·음성 중지":"주차 상태 · 샘플"}</span></header>
   <div className="vehicle-content">
    <section className="eta-panel" aria-label="남은 시간"><span className="eyebrow">목적지 · 회사</span><div className="eta">{arrived?"도착":<><strong>{short?4:18}</strong><span>분</span></>}</div><p>{arrived?"저장한 소식을 확인하세요":"도착까지 남은 시간"}</p><div className="route-track" aria-hidden="true"><i/><span/><i/></div><span className="route-caption">경로·시간·주차 상태는 샘플</span></section>
    <section className="brief" aria-label="이번 브리핑">
     <div className="brief-meta"><span className="eyebrow">{blocked?"이용 일시 중지":short?"도착 전에 먼저 확인":"오늘의 대표 소식"}</span><span>{blocked?"안전 상태":arrived?"재생 종료":`예상 ${plan.seconds}초 · 예산 ${plan.budget}초`}</span></div>
     <div className="lead-card"><span className="category">{blocked?"주차 후 다시 이용":arrived?"도착 후 확인 · 연출":lead?sourceName(state,lead):"편성할 소식 없음"}</span>
      <h1>{blocked?frame==="unknown"?"차량 상태를 확인해 주세요":"안전하게 주차한 뒤 이용해 주세요":arrived?"저장한 소식을 확인하세요":lead?title:"설정한 출처와 듣기 예산을 확인해 주세요"}</h1>
      {!blocked&&!arrived&&lead&&<><Published item={lead}/>{lead.originalUrl&&officialUrl(state.sources.find(s=>s.id===lead.sourceId)!.provider,lead.originalUrl)&&<a className="original-link" href={lead.originalUrl} target="_blank" rel="noreferrer">공식 원문 확인 ↗</a>}</>}
      <button className="primary" disabled={blocked||!lead} onClick={()=>setPlaying(!playing)} aria-pressed={playing}><Glyph kind={playing?"pause":"play"}/>{playing?"시연 중지":"듣기 시연"}</button>
      <span className="play-status" role="status">{playing?"재생 상태 연출 · 실제 음성 없음":frame==="saved"?"저장 완료 장면 · 실제 저장은 버튼을 눌러요":"상태 연출만 제공 · 실제 음성 없음"}</span>
      <p>{blocked?"브리핑 화면 조작과 음성을 중지했어요.":arrived?"도착 후 보관함에서 저장한 항목을 확인하세요.":lead?lead.summary:"켜 둔 출처에 확인된 소식이 없거나 남은 듣기 예산을 넘었어요. 예시로 대체하지 않아요."}</p>
      <button className="secondary" disabled={blocked||!lead} onClick={()=>save(lead)}><Glyph kind={isSaved||frame==="saved"?"check":"save"}/>{isSaved?"이 브라우저에 저장됨":frame==="saved"?"저장 완료 · 연출":"이 브라우저 보관함에 저장"}</button>
     </div>
     <div className="next-up">{blocked?"제한 상태에서는 목록 전환과 재생을 중지해요.":arrived?"브라우저 보관함 · Android 앱과 동기화 안 됨":<>{short?"나중에 확인":"다음 소식"} · {(short?plan.deferred:plan.selected.slice(1)).map(i=>i.title).join(" · ")||"없음"}</>}</div>
     {!blocked&&!arrived&&plan.deferred.length>0&&<section className="deferred-items" aria-label="도착 후 이어볼 소식"><h2>나중에 볼 소식</h2>{plan.deferred.map(item=>{
      const saved=state.savedItems.some(i=>(i.originalUrl||i.sourceId+":"+i.id)===(item.originalUrl||item.sourceId+":"+item.id));
      return <article className="deferred-card" key={item.originalUrl||item.sourceId+item.id}><span className="category">{sourceName(state,item)}</span><h3>{item.title}</h3><Published item={item}/><button className="deferred-save" aria-label={`${item.title} 나중에 보기 저장`} disabled={saved} onClick={()=>save(item)}>{saved?"이 브라우저에 저장됨":"나중에 보기로 저장"}</button></article>;
     })}</section>}
    </section>
   </div><div className="app-boundary">현재 주소의 브라우저에만 저장 · Android 앱/다른 기기와 동기화 안 됨</div>
  </div><div className="system-nav" aria-label="시스템 내비게이션 예시"><span>앱 라이브러리</span><span>내비게이션</span><span>공조 22°</span><small>시스템 GNB 예시</small></div>
 </section>;
}
function SourceSettings({state,update,refresh,busy}:{state:Registry;update:(next:Registry)=>void;refresh:(source:RegisteredSource)=>void;busy:string[]}) {
 const feedback=useContext(RefreshFeedbackContext);
 const [provider,setProvider]=useState("lol"),[name,setName]=useState("리그 오브 레전드"),[url,setUrl]=useState(providers.find(p=>p.id==="lol")!.suggestedUrl),[error,setError]=useState("");
 return <><p>켜 둔 출처를 위에서부터 편성해요. 마감이 가까운 일정은 먼저예요.</p><ol className="source-list">{state.sources.map((source,index)=><li className="registered-source" key={source.id} data-source-id={source.id}>
  <label className="source"><span>{source.name}</span><input type="checkbox" aria-label={`${source.name} 사용`} checked={source.enabled} onChange={e=>update(toggleSource(state,source.id,e.target.checked))}/></label>
  <p className="source-status">{statusText(source)}{source.lastSuccessAt&&<><br/>마지막 확인 {source.lastSuccessAt}</>}</p>
  {source.items[0]?.fetchedAt&&source.status==="connected_snapshot"&&<p className="source-status">확인 {source.items[0].fetchedAt}</p>}
  {source.provider==="steam"&&<>{feedback[source.id]&&<span className="refresh-feedback-label">이 출처의 소식 목록</span>}<p className="refresh-feedback" role="status" aria-live="polite" aria-atomic="true" aria-label={`${source.name} 소식 목록 확인 결과`}>{feedback[source.id]||""}</p></>}
  <div className="source-actions"><button disabled={index===0} aria-label={`${source.name} 위로`} onClick={()=>update(moveSource(state,source.id,-1))}>위로</button><button disabled={index===state.sources.length-1} aria-label={`${source.name} 아래로`} onClick={()=>update(moveSource(state,source.id,1))}>아래로</button>{source.provider==="steam"&&<button disabled={!source.enabled||busy.includes(source.id)||!localSteamConnectionAvailable()} aria-label={`${source.name} 새 소식 확인`} onClick={()=>refresh(source)}>{busy.includes(source.id)?"확인 중…":"새 소식 확인"}</button>}</div>
  {source.registrationUrl&&<a href={source.registrationUrl} target="_blank" rel="noreferrer">등록한 공식 페이지 ↗</a>}
 </li>)}</ol>
 <form className="source-form" onSubmit={e=>{e.preventDefault();try{update(addSource(state,provider,name,url));setError("");}catch(error){setError((error as Error).message);}}}>
  <h3>내 출처 추가</h3><label>서비스<select aria-label="출처 서비스" value={provider} onChange={e=>{const next=providers.find(p=>p.id===e.target.value)!;setProvider(next.id);setUrl(next.suggestedUrl);setName(next.label);}}>{providers.map(p=><option key={p.id} value={p.id}>{p.label}</option>)}</select></label>
  <label>내가 부를 이름<input aria-label="출처 이름" maxLength={80} value={name} onChange={e=>setName(e.target.value)} required/></label>
  <label>공식 채널·게임·게시판 주소<input aria-label="출처 공식 주소" type="url" value={url} onChange={e=>setUrl(e.target.value)} required maxLength={512}/></label>
  <button type="submit">출처 추가</button>{error&&<p role="alert">{error}</p>}
  <p className="sample-note">공식 링크만 등록해요. 가져온 소식이 없는 출처는 연결 상태를 표시하고 브리핑을 만들지 않아요.</p>
 </form></>;
}
function Pocket({frame,state,update,refresh,busy}:{frame:Frame;state:Registry;update:(next:Registry)=>void;refresh:(source:RegisteredSource)=>void;busy:string[]}) {
 const filter=useContext(SavedFilterContext),filterButton=useRef<HTMLButtonElement>(null),reviewFocus=useRef<string|null>(null);
 const searchInput=useRef<HTMLInputElement>(null),query=filter.query.trim().toLowerCase();
 const filtered=state.savedItems.filter(item=>!filter.unreadOnly||item.reviewed!==true);
 const visible=filtered.filter(item=>item.title.toLowerCase().includes(query));
 const undo=useContext(SavedUndoContext),undoButton=useRef<HTMLButtonElement>(null),pocket=useRef<HTMLElement>(null);
 useEffect(()=>{if(frame==="arrived"&&undo.pending)undoButton.current?.focus();},[frame,undo.pending]);
 useEffect(()=>{
  if(frame!=="arrived"||!undo.focusKey)return;
  const article=[...(pocket.current?.querySelectorAll<HTMLElement>(".saved-article")||[])].find(node=>node.dataset.savedKey===undo.focusKey);
  (article?.querySelector<HTMLElement>("h3")||filterButton.current)?.focus();undo.focusDone();
 },[frame,undo.focusKey]);
 useEffect(()=>{
  if(reviewFocus.current===null)return;
  const article=[...(pocket.current?.querySelectorAll<HTMLElement>(".saved-article")||[])].find(node=>node.dataset.savedKey===reviewFocus.current);
  (article?.querySelector<HTMLButtonElement>(".saved-review")||filterButton.current)?.focus();reviewFocus.current=null;
 },[state.savedItems]);
 function review(item:ContentItem) {
  const index=visible.findIndex(candidate=>savedIdentity(candidate)===savedIdentity(item));
  const neighbor=visible[index+1]||visible[index-1];
  if(undo.review(item)&&filter.unreadOnly)reviewFocus.current=neighbor?savedIdentity(neighbor):"";
 }
 return <aside className="phone" ref={pocket} aria-label={frame==="settings"?"이 브라우저 출처 설정":"이 브라우저 도착 후 확인"}><div className="phone-system">{frame==="settings"?"8:30":"9:00"}<span>휴대폰 모형 · 브라우저에서 동작</span></div><div className="phone-app"><span className="eyebrow">도착 전에</span><h2>{frame==="settings"?"내 출근길 출처":"도착해서 보기"}</h2><p className="local-only">현재 주소의 브라우저에만 저장 · Android 앱/다른 기기와 동기화 안 됨</p>
 {frame==="arrived"&&<div className="saved-search"><label htmlFor="saved-title-search">저장한 제목 검색</label><input ref={searchInput} id="saved-title-search" type="search" value={filter.query} onChange={e=>filter.search(e.target.value)}/><button onClick={()=>{filter.search("");searchInput.current?.focus();}}>검색 지우기</button></div>}
 {frame==="arrived"&&<div className="saved-filters" role="group" aria-label="보관함 필터"><button ref={filterButton} aria-pressed={!filter.unreadOnly} onClick={()=>filter.select(false)}>전체</button><button aria-pressed={filter.unreadOnly} onClick={()=>filter.select(true)}>미확인만</button><span role="status">{query?"검색 결과 · ":""}표시 {visible.length}건</span></div>}
 {frame==="arrived"&&<p className="saved-count" role="status">미확인 {state.savedItems.filter(item=>item.reviewed!==true).length}건</p>}
 {frame==="arrived"&&<section className="saved-review-undo" data-active={Boolean(undo.reviewNotice)} aria-label="최근 확인 실행 취소"><p role="status" aria-live="polite" aria-atomic="true">{undo.reviewNotice}</p>{undo.reviewPending&&<button className="saved-undo-button" onClick={undo.undoReview}>확인 실행 취소</button>}</section>}
 {frame==="arrived"&&<section className="saved-undo" data-active={Boolean(undo.notice||undo.pending)} aria-label="최근 삭제 실행 취소"><p className="saved-undo-status" role="status" aria-live="polite" aria-atomic="true">{undo.notice}</p>{undo.pending&&<button className="saved-undo-button" ref={undoButton} onClick={undo.restore}>삭제 실행 취소</button>}</section>}
 {frame==="settings"?<SourceSettings state={state} update={update} refresh={refresh} busy={busy}/>:visible.length?visible.map(item=><article className="saved-article" data-saved-key={savedIdentity(item)} key={item.originalUrl||item.sourceId+item.id}><span className="category">{sourceName(state,item)}</span><h3 tabIndex={-1}>{item.title}</h3><p>{item.summary}</p><Published item={item}/>{item.originalUrl&&<a href={item.originalUrl} target="_blank" rel="noreferrer">공식 원문 확인 ↗</a>}<p className="saved-review-status">{item.reviewed===true?"확인 완료":"미확인"}</p><button className="saved-review" aria-label={`${item.title} ${item.reviewed===true?"미확인으로 되돌리기":"확인 완료"}`} onClick={()=>review(item)}>{item.reviewed===true?"미확인으로 되돌리기":"확인 완료"}</button><button aria-label={`${item.title} 저장 취소`} onClick={()=>undo.remove(item)}>저장 취소</button></article>):state.savedItems.length&&query&&filtered.length?<p className="empty-saved">검색 결과가 없어요. 제목 검색어를 바꾸거나 지워 주세요.</p>:state.savedItems.length?<p className="empty-saved">미확인 소식이 없어요. <button onClick={()=>filter.select(false)}>전체 보기로 돌아가기</button></p>:<p className="empty-saved">저장한 소식이 없어요. 차량 모형에서 저장 버튼을 누르면 이 브라우저 보관함에 남아요. 자동 시연은 실제 저장을 만들지 않아요.</p>}
 <div className="phone-scope">브라우저 보관함 · 기기 간 동기화 미구현</div></div><div className="phone-home" aria-hidden="true"/></aside>;
}
function SceneView({scene,state,update,refresh,busy,long}:{scene:Scene;state:Registry;update:(next:Registry)=>void;refresh:(source:RegisteredSource)=>void;busy:string[];long:boolean}) {
 return <><div className="scene-composition" data-phone={scene.frame==="settings"||scene.frame==="arrived"} data-arrived={scene.frame==="arrived"}>{scene.frame==="arrived"&&<Pocket frame={scene.frame} state={state} update={update} refresh={refresh} busy={busy}/>}<Vehicle frame={scene.frame} state={state} save={item=>update(saveItem(state,item))} long={long}/>{scene.frame==="settings"&&<Pocket frame={scene.frame} state={state} update={update} refresh={refresh} busy={busy}/>}</div><div className="scene-caption" role="status">{scene.frame==="saved"?"02 / 저장 · 자동 시연은 저장 완료 장면만 연출합니다.":scene.frame==="priority"?"03 / 4분 · 남은 듣기 예산과 일정 마감으로 다시 편성합니다.":scene.frame==="arrived"?"04 / 도착 · 이 브라우저 보관함을 확인합니다.":scene.frame==="settings"?"설정 / 출처와 순서는 이 브라우저에서만 보존됩니다.":scene.frame==="restricted"||scene.frame==="unknown"?"안전 / 화면 조작과 음성을 중지합니다.":"01 / 18분 · 켜 둔 출처와 순서대로 브리핑을 준비합니다."}</div></>;
}
export function Showcase({state:initialState}:{state:string|null}) {
 const params=typeof location==="undefined"?new URLSearchParams():new URLSearchParams(location.search);
 const [theme,setTheme]=useState(params.get("theme")==="day"?"day":"night"),[manual,setManual]=useState<Frame|null>(frames.includes(initialState as Frame)?initialState as Frame:null),[state,setState]=useState<Registry>(defaults),[replay,setReplay]=useState(0),[error,setError]=useState(""),[busy,setBusy]=useState<string[]>([]),[feedback,setFeedback]=useState<Record<string,string>>({}),[pendingUndo,setPendingUndo]=useState<SavedRemoval|null>(null),[undoNotice,setUndoNotice]=useState(""),[undoFocusKey,setUndoFocusKey]=useState<string|null>(null);
 const [reviewNotice,setReviewNotice]=useState(""),[reviewPending,setReviewPending]=useState(false);
 const reviewUndoRef=useRef<Registry["savedItems"][number]|null>(null);
 const current=useRef(state);current.current=state;
 const filterKey="before-arrival-saved-filter-v1";
 const [unreadOnly,setUnreadOnly]=useState(false),[query,setQuery]=useState("");
 useEffect(()=>{try{setUnreadOnly(localStorage.getItem(filterKey)==="unread");}catch{/* Storage access can be denied; default to all. */}},[]);
 function selectSavedFilter(value:boolean) {
  if(!allowedRef.current)return;
  setUnreadOnly(value);
  try{localStorage.setItem(filterKey,value?"unread":"all");setError("");}
  catch{setError("필터를 저장하지 못했어요. 현재 화면에만 적용되며 다음 방문에는 유지되지 않을 수 있어요.");}
 }
 const pendingUndoRef=useRef<SavedRemoval|null>(null);
 const jobs=useRef(new Map<string,AbortController>());
 const allowed=manual!=="restricted"&&manual!=="unknown";
 const allowedRef=useRef(allowed);allowedRef.current=allowed;
 useEffect(()=>{try{const restored=readRegistry(localStorage);current.current=restored;setState(restored);}catch{/* Storage access can be denied; defaults remain visible. */}},[]);
 useEffect(()=>{
  for(const [id,job] of jobs.current) if(!allowed||!state.sources.some(s=>s.id===id&&s.enabled)){job.abort();jobs.current.delete(id);setBusy(old=>old.filter(v=>v!==id));}
  setFeedback(old=>{const kept=Object.fromEntries(Object.entries(old).filter(([id])=>allowed&&state.sources.some(s=>s.id===id&&s.enabled)));return Object.keys(kept).length===Object.keys(old).length?old:kept;});
 },[allowed,state.sources]);
 useEffect(()=>{if(!allowed){reviewUndoRef.current=null;setReviewPending(false);setReviewNotice("");pendingUndoRef.current=null;setPendingUndo(null);setUndoNotice("");setUndoFocusKey(null);}},[allowed]);
 useEffect(()=>()=>{for(const job of jobs.current.values())job.abort();},[]);
 function update(next:Registry) {
  if(!allowedRef.current)return false;
  try{localStorage.setItem(registryKey,JSON.stringify(next));current.current=next;setState(next);setError("");if(reviewUndoRef.current&&!next.savedItems.includes(reviewUndoRef.current)){reviewUndoRef.current=null;setReviewPending(false);setReviewNotice("");}return true;}catch{setError("저장하지 못했어요. 브라우저 저장 공간을 확인해 주세요.");return false;}
 }
 function reviewSaved(item:ContentItem) {
  if(!allowedRef.current)return false;
  const next=toggleSavedReview(current.current,item);
  if(next===current.current||!update(next))return false;
  const reviewed=next.savedItems.find(saved=>savedIdentity(saved)===savedIdentity(item));
  if(reviewed?.reviewed===true){reviewUndoRef.current=reviewed;setReviewPending(true);setReviewNotice(`「${reviewed.title}」 확인 완료했어요.`);}
  return true;
 }
 function undoReview() {
  const target=reviewUndoRef.current;
  if(!allowedRef.current||!target||!current.current.savedItems.includes(target))return;
  const next={...current.current,savedItems:current.current.savedItems.map(item=>item===target?{...item,reviewed:false}:item)};
  if(!update(next))return;
  setReviewNotice(`「${target.title}」을 미확인으로 되돌렸어요.`);setUndoFocusKey(savedIdentity(target));
 }
 function removeSaved(item:ContentItem) {
  if(!allowedRef.current)return;
  const deletion=prepareSavedRemoval(current.current,item);
  if(!deletion||!update(deletion.next))return;
  pendingUndoRef.current=deletion.removal;setPendingUndo(deletion.removal);setUndoFocusKey(null);
  setUndoNotice(`「${deletion.removal.item.title}」을 보관함에서 뺐어요.`);
 }
 function restoreSaved() {
  if(!allowedRef.current||!pendingUndoRef.current)return;
  const removal=pendingUndoRef.current,restored=prepareSavedRestore(current.current,removal);
  if(!restored.alreadyPresent&&!update(restored.next))return;
  pendingUndoRef.current=null;setPendingUndo(null);setUndoFocusKey(savedIdentity(removal.item));
  setUndoNotice(restored.alreadyPresent?"이미 보관함에 있는 소식이에요.":`「${removal.item.title}」을 다시 보관했어요.`);
 }
 async function refresh(source:RegisteredSource) {
  if(!allowed||!source.enabled||jobs.current.has(source.id))return;
  const job=new AbortController();jobs.current.set(source.id,job);setBusy(old=>[...old,source.id]);
  setFeedback(old=>{const next={...old};delete next[source.id];return next;});
  const timer=setTimeout(()=>job.abort(),6000),attemptedAt=new Date().toISOString();
  try{
   const result=await refreshSteam(source,job.signal);
   if(allowedRef.current&&jobs.current.get(source.id)===job&&!job.signal.aborted&&current.current.sources.some(s=>s.id===source.id&&s.enabled)) {
    const previous=current.current.sources.find(s=>s.id===source.id)!;
    const next=applyFeed(current.current,source.id,{items:result.items},result.fetchedAt);
    const accepted=next.sources.find(s=>s.id===source.id)!;
    if(update(next))setFeedback(old=>({...old,[source.id]:accepted.refreshError?failedRefreshFeedback(previous.items):completedRefreshFeedback(previous.items,accepted.items)}));
   }
  } catch {
   if(allowedRef.current&&jobs.current.get(source.id)===job&&current.current.sources.some(s=>s.id===source.id&&s.enabled)) {
    const previous=current.current.sources.find(s=>s.id===source.id)!;
    if(update(applyFeed(current.current,source.id,{error:"failed"},attemptedAt)))setFeedback(old=>({...old,[source.id]:failedRefreshFeedback(previous.items)}));
   }
  } finally {clearTimeout(timer);if(jobs.current.get(source.id)===job){jobs.current.delete(source.id);setBusy(old=>old.filter(id=>id!==source.id));}}
 }
 const render=(scene:Scene)=><SceneView scene={scene} state={state} update={update} refresh={refresh} busy={busy} long={params.get("long")==="1"}/>;
 return <SavedFilterContext.Provider value={{query,search:value=>{if(allowedRef.current)setQuery(value);},unreadOnly,select:selectSavedFilter}}><RefreshFeedbackContext.Provider value={feedback}><SavedUndoContext.Provider value={{reviewPending,reviewNotice,undoReview,pending:pendingUndo,notice:undoNotice,focusKey:undoFocusKey,review:reviewSaved,remove:removeSaved,restore:restoreSaved,focusDone:()=>setUndoFocusKey(null)}}><main className="ba-page" data-theme={theme}><div className="page-inner"><header className="masthead"><span>도착 전에 <small>PLEOS CONNECT · 샘플 경로 시연</small></span><button onClick={()=>setTheme(theme==="night"?"day":"night")}>{theme==="night"?"주간으로 보기":"야간으로 보기"}</button></header><div className="intro"><span className="eyebrow">내가 고른 출처, 남은 시간에 맞게</span><h2>남은 시간만큼,<br/>필요한 소식부터.</h2></div><nav className="scene-controls" aria-label="시연 장면">{([['opening','01 18분'],['saved','02 저장'],['priority','03 4분'],['arrived','04 도착'],['settings','출처 설정'],['restricted','주행 제한'],['unknown','상태 미확인']] as const).map(([frame,label])=><button key={frame} aria-pressed={manual===frame} onClick={()=>setManual(frame)}>{label}</button>)}<button onClick={()=>{setManual(null);setReplay(replay+1);}}>자동 시연 다시 보기</button></nav>{error&&<p className="storage-error" role="alert">{error}</p>}<Stage key={`${manual}-${replay}`} width={1440} height={680} className="ba-scene" theme={theme==="night"?"dark":"light"} play="once" initial={{frame:manual||"opening"} as Scene} poster={{frame:manual||"arrived"} as Scene} script={manual?undefined:sequence}>{render}</Stage><footer className="evidence-note">경로·차량 상태·재생은 샘플 · 소식마다 실제/예시와 확인 시각을 표시<br/>출처·저장은 이 브라우저에만 보존 · Android 앱/다른 기기와 동기화 안 됨</footer></div></main></SavedUndoContext.Provider></RefreshFeedbackContext.Provider></SavedFilterContext.Provider>;
}
