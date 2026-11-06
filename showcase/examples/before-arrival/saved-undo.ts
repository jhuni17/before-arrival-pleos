import type {ContentItem,SavedItem,Registry} from "./catalog";
export type SavedRemoval={item:SavedItem;index:number;before:string|null;after:string|null};
export function savedIdentity(item:ContentItem):string {
 if(item.originalUrl){try{return "url:"+new URL(item.originalUrl).href.replace(/\/$/,"");}catch{}}
 return `item:${item.sourceId}:${item.id}`;
}
export function prepareSavedRemoval(state:Registry,target:ContentItem):{next:Registry;removal:SavedRemoval}|null {
 const index=state.savedItems.findIndex(item=>savedIdentity(item)===savedIdentity(target));
 if(index<0)return null;
 const item=state.savedItems[index];
 return {next:{...state,savedItems:state.savedItems.filter((_,position)=>position!==index)},removal:{item:{...item},index,before:index? savedIdentity(state.savedItems[index-1]):null,after:index+1<state.savedItems.length?savedIdentity(state.savedItems[index+1]):null}};
}
export function prepareSavedRestore(state:Registry,removal:SavedRemoval):{next:Registry;alreadyPresent:boolean} {
 const key=savedIdentity(removal.item);
 if(state.savedItems.some(item=>savedIdentity(item)===key))return {next:state,alreadyPresent:true};
 const before=removal.before===null?-1:state.savedItems.findIndex(item=>savedIdentity(item)===removal.before);
 const after=removal.after===null?-1:state.savedItems.findIndex(item=>savedIdentity(item)===removal.after);
 const position=before>=0?before+1:after>=0?after:Math.min(removal.index,state.savedItems.length);
 const savedItems=[...state.savedItems];savedItems.splice(position,0,removal.item);
 return {next:{...state,savedItems},alreadyPresent:false};
}

export function toggleSavedReview(state:Registry,target:ContentItem):Registry {
 const key=savedIdentity(target);
 if(!state.savedItems.some(item=>savedIdentity(item)===key))return state;
 return {...state,savedItems:state.savedItems.map(item=>savedIdentity(item)===key?{...item,reviewed:item.reviewed!==true}:item)};
}
