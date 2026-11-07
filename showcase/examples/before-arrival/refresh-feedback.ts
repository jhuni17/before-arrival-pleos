import type {ContentItem} from "./catalog";
// Accepted source-cache items only: publication dates and saved/read state are irrelevant.
function identities(items:ContentItem[]):Set<string> {
 return new Set(items.map(item=>{
  if(item.originalUrl){try{return "url:"+new URL(item.originalUrl).href.replace(/\/$/,"");}catch{}}
  return `item:${item.sourceId}:${item.id}`;
 }));
}
export function completedRefreshFeedback(previous:ContentItem[],accepted:ContentItem[]):string {
 const current=identities(accepted);
 if(!current.size)return "현재 소식 없음";
 const prior=identities(previous);
 const added=[...current].filter(key=>!prior.has(key)).length;
 return added?`${added}개 추가됨`:`추가된 소식 없음 · ${current.size}개 확인`;
}
export function failedRefreshFeedback(previous:ContentItem[]):string {
 return previous.length?"확인 실패 · 이전 소식 유지":"확인 실패 · 보관한 소식 없음";
}
