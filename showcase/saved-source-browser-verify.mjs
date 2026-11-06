import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {chromium} from 'playwright-core';
import {createServer} from 'node:http';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {createHash} from 'node:crypto';
const out=process.env.EVIDENCE_DIR||'validation/saved-source-counts';await mkdir(out,{recursive:true});
const html=await readFile('before-arrival-showcase.html');const server=createServer((req,res)=>{res.writeHead(200,{'Content-Type':'text/html;charset=utf-8'});res.end(html);});await new Promise(r=>server.listen(0,'127.0.0.1',r));
const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/usr/bin/chromium',headless:true,args:['--no-sandbox']});const base=`http://127.0.0.1:${server.address().port}`;
const A='리그 오브 레전드 26.22 패치 노트',B='발로란트 13.09 패치 노트',C='수정2 클라이언트 1.2.420 업데이트 안내',D='이번 주말, 협동 모드가 열려요';
const key='before-arrival-sources-v2';const report={candidateSha:process.env.CANDIDATE_SHA||execFileSync('git',['rev-parse','HEAD'],{encoding:'utf8'}).trim(),workingTreeDirty:Boolean(execFileSync('git',['status','--porcelain'],{encoding:'utf8'}).trim()),ime:'Synthetic composition events only; physical Korean IME remains a manual check.',scope:'Browser sample only; no APK installation, emulator or actual Pleos SDK evidence.',captures:[],checkedAt:new Date().toISOString(),htmlSha256:createHash('sha256').update(html).digest('hex'),sourceSha256:{},upstream:'official snapshots registered through UI; no external network',checks:[],readability:[]};
for(const path of ['examples/before-arrival/index.tsx','examples/before-arrival/catalog.ts','examples/before-arrival/saved-undo.ts','examples/before-arrival/showcase.css','examples/before-arrival/verify.tsx','saved-source-browser-verify.mjs','package.json'])report.sourceSha256[path]=createHash('sha256').update(await readFile(path)).digest('hex');
const titles=page=>page.locator('.saved-article h3').allTextContents();const remove=(page,title)=>page.getByRole('button',{name:title+' 저장 취소',exact:true});const undo=page=>page.getByRole('button',{name:'삭제 실행 취소',exact:true});const notice=page=>page.locator('.saved-undo-status');const read=page=>page.evaluate(k=>JSON.parse(localStorage.getItem(k)),key);
async function tabTo(page,target){for(let i=0;i<90;i++){if(await target.evaluate(el=>el===document.activeElement))return;await page.keyboard.press('Tab');}throw new Error('Keyboard Tab did not reach target');}
async function saveDeferred(page,title){await page.getByRole('button',{name:'03 4분',exact:true}).click();await page.getByRole('button',{name:title+' 나중에 보기 저장',exact:true}).click();await page.getByRole('button',{name:'04 도착',exact:true}).click();}
async function failWrites(page,value){await page.evaluate(value=>window.__undoFailWrites=value,value);}
try{
 const page=await browser.newPage({viewport:{width:390,height:1080}});page.setDefaultNavigationTimeout(60000);
 await page.goto(base+'/?state=settings');
 for(const [provider,name] of [['lol','롤'],['valorant','발로란트'],['maple','메이플']]){await page.getByLabel('출처 서비스',{exact:true}).selectOption(provider);await page.getByLabel('출처 이름',{exact:true}).fill(name);await page.getByRole('button',{name:'출처 추가',exact:true}).click();}
 for(const title of [A,B,C])await saveDeferred(page,title);
 const seed=await read(page),lolItem=seed.savedItems.find(item=>item.title===A&&seed.sources.some(source=>source.id===item.sourceId&&source.provider==='lol'));
 assert(lolItem,'Expected the saved League of Legends item for the synthetic search fixture');
 // Synthetic test data only: this unique, allowed article URL is never fetched.
 seed.savedItems.push({...lolItem,id:'search-test',originalUrl:'https://www.leagueoflegends.com/ko-kr/news/game-updates/search-test/',title:'Alpha PATCH 한글'});
 await page.evaluate(({key,seed})=>localStorage.setItem(key,JSON.stringify(seed)),{key,seed});await page.goto(base+'/?state=arrived');
 await page.locator('.scene-composition[data-arrived="true"]').waitFor({state:'visible'});
 await page.getByRole('searchbox',{name:'저장한 제목 검색',exact:true}).waitFor({state:'visible'});
 assert.deepEqual(await titles(page),[A,B,C,'Alpha PATCH 한글']);

 const ids=[...new Set(seed.savedItems.map(i=>i.sourceId))];assert.equal(ids.length,3);
 const name='아주 긴 출처 이름으로 좁은 화면에서도 선택할 수 있는 저장 소식 출처';seed.sources.filter(s=>ids.slice(0,2).includes(s.id)).forEach(s=>s.name=name);seed.savedItems.forEach((item,index)=>item.reviewed=index===2);
 await page.evaluate(({key,seed})=>localStorage.setItem(key,JSON.stringify(seed)),{key,seed});await page.reload();
 const source=page.getByLabel('저장한 출처',{exact:true}),search=page.getByRole('searchbox'),unread=page.getByRole('button',{name:'미확인만',exact:true}),all=page.getByRole('button',{name:'전체',exact:true});
 const next=page.getByRole('button',{name:'다음 미확인 소식',exact:true});
 async function expectCounts(){
  const stored=await read(page),options=await source.locator('option').evaluateAll(nodes=>nodes.map(el=>({id:el.value,label:el.textContent})));
  for(const option of options){const count=stored.savedItems.filter(item=>(!option.id||item.sourceId===option.id)&&item.reviewed!==true).length;assert(option.label.endsWith(` · 미확인 ${count}건`),JSON.stringify(option));}
  assert.match(await page.locator('#saved-source-count-help').innerText(),/전체 저장 기준.*검색\/필터 무관/);
 }
 await expectCounts();
 let popups=0;page.on('popup',()=>popups++);
 for(const sourceId of ['',...ids])for(const query of ['', ' PATCH ', '없음'])for(const onlyUnread of [false,true]){
  await source.selectOption(sourceId);await search.fill(query);await (onlyUnread?unread:all).click();
  const expected=seed.savedItems.filter(i=>(!sourceId||i.sourceId===sourceId)&&i.title.toLowerCase().includes(query.trim().toLowerCase())&&!i.reviewed);
  await expectCounts();assert.equal(await next.isDisabled(),expected.length===0);
  if(!expected.length)assert.match(await page.locator('#saved-next-help').innerText(),/미확인 소식이 없어요/);
  for(let index=0;index<expected.length+1&&expected.length;index++){
   await next.focus();await page.keyboard.press(index%2?'Space':'Enter');
   assert.equal(await page.evaluate(()=>document.activeElement.textContent),expected[index%expected.length].title);
  }
  assert.equal(await source.inputValue(),sourceId);assert.equal(await search.inputValue(),query);
  assert.equal(await unread.getAttribute('aria-pressed'),String(onlyUnread));assert.deepEqual(await read(page),seed);
 }
 await source.selectOption('');await search.fill('');await all.click();
 await page.locator('.saved-article h3').nth(2).focus();
 await next.evaluate(el=>el[Object.keys(el).find(k=>k.startsWith('__reactProps$'))].onClick());
 assert.equal(await page.evaluate(()=>document.activeElement.textContent),seed.savedItems[3].title);
 for(const blocked of ['주행 제한','상태 미확인']){
  const stale=await next.evaluateHandle(el=>el[Object.keys(el).find(k=>k.startsWith('__reactProps$'))].onClick);
  await page.getByRole('button',{name:'01 18분',exact:true}).click();await page.getByRole('button',{name:'듣기 시연',exact:true}).click();
  await page.getByRole('button',{name:blocked,exact:true}).click();await stale.evaluate(fn=>fn());
  assert.equal(await next.count(),0);assert(await page.locator('.primary').isDisabled());assert.equal(await page.locator('.primary').getAttribute('aria-pressed'),'false');
  await page.getByRole('button',{name:'04 도착',exact:true}).click();
  const beforeFocus=await page.evaluate(()=>document.activeElement.textContent);await stale.evaluate(fn=>fn());
  assert.equal(await page.evaluate(()=>document.activeElement.textContent),beforeFocus);assert.deepEqual(await read(page),seed);
  assert.equal(await page.locator('.primary').getAttribute('aria-pressed'),'false');
 }
 // Fixed long-list fixture: navigation changes neither canonical order nor URLs.
 const longSeed={...seed,savedItems:Array.from({length:32},(_,index)=>({...lolItem,id:`next-${index}`,title:`긴 보관함 소식 ${index}`,originalUrl:`https://www.leagueoflegends.com/ko-kr/news/game-updates/next-${index}/`,reviewed:index!==31}))};
 await page.evaluate(({key,seed})=>localStorage.setItem(key,JSON.stringify(seed)),{key,seed:longSeed});await page.reload();
 await next.click();assert.equal(await page.evaluate(()=>document.activeElement.textContent),'긴 보관함 소식 31');
 await next.focus();await page.keyboard.press('Space');assert.equal(await page.evaluate(()=>document.activeElement.textContent),'긴 보관함 소식 31');assert.deepEqual(await read(page),longSeed);
 await page.evaluate(({key,seed})=>localStorage.setItem(key,JSON.stringify(seed)),{key,seed});await page.reload();
 assert.equal(popups,0);
 report.checks.push('Next unread: zero/one/multiple intersections, Enter/Space, wrap, manually focused reviewed title, unchanged filters/order/URLs/storage, stale handlers during restriction and after return, no popup or automatic playback.');
 async function expectItems(items){await expectCounts();assert.deepEqual(await titles(page),items.map(i=>i.title));assert.match(await page.locator('.saved-filters').innerText(),new RegExp(`표시 ${items.length}건`));}
 assert.equal(await source.inputValue(),'');assert.equal(new Set(await source.locator('option').allTextContents()).size,4);
 for(const id of ids){await source.selectOption(id);await expectItems(seed.savedItems.filter(i=>i.sourceId===id));}
 const reset=page.getByRole('button',{name:'조건 모두 지우기',exact:true});
 await source.selectOption('');assert.equal(await reset.count(),0);
 for(const sourceId of ['',ids[0]])for(const query of ['', '   ', ' PATCH ', '없음'])for(const onlyUnread of [false,true]){
  await source.selectOption(sourceId);await search.fill(query);await (onlyUnread?unread:all).click();
  await expectItems(seed.savedItems.filter(i=>(!sourceId||i.sourceId===sourceId)&&(!onlyUnread||!i.reviewed)&&i.title.toLowerCase().includes(query.trim().toLowerCase())));
  if(sourceId||query||onlyUnread){await reset.click();assert.equal(await source.inputValue(),'');assert.equal(await search.inputValue(),'');assert.equal(await all.getAttribute('aria-pressed'),'true');assert.equal(await search.evaluate(el=>el===document.activeElement),true);}
  await expectItems(seed.savedItems);assert.deepEqual(await read(page),seed);assert.equal(await reset.count(),0);
 }
 await source.selectOption(ids[0]);await search.fill('PATCH');await unread.click();await expectItems(seed.savedItems.filter(i=>i.sourceId===ids[0]&&!i.reviewed&&i.title.toLowerCase().includes('patch')));assert.deepEqual(await read(page),seed);
 await search.fill('없음');assert.match(await page.locator('.empty-saved').innerText(),/조건에 맞는 항목이 없어요/);await page.getByRole('button',{name:'전체 출처로 돌아가기'}).click();assert.equal(await source.inputValue(),'');assert.equal(await search.inputValue(),'없음');
 await search.fill('');await all.click();await source.selectOption(ids[1]);const target=seed.savedItems.find(i=>i.sourceId===ids[1]);
 await remove(page,target.title).click();assert.equal(await source.inputValue(),ids[1]);await expectItems([]);assert.match(await page.locator('.empty-saved').innerText(),/조건에 맞는 항목이 없어요/);await undo(page).click();await expectItems([target]);assert.equal(await source.inputValue(),ids[1]);assert.equal(await page.evaluate(()=>document.activeElement.textContent),target.title);assert.deepEqual(await read(page),seed);
 await page.getByRole('button',{name:target.title+' 확인 완료',exact:true}).click();await unread.click();await expectItems([]);await page.getByRole('button',{name:'확인 실행 취소',exact:true}).click();await expectItems([target]);assert.deepEqual(await read(page),seed);await all.click();
 for(const blocked of ['주행 제한','상태 미확인']){const stale=await source.elementHandle();const staleReset=await reset.evaluateHandle(el=>el[Object.keys(el).find(k=>k.startsWith('__reactProps$'))].onClick);await page.getByRole('button',{name:'01 18분',exact:true}).click();await page.getByRole('button',{name:'듣기 시연',exact:true}).click();await page.getByRole('button',{name:blocked,exact:true}).click();await staleReset.evaluate(handler=>handler());await stale.evaluate(el=>{el.value='';el.dispatchEvent(new Event('change',{bubbles:true}));});assert.equal(await source.count(),0);assert.equal(await page.locator('.primary').getAttribute('aria-pressed'),'false');await page.getByRole('button',{name:'04 도착',exact:true}).click();assert.equal(await source.inputValue(),ids[1]);assert.deepEqual(await read(page),seed);assert.equal(await page.locator('.primary').getAttribute('aria-pressed'),'false');}
 await page.reload();assert.equal(await source.inputValue(),ids[1]);await expectItems(seed.savedItems.filter(i=>i.sourceId===ids[1]));await reset.click();await page.reload();assert.equal(await source.inputValue(),'');await expectItems(seed.savedItems);

 const sourceKey='before-arrival-saved-source-v1',positionKey='before-arrival-saved-position-v1';
 const fixed={...seed,savedItems:Array.from({length:32},(_,index)=>({...seed.savedItems[index%seed.savedItems.length],id:`persist-${index}`,title:`보존 소식 ${index}`,originalUrl:seed.savedItems[index%seed.savedItems.length].originalUrl.replace(/[^/]+\/?$/,index%4===2?String(9000+index):`persist-${index}/`),reviewed:index%3===0}))};
 await page.evaluate(({key,fixed})=>localStorage.setItem(key,JSON.stringify(fixed)),{key,fixed});await page.reload();
 await page.getByRole('button',{name:'보존 소식 1 여기까지 봤어요',exact:true}).click();
 const positionBefore=await page.evaluate(k=>localStorage.getItem(k),positionKey);
 await source.selectOption(ids[0]);await search.fill('소식');await unread.click();await page.reload();
 assert.equal(await source.inputValue(),ids[0]);assert.equal(await search.inputValue(),'');assert.equal(await unread.getAttribute('aria-pressed'),'true');
 await expectItems(fixed.savedItems.filter(i=>i.sourceId===ids[0]&&!i.reviewed));
 assert(await page.getByRole('button',{name:'지난 위치로 이동',exact:true}).isDisabled());assert.match(await page.locator('#saved-position-help').innerText(),/필터가 저장한 위치를 숨겨/);
 assert.equal(await page.evaluate(k=>localStorage.getItem(k),positionKey),positionBefore);assert.deepEqual(await read(page),fixed);
 // Fixed 32-item counts: both review directions, removal and undo.
 await reset.click();await expectCounts();
 for(const item of [fixed.savedItems[1],fixed.savedItems[0]]){
  await page.getByRole('button',{name:item.title+(item.reviewed?' 미확인으로 되돌리기':' 확인 완료'),exact:true}).click();await expectCounts();
  if(!item.reviewed){await page.getByRole('button',{name:'확인 실행 취소',exact:true}).click();await expectCounts();}
  else {await page.getByRole('button',{name:item.title+' 확인 완료',exact:true}).click();await expectCounts();}
  await remove(page,item.title).click();await expectCounts();await undo(page).click();await expectCounts();
 }
 assert.deepEqual(await read(page),fixed);await source.selectOption(ids[0]);await page.reload();await expectCounts();
 report.checks.push('Unread counts: all saved items independent of filters; 32-item review/unreview/delete/undo and reload preserve canonical data.');
 // A source removed from registration remains selectable through its saved articles.
 const orphan={...fixed,sources:fixed.sources.filter(s=>s.id!==ids[0])};
 await page.evaluate(({key,orphan})=>localStorage.setItem(key,JSON.stringify(orphan)),{key,orphan});await page.reload();await expectCounts();assert.equal(await source.inputValue(),ids[0]);assert.deepEqual(await read(page),orphan);
 await page.evaluate(k=>localStorage.setItem(k,JSON.stringify('missing-source')),sourceKey);await page.reload();assert.equal(await source.inputValue(),'');assert.match(await page.locator('.saved-source-notice').innerText(),/등록 목록과 보관함에 없어/);
 for(const raw of ['{bad','true','null','32','{}']){await page.evaluate(({sourceKey,raw})=>localStorage.setItem(sourceKey,raw),{sourceKey,raw});await page.reload();assert.equal(await source.inputValue(),'');assert.match(await page.locator('.saved-source-notice').innerText(),/읽지 못했거나/);}
 await page.addInitScript(k=>{const get=Storage.prototype.getItem;Storage.prototype.getItem=function(key){if(key===k&&sessionStorage.getItem('deny-source-read')==='yes')throw Error('read denied');return get.call(this,key);};},sourceKey);
 await page.evaluate(()=>sessionStorage.setItem('deny-source-read','yes'));await page.reload();assert.match(await page.locator('.saved-source-notice').innerText(),/읽지 못했거나/);await page.evaluate(()=>sessionStorage.removeItem('deny-source-read'));
 await page.evaluate(k=>{window.__sourceSet=Storage.prototype.setItem;Storage.prototype.setItem=function(key,value){if(key===k)throw Error('write denied');return window.__sourceSet.call(this,key,value);};},sourceKey);
 const storedBefore=await page.evaluate(k=>localStorage.getItem(k),sourceKey);
 await source.selectOption(ids[0]);assert.equal(await source.inputValue(),ids[0]);assert.match(await page.locator('.saved-source-notice').innerText(),/현재 화면에만 적용/);assert.equal(await page.evaluate(k=>localStorage.getItem(k),sourceKey),storedBefore);
 await page.getByRole('button',{name:'출처 필터 저장 재시도',exact:true}).click();assert.match(await page.locator('.saved-source-notice').innerText(),/현재 화면에만 적용/);
 await page.evaluate(()=>Storage.prototype.setItem=window.__sourceSet);await page.getByRole('button',{name:'출처 필터 저장 재시도',exact:true}).click();assert.equal(await page.locator('.saved-source-notice').count(),0);await page.reload();assert.equal(await source.inputValue(),ids[0]);
 for(const blocked of ['주행 제한','상태 미확인']){
  const stale=await source.evaluateHandle(el=>el[Object.keys(el).find(k=>k.startsWith('__reactProps$'))].onChange);
  await page.getByRole('button',{name:blocked,exact:true}).click();await stale.evaluate(fn=>fn({target:{value:''}}));assert.equal(await page.evaluate(k=>JSON.parse(localStorage.getItem(k)),sourceKey),ids[0]);
  await page.getByRole('button',{name:'04 도착',exact:true}).click();await stale.evaluate(fn=>fn({target:{value:''}}));assert.equal(await source.inputValue(),ids[0]);assert.equal(await page.locator('.primary').getAttribute('aria-pressed'),'false');
 }
 await reset.click();await page.reload();assert.equal(await source.inputValue(),'');assert.equal(await all.getAttribute('aria-pressed'),'true');assert.deepEqual(await read(page),orphan);assert.equal(await page.evaluate(k=>localStorage.getItem(k),positionKey),positionBefore);assert.equal(popups,0);
 report.checks.push('32 fixed saved articles: persisted source/unread intersection, session-only search, orphan source retention, missing source fallback, malformed values, read denial, write failure and retry, all/reset persistence, unchanged canonical registry/position, hidden position reason, stale source handlers blocked during restriction and after parking.');
 await page.evaluate(({key,fixed})=>localStorage.setItem(key,JSON.stringify(fixed)),{key,fixed});await page.reload();
 for(const theme of ['day','night'])for(const width of [320,390]){
 await page.setViewportSize({width,height:width===320?960:1080});await page.goto(base+'/?state=arrived&theme='+theme);await page.evaluate(()=>document.fonts.ready);
 const firstTitle=await page.locator('.saved-article h3').first().boundingBox();assert(firstTitle.y>=0&&firstTitle.y+firstTitle.height<=(width===320?960:1080));assert.equal(await page.evaluate(()=>scrollY),0);
 await expectCounts();const arrivalPath=`${theme}-${width}-arrival-counts.png`;await page.screenshot({path:out+'/'+arrivalPath});report.captures.push({path:arrivalPath,sha256:createHash('sha256').update(await readFile(out+'/'+arrivalPath)).digest('hex')});
 await tabTo(page,source);await page.keyboard.press('Home');await page.keyboard.press('ArrowDown');await page.keyboard.press('Enter');assert.equal(await source.inputValue(),ids[0]);await page.evaluate(()=>document.fonts.ready);
 const audit=await reset.evaluate(el=>{const c=getComputedStyle(el),rgb=s=>s.match(/[\d.]+/g).map(Number).slice(0,3),lum=c=>c.map(v=>v/255).map(v=>v<=.04045?v/12.92:((v+.055)/1.055)**2.4).reduce((n,v,i)=>n+v*[.2126,.7152,.0722][i],0),a=lum(rgb(c.color)),b=lum(rgb(c.backgroundColor));return {contrast:(Math.max(a,b)+.05)/(Math.min(a,b)+.05),fontSize:parseFloat(c.fontSize),pageOverflow:document.documentElement.scrollWidth>innerWidth+1};});assert(audit.contrast>=4.5);assert(audit.fontSize>=16);assert.equal(audit.pageOverflow,false);report.readability.push({theme,width,...audit});
 await page.emulateMedia({reducedMotion:'reduce'});
 await page.evaluate(()=>{window.__scrollCalls=[];const scroll=Element.prototype.scrollIntoView;Element.prototype.scrollIntoView=function(options){window.__scrollCalls.push(options);return scroll.call(this,options);};});
 await next.focus();await page.keyboard.press(width===320?'Enter':'Space');
 assert.equal(await page.evaluate(()=>window.__scrollCalls.at(-1).behavior),'instant');
 const titleAudit=await page.locator('.saved-article h3:focus').evaluate(el=>{const c=getComputedStyle(el),r=el.getBoundingClientRect();return {outline:c.outlineStyle,outlineWidth:parseFloat(c.outlineWidth),fontSize:parseFloat(c.fontSize),visible:r.top>=0&&r.bottom<=innerHeight,pageOverflow:document.documentElement.scrollWidth>innerWidth+1};});
 assert.equal(titleAudit.outline,'solid');assert(titleAudit.outlineWidth>=2);assert(titleAudit.fontSize>=16);assert(titleAudit.visible);assert.equal(titleAudit.pageOverflow,false);
 const targetPath=`${theme}-${width}-next-title.png`;await page.screenshot({path:out+'/'+targetPath,fullPage:false});report.captures.push({path:targetPath,sha256:createHash('sha256').update(await readFile(out+'/'+targetPath)).digest('hex')});report.readability.push({theme,width,target:titleAudit});
 await next.focus();
 const nextAudit=await next.evaluate(el=>{const c=getComputedStyle(el);return {fontSize:parseFloat(c.fontSize),overflow:el.scrollWidth>el.clientWidth+1};});assert(nextAudit.fontSize>=16);assert.equal(nextAudit.overflow,false);report.readability.push({theme,width,next:nextAudit});
 for(const empty of [false,true]){await search.fill(empty?'없는 제목':'');const path=`${theme}-${width}-${empty?'empty':'source'}.png`;await page.screenshot({path:out+'/'+path,fullPage:false});report.captures.push({path,sha256:createHash('sha256').update(await readFile(out+'/'+path)).digest('hex')});}
 await tabTo(page,reset);await page.keyboard.press(width===320?'Enter':'Space');assert.equal(await search.evaluate(el=>el===document.activeElement),true);await expectItems(fixed.savedItems);assert.deepEqual(await read(page),fixed);
 }
 report.checks.push('Reset all filter combinations including whitespace/empty results; Enter/Space and search focus; stale reset closures blocked; Three source IDs; duplicate long names; exact counts/order; source/search/unread intersection; unchanged storage; empty reset; last deletion/undo; review undo; persisted source; stale blocked handlers; stopped playback; keyboard, contrast and overflow at 320/390 day/night.');
 await writeFile(out+'/source-report.json',JSON.stringify(report,null,2)+'\n');console.log('PASS: saved source filter');
}finally{await browser.close();server.closeAllConnections();await new Promise(r=>server.close(r));}
