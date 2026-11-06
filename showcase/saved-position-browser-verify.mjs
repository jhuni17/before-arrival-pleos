import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {chromium} from 'playwright-core';
import {createServer} from 'node:http';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {createHash} from 'node:crypto';
const out=process.env.EVIDENCE_DIR||'validation/saved-position';await mkdir(out,{recursive:true});
const html=await readFile('before-arrival-showcase.html');const server=createServer((req,res)=>{res.writeHead(200,{'Content-Type':'text/html;charset=utf-8'});res.end(html);});await new Promise(r=>server.listen(0,'127.0.0.1',r));
const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/usr/bin/chromium',headless:true,args:['--no-sandbox']});const base=`http://127.0.0.1:${server.address().port}`;
const A='리그 오브 레전드 26.22 패치 노트',B='발로란트 13.09 패치 노트',C='수정2 클라이언트 1.2.420 업데이트 안내';
const key='before-arrival-sources-v2';const report={candidateSha:process.env.CANDIDATE_SHA||execFileSync('git',['rev-parse','HEAD'],{encoding:'utf8'}).trim(),workingTreeDirty:Boolean(execFileSync('git',['status','--porcelain'],{encoding:'utf8'}).trim()),scope:'Browser sample only; no APK installation, emulator or actual Pleos SDK evidence.',captures:[],checkedAt:new Date().toISOString(),htmlSha256:createHash('sha256').update(html).digest('hex'),sourceSha256:{},upstream:'official snapshots registered through UI; no external network',checks:[],readability:[]};
for(const path of ['examples/before-arrival/index.tsx','examples/before-arrival/saved-undo.ts','examples/before-arrival/showcase.css','saved-position-browser-verify.mjs','package.json'])report.sourceSha256[path]=createHash('sha256').update(await readFile(path)).digest('hex');
const read=page=>page.evaluate(k=>JSON.parse(localStorage.getItem(k)),key);
async function tabTo(page,target){for(let i=0;i<220;i++){if(await target.evaluate(el=>el===document.activeElement))return;await page.keyboard.press('Tab');}throw new Error('Keyboard Tab did not reach target');}
async function saveDeferred(page,title){await page.getByRole('button',{name:'03 4분',exact:true}).click();await page.getByRole('button',{name:title+' 나중에 보기 저장',exact:true}).click();await page.getByRole('button',{name:'04 도착',exact:true}).click();}
try{
 const page=await browser.newPage({viewport:{width:390,height:1080}});page.setDefaultNavigationTimeout(60000);
 await page.goto(base+'/?state=settings');
 for(const [provider,name] of [['lol','롤'],['valorant','발로란트'],['maple','메이플']]){await page.getByLabel('출처 서비스',{exact:true}).selectOption(provider);await page.getByLabel('출처 이름',{exact:true}).fill(name);await page.getByRole('button',{name:'출처 추가',exact:true}).click();}
 for(const title of [A,B,C])await saveDeferred(page,title);
 const seed=await read(page),lolItem=seed.savedItems.find(item=>item.title===A&&seed.sources.some(source=>source.id===item.sourceId&&source.provider==='lol'));
 assert(lolItem,'Expected the saved League of Legends item for the synthetic search fixture');
 const fixture={...seed,savedItems:Array.from({length:32},(_,i)=>({...seed.savedItems[i%3],id:`item-next-${i}`,title:`긴 보관함 ${i%2?'홀수':'짝수'} 소식 ${i}`,originalUrl:i%3===2?`https://maplestory.nexon.com/news/update/${9000+i}`:seed.savedItems[i%3].originalUrl.replace(/[^/]+\/$/,'')+`item-next-${i}/`,reviewed:true}))};
 const button=i=>page.getByRole('button',{name:fixture.savedItems[i].title+' 다음 미확인 소식',exact:true});
 const search=page.getByRole('searchbox',{name:'저장한 제목 검색',exact:true});
 async function load(unread){fixture.savedItems.forEach((item,i)=>item.reviewed=!unread.includes(i));await page.evaluate(({key,seed})=>localStorage.setItem(key,JSON.stringify(seed)),{key,seed:fixture});await page.goto(base+'/?state=arrived');await page.getByLabel('저장한 출처',{exact:true}).selectOption('');await page.getByRole('button',{name:'전체',exact:true}).click();await button(31).waitFor();}
 const mark=i=>page.getByRole('button',{name:fixture.savedItems[i].title+' 여기까지 봤어요',exact:true});
 const clear=()=>page.getByRole('button',{name:'지난 위치 지우기',exact:true});
 const resume=()=>page.getByRole('button',{name:'지난 위치로 이동',exact:true});
 const reveal=()=>page.getByRole('button',{name:'조건 지우고 지난 위치로 이동',exact:true});
 let popups=0;page.on('popup',()=>popups++);
 const positionKey='before-arrival-saved-position-v1';
 const position=()=>page.evaluate(k=>localStorage.getItem(k),positionKey);
 if(!process.env.POSITION_CONTINUE_ONLY){
 await load([2,17,31]);assert(await resume().isDisabled());assert.equal(await reveal().count(),0);
 for(const i of [17,31]){await mark(i).click();assert.equal(await position(),'url:'+fixture.savedItems[i].originalUrl.replace(/\/$/,''));assert.deepEqual(await read(page),fixture);}
 await page.reload();await resume().waitFor();assert.notEqual(await page.evaluate(()=>document.activeElement.textContent),fixture.savedItems[31].title);
 await page.emulateMedia({reducedMotion:'reduce'});await resume().click();assert.equal(await page.evaluate(()=>document.activeElement.textContent),fixture.savedItems[31].title);
 for(const source of ['',fixture.savedItems[31].sourceId,fixture.savedItems[0].sourceId])for(const query of ['','홀수','짝수'])for(const unread of [false,true]){
  await page.getByLabel('저장한 출처',{exact:true}).selectOption(source);await search.fill(query);await page.getByRole('button',{name:unread?'미확인만':'전체',exact:true}).click();
  const hidden=Boolean(source&&source!==fixture.savedItems[31].sourceId)||!fixture.savedItems[31].title.includes(query);
  assert.equal(await resume().isDisabled(),hidden);if(hidden)assert.match(await page.locator('#saved-position-help').innerText(),/필터/);assert.deepEqual(await read(page),fixture);
 }
 // Every individual filter and their combinations can explicitly reveal the exact position.
 for(const mask of [1,2,3,4,5,6,7]){
  await load([2,17,31]);await mark(18).click();const before=await position();
  if(mask&1)await search.fill('홀수');
  if(mask&2)await page.getByLabel('저장한 출처',{exact:true}).selectOption(fixture.savedItems[19].sourceId);
  if(mask&4)await page.getByRole('button',{name:'미확인만',exact:true}).click();
  assert.equal(await reveal().count(),1);
  await tabTo(page,reveal());await page.keyboard.press(mask%2?'Enter':'Space');
  assert.equal(await page.evaluate(()=>document.activeElement.textContent),fixture.savedItems[18].title);
  assert.equal(await search.inputValue(),'');assert.equal(await page.getByLabel('저장한 출처',{exact:true}).inputValue(),'');
  assert.equal(await page.getByRole('button',{name:'전체',exact:true}).getAttribute('aria-pressed'),'true');
  assert.equal(await reveal().count(),0);assert.deepEqual(await read(page),fixture);assert.equal(await position(),before);
  await page.reload();assert.equal(await reveal().count(),0);assert.notEqual(await page.evaluate(()=>document.activeElement.textContent),fixture.savedItems[18].title);
 }
 for(const failedKey of ['before-arrival-saved-source-v1','before-arrival-saved-filter-v1']){
  await load([2,17,31]);await mark(18).click();await search.fill('홀수');
  await page.getByLabel('저장한 출처',{exact:true}).selectOption(fixture.savedItems[19].sourceId);
  await page.getByRole('button',{name:'미확인만',exact:true}).click();
  await page.evaluate(k=>{window.filterWrite=Storage.prototype.setItem;Storage.prototype.setItem=function(key,value){if(key===k)throw Error('denied');return window.filterWrite.call(this,key,value);};},failedKey);
  await reveal().click();assert.equal(await page.evaluate(()=>document.activeElement.textContent),fixture.savedItems[18].title);
  assert.match(await page.locator(failedKey.includes('source')?'.saved-source-notice':'.storage-error').innerText(),/현재 화면에만 적용.*다음 방문에는 유지되지 않을 수/);
  assert.deepEqual(await read(page),fixture);await page.evaluate(()=>Storage.prototype.setItem=window.filterWrite);
 }
 for(const blocked of ['주행 제한','상태 미확인']){
  await load([2,17,31]);await mark(18).click();await search.fill('홀수');
  const stale=await reveal().evaluateHandle(el=>el[Object.keys(el).find(k=>k.startsWith('__reactProps$'))].onClick);
  // Batch reset and safety transition before the pending effect can move focus.
  await page.evaluate(label=>{const click=el=>el[Object.keys(el).find(k=>k.startsWith('__reactProps$'))].onClick();click([...document.querySelectorAll('button')].find(el=>el.textContent==='조건 지우고 지난 위치로 이동'));click([...document.querySelectorAll('button')].find(el=>el.textContent===label));},blocked);
  const snapshot=await page.evaluate(()=>JSON.stringify(localStorage));await stale.evaluate(fn=>fn());assert.equal(await page.evaluate(()=>JSON.stringify(localStorage)),snapshot);
  assert.equal(await reveal().count(),0);assert.equal(await page.locator('.primary').getAttribute('aria-pressed'),'false');
  await page.getByRole('button',{name:'04 도착',exact:true}).click();const focus=await page.evaluate(()=>document.activeElement.textContent);
  await stale.evaluate(fn=>fn());assert.equal(await page.evaluate(()=>document.activeElement.textContent),focus);assert.notEqual(focus,fixture.savedItems[18].title);
 }
 await load([2,17,31]);await mark(18).click();await page.getByRole('button',{name:'미확인만',exact:true}).click();assert(await resume().isDisabled());assert.match(await page.locator('#saved-position-help').innerText(),/필터/);
 await page.getByRole('button',{name:'전체',exact:true}).click();await mark(31).click();
 await page.evaluate(()=>{window.originalPositionWrite=Storage.prototype.setItem;Storage.prototype.setItem=function(k,v){if(k==='before-arrival-saved-position-v1')throw Error('denied');return window.originalPositionWrite.call(this,k,v);};});
 const previous=await position();await mark(17).click();assert.match(await page.locator('.saved-position [role=status]').last().innerText(),/저장하지 못/);assert.equal(await position(),previous);
 await page.evaluate(()=>Storage.prototype.setItem=window.originalPositionWrite);await mark(17).click();assert.match(await page.locator('.saved-position [role=status]').last().innerText(),/저장했/);
 for(const blocked of ['주행 제한','상태 미확인']){
  const stale=[];for(const control of [mark(31),resume(),clear()])stale.push(await control.evaluateHandle(el=>el[Object.keys(el).find(k=>k.startsWith('__reactProps$'))].onClick));
  const before=await position();await page.getByRole('button',{name:blocked,exact:true}).click();
  for(const fn of stale)await fn.evaluate(fn=>fn());assert.equal(await position(),before);assert.equal(await page.locator('.saved-position').count(),0);assert.equal(await page.locator('.primary').getAttribute('aria-pressed'),'false');
  await page.getByRole('button',{name:'04 도착',exact:true}).click();const focus=await page.evaluate(()=>document.activeElement.textContent);
  for(const fn of stale)await fn.evaluate(fn=>fn());assert.equal(await position(),before);assert.equal(await page.evaluate(()=>document.activeElement.textContent),focus);assert.equal(await page.locator('.primary').getAttribute('aria-pressed'),'false');
 }
 await page.getByRole('button',{name:fixture.savedItems[17].title+' 저장 취소',exact:true}).click();assert(await resume().isDisabled());assert.match(await page.locator('#saved-position-help').innerText(),/삭제/);assert.equal(await reveal().count(),0);
 await page.getByRole('button',{name:'삭제 실행 취소',exact:true}).click();assert.equal(await resume().isDisabled(),false);assert.deepEqual(await read(page),fixture);
 // Clear existing, filtered-out and deleted targets, preserving all other storage.
 async function otherStorage(){return page.evaluate(k=>Object.fromEntries(Object.entries(localStorage).filter(([name])=>name!==k)),positionKey);}
 for(const target of ['normal','hidden','deleted']){
  await load([2,17,31]);await mark(17).click();
  if(target==='hidden')await search.fill('짝수');
  if(target==='deleted')await page.getByRole('button',{name:fixture.savedItems[17].title+' 저장 취소',exact:true}).click();
  const before=await otherStorage(),reason=await page.locator('#saved-position-help').innerText(),saved=await position();
  await page.evaluate(()=>{window.originalRemove=Storage.prototype.removeItem;Storage.prototype.removeItem=function(k){if(k==='before-arrival-saved-position-v1')throw Error('denied');return window.originalRemove.call(this,k);};});
  await clear().click();assert.equal(await position(),saved);assert.equal(await page.locator('#saved-position-help').innerText(),reason);assert.match(await page.locator('.saved-position [role=status]').last().innerText(),/재시도/);assert.deepEqual(await otherStorage(),before);
  await page.evaluate(()=>Storage.prototype.removeItem=window.originalRemove);
  await tabTo(page,clear());await page.keyboard.press(target==='hidden'?'Space':'Enter');
  assert.equal(await position(),null);assert.equal(await clear().count(),0);assert(await resume().isDisabled());assert(await resume().evaluate(el=>el===document.activeElement));assert.equal(await page.locator('#saved-position-help').innerText(),'');assert.equal(await page.locator('.saved-position [role=status]').last().innerText(),'');assert.deepEqual(await otherStorage(),before);
  await page.reload();assert.equal(await position(),null);assert.equal(await clear().count(),0);assert(await resume().isDisabled());assert.deepEqual(await otherStorage(),before);assert.equal(await page.locator('.primary').getAttribute('aria-pressed'),'false');
 }
 }
 // Explicit continuation uses the full saved order, excluding the anchor even after wrap.
 const onward=()=>page.getByRole('button',{name:'지난 위치 다음 미확인부터 이어보기',exact:true});
 async function continueCheck(target,method='Enter'){
  const before=await page.evaluate(()=>JSON.stringify(localStorage));
  await onward().focus();await page.keyboard.press(method);
  assert.equal(await page.evaluate(()=>document.activeElement.textContent),fixture.savedItems[target].title);
  assert.equal(await page.evaluate(()=>JSON.stringify(localStorage)),before);
  assert.equal(await page.locator('.primary').getAttribute('aria-pressed'),'false');
 }
 await load([2,17,31]);await mark(17).click();await clear().click();assert(await onward().isDisabled());assert.match(await page.locator('#saved-position-continue-help').innerText(),/없어요/);
 await mark(17).click();await continueCheck(31);await mark(31).click();await continueCheck(2,'Space');
 await page.reload();assert.notEqual(await page.evaluate(()=>document.activeElement.textContent),fixture.savedItems[2].title);await continueCheck(2);
 for(const source of ['',fixture.savedItems[2].sourceId])for(const query of ['','짝수'])for(const mode of ['전체','미확인만','확인 완료만']){
  await page.getByLabel('저장한 출처',{exact:true}).selectOption(source);await search.fill(query);await page.getByRole('button',{name:mode,exact:true}).click();
  if(mode==='확인 완료만'){assert(await onward().isDisabled());assert.match(await page.locator('#saved-position-continue-help').innerText(),/없어요/);}else await continueCheck(2);
 }
 await load([17]);await mark(17).click();assert(await onward().isDisabled());
 await page.getByRole('button',{name:fixture.savedItems[17].title+' 저장 취소',exact:true}).click();assert(await onward().isDisabled());assert.match(await page.locator('#saved-position-continue-help').innerText(),/삭제/);
 for(const blocked of ['주행 제한','상태 미확인']){
  await load([2,17,31]);await mark(17).click();const stale=await onward().evaluateHandle(el=>el[Object.keys(el).find(k=>k.startsWith('__reactProps$'))].onClick);
  await page.getByRole('button',{name:blocked,exact:true}).click();const snapshot=await page.evaluate(()=>JSON.stringify(localStorage));await stale.evaluate(fn=>fn());
  await page.getByRole('button',{name:'04 도착',exact:true}).click();const focus=await page.evaluate(()=>document.activeElement.textContent);await stale.evaluate(fn=>fn());
  assert.equal(await page.evaluate(()=>document.activeElement.textContent),focus);assert.equal(await page.evaluate(()=>JSON.stringify(localStorage)),snapshot);assert.equal(await page.locator('.primary').getAttribute('aria-pressed'),'false');
 }
 for(const theme of ['day','night'])for(const width of [320,390]){
  const height=width===320?960:1080;await load([2,17,31]);await page.setViewportSize({width,height});await page.goto(base+'/?state=arrived&theme='+theme);await mark(17).click();await page.evaluate(()=>{scrollTo(0,0);return document.fonts.ready;});
  const heading=await page.locator('.saved-article h3').first().boundingBox();assert(heading&&heading.y>=0&&heading.y+heading.height<=height,JSON.stringify({theme,width,heading}));
  const metrics=await onward().evaluate(el=>{
   const rgb=s=>s.match(/[\d.]+/g).slice(0,3).map(Number);
   const lum=s=>rgb(s).map(v=>{v/=255;return v<=.04045?v/12.92:((v+.055)/1.055)**2.4;}).reduce((sum,v,i)=>sum+v*[.2126,.7152,.0722][i],0);
   const measure=node=>{const style=getComputedStyle(node);let bg=node;while(getComputedStyle(bg).backgroundColor==='rgba(0, 0, 0, 0)')bg=bg.parentElement;const a=lum(style.color),b=lum(getComputedStyle(bg).backgroundColor);return {font:parseFloat(style.fontSize),contrast:(Math.max(a,b)+.05)/(Math.min(a,b)+.05)};};
   return {button:measure(el),reason:measure(document.querySelector('#saved-position-continue-help')),height:el.getBoundingClientRect().height};
  });
  assert(metrics.height>=44);for(const text of [metrics.button,metrics.reason]){assert(text.font>=16);assert(text.contrast>=4.5);}report.readability.push({theme,width,height,...metrics});
  await tabTo(page,onward());await page.keyboard.press(width===320?'Enter':'Space');assert.equal(await page.evaluate(()=>document.activeElement.textContent),fixture.savedItems[31].title);
  const path=`continue-${theme}-${width}.png`;await page.screenshot({path:out+'/'+path});report.captures.push({path,sha256:createHash('sha256').update(await readFile(out+'/'+path)).digest('hex')});
 }
 report.checks.push('Continuation: full-order wrap excluding anchor, hidden anchor, search/source/review filters, no position/deleted/no other unread, reload without auto-jump, unchanged complete storage, no playback/popups, stale handlers across both safety transitions.');
 if(!process.env.POSITION_CONTINUE_ONLY){
 await page.emulateMedia({reducedMotion:'reduce'});
 for(const theme of ['day','night'])for(const width of [320,390]){
  await load([2,17,31]);const height=width===320?960:1080;await page.setViewportSize({width,height});await page.goto(base+'/?state=arrived&theme='+theme);await page.evaluate(()=>document.fonts.ready);
  await mark(17).click();await search.fill('짝수');await page.evaluate(()=>scrollTo(0,0));
  for(const heading of [page.locator('.phone h2'),page.locator('.saved-article h3').first()]){const box=await heading.boundingBox();assert(box&&box.y>=0&&box.y+box.height<=height, 'Full arrival and first item headings in first viewport '+JSON.stringify({theme,width,box}));}
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1),false);
  const initial=`${theme}-${width}-initial.png`;await page.screenshot({path:out+'/'+initial});report.captures.push({path:initial,sha256:createHash('sha256').update(await readFile(out+'/'+initial)).digest('hex')});
  await tabTo(page,reveal());await page.keyboard.press(width===320?'Enter':'Space');
  async function capture(kind){const path=`${theme}-${width}-${kind}.png`;await page.screenshot({path:out+'/'+path});report.captures.push({path,sha256:createHash('sha256').update(await readFile(out+'/'+path)).digest('hex')});}
  await capture('saved');await tabTo(page,resume());await capture('control');await page.keyboard.press(width===320?'Space':'Enter');
  assert.equal(await page.evaluate(()=>document.activeElement.textContent),fixture.savedItems[17].title);const box=await page.locator('.saved-article h3:focus').boundingBox();assert(box.y>=0&&box.y+box.height<=height);assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1),false);await capture('target');assert.deepEqual(await read(page),fixture);
 }

 }
 assert.equal(popups,0);
 if(!process.env.POSITION_CONTINUE_ONLY){
 report.checks.push('Reveal: each filter and all combinations, reset storage failures, reload without auto-jump, keyboard, reduced motion, batched safety transition and stale callbacks; hidden control layout captures.');
 report.checks.push('Clear normal/hidden/deleted positions, remove failure/retry, other localStorage unchanged, reload, keyboard focus and stale clear callbacks; first-viewport headings at 320x960/390x1080 day/night. ');
 report.checks.push('32 fixed items: position replacement, reload without auto-jump, write failure/retry, source/search/unread combinations, deleted target/undo, stale save/jump callbacks before/after restricted/unknown, unchanged registry/URLs/order/review, day/night 320/390 Tab/Enter/Space and reduced-motion immediate title focus.');
 }
 await writeFile(out+'/saved-position-report.json',JSON.stringify(report,null,2)+'\n');
 await writeFile(out+'/saved-position-report.sha256',createHash('sha256').update(await readFile(out+'/saved-position-report.json')).digest('hex')+'  saved-position-report.json\n');console.log('PASS: saved position');
}finally{await browser.close();server.closeAllConnections();await new Promise(r=>server.close(r));}
