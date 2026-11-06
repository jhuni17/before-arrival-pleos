import assert from 'node:assert/strict';
import {chromium} from 'playwright-core';
import {createServer} from 'node:http';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {createHash} from 'node:crypto';
const out=process.env.EVIDENCE_DIR||'validation/saved-search';await mkdir(out,{recursive:true});
const html=await readFile('before-arrival-showcase.html');const server=createServer((req,res)=>{res.writeHead(200,{'Content-Type':'text/html;charset=utf-8'});res.end(html);});await new Promise(r=>server.listen(0,'127.0.0.1',r));
const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/usr/bin/chromium',headless:true,args:['--no-sandbox']});const base=`http://127.0.0.1:${server.address().port}`;
const A='리그 오브 레전드 26.22 패치 노트',B='발로란트 13.09 패치 노트',C='수정2 클라이언트 1.2.420 업데이트 안내',D='이번 주말, 협동 모드가 열려요';
const key='before-arrival-sources-v2';const report={checkedAt:new Date().toISOString(),htmlSha256:createHash('sha256').update(html).digest('hex'),sourceSha256:{},upstream:'official snapshots registered through UI; no external network',checks:[],readability:[]};
for(const path of ['examples/before-arrival/index.tsx','examples/before-arrival/saved-undo.ts','examples/before-arrival/showcase.css','saved-search-browser-verify.mjs'])report.sourceSha256[path]=createHash('sha256').update(await readFile(path)).digest('hex');
const titles=page=>page.locator('.saved-article h3').allTextContents();const remove=(page,title)=>page.getByRole('button',{name:title+' 저장 취소',exact:true});const undo=page=>page.getByRole('button',{name:'삭제 실행 취소',exact:true});const notice=page=>page.locator('.saved-undo-status');const read=page=>page.evaluate(k=>JSON.parse(localStorage.getItem(k)),key);
async function tabTo(page,target){for(let i=0;i<90;i++){if(await target.evaluate(el=>el===document.activeElement))return;await page.keyboard.press('Tab');}throw new Error('Keyboard Tab did not reach target');}
async function saveDeferred(page,title){await page.getByRole('button',{name:'03 4분',exact:true}).click();await page.getByRole('button',{name:title+' 나중에 보기 저장',exact:true}).click();await page.getByRole('button',{name:'04 도착',exact:true}).click();}
async function failWrites(page,value){await page.evaluate(value=>window.__undoFailWrites=value,value);}
try{
 const page=await browser.newPage({viewport:{width:390,height:1080}});
 await page.goto(base+'/?state=settings');
 for(const [provider,name] of [['lol','롤'],['valorant','발로란트'],['maple','메이플']]){await page.getByLabel('출처 서비스',{exact:true}).selectOption(provider);await page.getByLabel('출처 이름',{exact:true}).fill(name);await page.getByRole('button',{name:'출처 추가',exact:true}).click();}
 for(const title of [A,B,C])await saveDeferred(page,title);
 const seed=await read(page);seed.savedItems.push({...seed.savedItems[0],id:'search-test',originalUrl:'https://www.leagueoflegends.com/ko-kr/news/test/',title:'Alpha PATCH 한글'});
 await page.evaluate(({key,seed})=>localStorage.setItem(key,JSON.stringify(seed)),{key,seed});await page.reload();
 let requests=0,popups=0;page.on('request',()=>requests++);page.on('popup',()=>popups++);
 await page.evaluate(()=>{const original=Storage.prototype.setItem;window.__writes=0;Storage.prototype.setItem=function(k,v){window.__writes++;if(window.__undoFailWrites)throw new DOMException('Denied','QuotaExceededError');return original.call(this,k,v);};});
 const search=page.getByRole('searchbox',{name:'저장한 제목 검색'}),clear=page.getByRole('button',{name:'검색 지우기',exact:true}),all=page.getByRole('button',{name:'전체',exact:true}),unread=page.getByRole('button',{name:'미확인만',exact:true});
 const review=title=>page.getByRole('button',{name:title+' 확인 완료',exact:true});
 await search.fill('  pAtCh  ');assert.deepEqual(await titles(page),['Alpha PATCH 한글']);
 await search.fill('패치');assert.deepEqual(await titles(page),[A,B]);assert.match(await page.locator('.saved-filters').innerText(),/검색 결과 · 표시 2건/);
 await search.fill('일치하지 않음');assert.match(await page.locator('.empty-saved').innerText(),/검색 결과가 없어요/);
 await clear.click();assert(await search.evaluate(el=>el===document.activeElement));assert.deepEqual(await titles(page),seed.savedItems.map(i=>i.title));
 assert.equal(await page.evaluate(()=>window.__writes),0);assert.equal(requests,0);assert.equal(popups,0);assert.deepEqual(await read(page),seed);
 await unread.click();await search.fill('패치');await failWrites(page,true);await tabTo(page,review(A));await page.keyboard.press('Enter');assert.deepEqual(await titles(page),[A,B]);assert(await review(A).evaluate(el=>el===document.activeElement));assert.match(await page.getByRole('alert').innerText(),/저장하지 못했어요/);
 await failWrites(page,false);await page.keyboard.press('Enter');assert.deepEqual(await titles(page),[B]);assert(await review(B).evaluate(el=>el===document.activeElement));
 await all.click();await page.getByRole('button',{name:A+' 미확인으로 되돌리기',exact:true}).click();await unread.click();await review(B).click();assert(await review(A).evaluate(el=>el===document.activeElement));await review(A).click();assert(await all.evaluate(el=>el===document.activeElement));assert.match(await page.locator('.empty-saved').innerText(),/검색 결과가 없어요/);
 await all.click();await remove(page,A).click();assert.deepEqual(await titles(page),[B]);await undo(page).click();assert.deepEqual(await titles(page),[A,B]);
 await page.getByRole('button',{name:'출처 설정',exact:true}).click();await page.getByRole('button',{name:'04 도착',exact:true}).click();assert.equal(await search.inputValue(),'패치');
 for(const blocked of ['주행 제한','상태 미확인']){const before=await read(page);await page.getByRole('button',{name:'01 18분',exact:true}).click();await page.getByRole('button',{name:'듣기 시연',exact:true}).click();await page.getByRole('button',{name:blocked,exact:true}).click();assert.equal(await search.count(),0);assert.equal(await page.locator('.saved-article').count(),0);assert.equal(await page.locator('.primary').getAttribute('aria-pressed'),'false');await page.getByRole('button',{name:'04 도착',exact:true}).click();assert.equal(await search.inputValue(),'패치');assert.deepEqual(await read(page),before);assert.equal(await page.locator('.primary').getAttribute('aria-pressed'),'false');}
 await unread.click();await page.reload();assert.equal(await search.inputValue(),'');assert.equal(await all.getAttribute('aria-pressed'),'true');
 // Browser composition events retain Korean text without submitting or processing items.
 await search.evaluate(el=>{el.dispatchEvent(new CompositionEvent('compositionstart',{bubbles:true,data:''}));});await search.fill('한글');await search.evaluate(el=>el.dispatchEvent(new CompositionEvent('compositionend',{bubbles:true,data:'한글'})));assert.deepEqual(await titles(page),['Alpha PATCH 한글']);
 await tabTo(page,clear);await page.keyboard.press('Enter');assert(await search.evaluate(el=>el===document.activeElement));
 for(const theme of ['day','night'])for(const viewport of [{width:320,height:960},{width:390,height:1080}]){await page.setViewportSize(viewport);await page.goto(base+'/?state=arrived&theme='+theme);await search.fill('패치');await page.evaluate(()=>document.fonts.ready);const audit=await page.locator('.saved-search').evaluate(el=>({overflow:el.scrollWidth>el.clientWidth+1,pageOverflow:document.documentElement.scrollWidth>innerWidth+1}));assert.equal(audit.overflow,false);assert.equal(audit.pageOverflow,false);await page.screenshot({path:out+'/'+theme+'-search-'+viewport.width+'.png',fullPage:true});report.readability.push({theme,viewport,...audit});}
 await search.fill('');await unread.click();await page.evaluate(k=>{const data=JSON.parse(localStorage.getItem(k));data.savedItems=data.savedItems.map(i=>({...i,reviewed:true}));localStorage.setItem(k,JSON.stringify(data));},key);await page.reload();await unread.click();await search.fill('없는 제목');assert.match(await page.locator('.empty-saved').innerText(),/미확인 소식이 없어요/);
 await page.evaluate(k=>{const data=JSON.parse(localStorage.getItem(k));data.savedItems=[];localStorage.setItem(k,JSON.stringify(data));},key);await page.reload();await search.fill('제목');assert.match(await page.locator('.empty-saved').innerText(),/저장한 소식이 없어요/);
 report.checks.push('Title substring, trim/case, Korean composition events, filter intersection/order, counts/three empty states, clear focus, no search writes/requests/popups, failed review focus, next/previous/filter focus, delete/undo, scene retention/reload reset, restricted/unknown stop and day/night narrow captures.');
 await writeFile(out+'/search-report.json',JSON.stringify(report,null,2)+'\n');console.log('PASS: saved title search');
}finally{await browser.close();server.closeAllConnections();await new Promise(r=>server.close(r));}
