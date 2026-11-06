import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {chromium} from 'playwright-core';
import {createServer} from 'node:http';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {createHash} from 'node:crypto';
const out=process.env.EVIDENCE_DIR||'validation/reviewed-filter';await mkdir(out,{recursive:true});
const html=await readFile('before-arrival-showcase.html');const server=createServer((req,res)=>{res.writeHead(200,{'Content-Type':'text/html;charset=utf-8'});res.end(html);});await new Promise(r=>server.listen(0,'127.0.0.1',r));
const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/usr/bin/chromium',headless:true,args:['--no-sandbox']});const base=`http://127.0.0.1:${server.address().port}`;
const A='리그 오브 레전드 26.22 패치 노트',B='발로란트 13.09 패치 노트',C='수정2 클라이언트 1.2.420 업데이트 안내',D='이번 주말, 협동 모드가 열려요';
const key='before-arrival-sources-v2';const report={candidateSha:process.env.CANDIDATE_SHA||execFileSync('git',['rev-parse','HEAD'],{encoding:'utf8'}).trim(),workingTreeDirty:Boolean(execFileSync('git',['status','--porcelain'],{encoding:'utf8'}).trim()),ime:'Synthetic composition events only; physical Korean IME remains a manual check.',scope:'Browser sample only; no APK installation, emulator or actual Pleos SDK evidence.',captures:[],checkedAt:new Date().toISOString(),htmlSha256:createHash('sha256').update(html).digest('hex'),sourceSha256:{},upstream:'official snapshots registered through UI; no external network',checks:[],readability:[]};
for(const path of ['examples/before-arrival/index.tsx','examples/before-arrival/saved-undo.ts','examples/before-arrival/showcase.css','reviewed-filter-browser-verify.mjs','package.json'])report.sourceSha256[path]=createHash('sha256').update(await readFile(path)).digest('hex');
const titles=page=>page.locator('.saved-article h3').allTextContents();const remove=(page,title)=>page.getByRole('button',{name:title+' 저장 취소',exact:true});const undo=page=>page.getByRole('button',{name:'삭제 실행 취소',exact:true});const notice=page=>page.locator('.saved-undo-status');const read=page=>page.evaluate(k=>JSON.parse(localStorage.getItem(k)),key);
async function tabTo(page,target){for(let i=0;i<90;i++){if(await target.evaluate(el=>el===document.activeElement))return;await page.keyboard.press('Tab');}throw new Error('Keyboard Tab did not reach target');}
async function saveDeferred(page,title){await page.getByRole('button',{name:'03 4분',exact:true}).click();await page.getByRole('button',{name:title+' 나중에 보기 저장',exact:true}).click();await page.getByRole('button',{name:'04 도착',exact:true}).click();}
async function failWrites(page,value){await page.evaluate(value=>window.__undoFailWrites=value,value);}
try{
 const page=await browser.newPage({viewport:{width:390,height:1080}});
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

 const reviewed=page.getByRole('button',{name:'확인 완료만',exact:true}),all=page.getByRole('button',{name:'전체',exact:true});
 const search=page.getByRole('searchbox'),source=page.getByLabel('저장한 출처',{exact:true});
 const next=page.getByRole('button',{name:'다음 미확인 소식',exact:true});
 const load=async data=>{await page.evaluate(({key,data})=>localStorage.setItem(key,JSON.stringify(data)),{key,data});await page.reload();};
 const focusText=()=>page.evaluate(()=>document.activeElement.textContent);
 await reviewed.focus();await page.keyboard.press('Enter');assert.deepEqual(await titles(page),[]);
 const fixture={...seed,savedItems:Array.from({length:32},(_,i)=>({...lolItem,id:'reviewed-'+i,title:i===0?A:'확인 소식 '+i,originalUrl:'https://www.leagueoflegends.com/ko-kr/news/game-updates/reviewed-'+i+'/',reviewed:true}))};
 await load(fixture);assert.equal(await reviewed.getAttribute('aria-pressed'),'true');assert.deepEqual(await titles(page),fixture.savedItems.map(i=>i.title));
 assert(await next.isDisabled());assert.match(await page.locator('#saved-next-help').innerText(),/확인 완료만에서는/);
 await search.fill(A);assert.deepEqual(await titles(page),[A]);await source.selectOption(lolItem.sourceId);assert.deepEqual(await titles(page),[A]);
 await search.fill('없는 제목');assert.deepEqual(await titles(page),[]);
 await page.getByRole('button',{name:'조건 모두 지우기',exact:true}).click();assert.equal(await all.getAttribute('aria-pressed'),'true');assert.equal(await source.inputValue(),'');assert.equal(await search.inputValue(),'');assert(await search.evaluate(el=>el===document.activeElement));
 await reviewed.click();
 await page.evaluate(()=>{const original=Storage.prototype.setItem;Storage.prototype.setItem=function(k,v){if(window.__fail&&k==='before-arrival-sources-v2')throw new DOMException('Denied','QuotaExceededError');return original.call(this,k,v);};window.__fail=true;});
 const revert=page.getByRole('button',{name:A+' 미확인으로 되돌리기',exact:true});
 await revert.focus();await page.keyboard.press('Space');assert.equal(await titles(page).then(x=>x.length),32);assert.deepEqual(await read(page),fixture);assert(await revert.evaluate(el=>el===document.activeElement));assert.match(await page.locator('.phone [role=alert]').innerText(),/다시 눌러/);
 await page.evaluate(()=>window.__fail=false);await page.keyboard.press('Enter');assert.equal(await focusText(),'확인 소식 1');assert.equal(await titles(page).then(x=>x.length),31);
 const changed={...fixture,savedItems:fixture.savedItems.map((i,n)=>n===0?{...i,reviewed:false}:i)};assert.deepEqual(await read(page),changed);
 await page.reload();assert.equal(await titles(page).then(x=>x.length),31);
 await search.fill('확인 소식 1'); // Reduce to one exact match using the full title of item 31 instead.
 await search.fill('확인 소식 31');await page.getByRole('button',{name:'확인 소식 31 미확인으로 되돌리기',exact:true}).click();
 assert.deepEqual(await titles(page),[]);assert(await page.locator('.empty-saved').evaluate(el=>el===document.activeElement));
 await page.getByRole('button',{name:'조건 모두 지우기',exact:true}).click();
 await page.getByRole('button',{name:A+' 확인 완료',exact:true}).click();
 await page.getByRole('button',{name:'확인 실행 취소',exact:true}).click();assert.equal((await read(page)).savedItems[0].reviewed,false);
 let popups=0;page.on('popup',()=>popups++);
 await reviewed.click();
 for(const blocked of ['주행 제한','상태 미확인']){
  const stale=await page.locator('.saved-review').first().evaluateHandle(el=>el[Object.keys(el).find(k=>k.startsWith('__reactProps$'))].onClick);
  const staleFilter=await reviewed.evaluateHandle(el=>el[Object.keys(el).find(k=>k.startsWith('__reactProps$'))].onClick);
  const stored=await read(page);
  await page.getByRole('button',{name:'01 18분',exact:true}).click();await page.getByRole('button',{name:'듣기 시연',exact:true}).click();
  await page.getByRole('button',{name:blocked,exact:true}).click();await stale.evaluate(fn=>fn());await staleFilter.evaluate(fn=>fn());
  assert(await page.locator('.primary').isDisabled());assert.equal(await page.locator('.primary').getAttribute('aria-pressed'),'false');
  await page.getByRole('button',{name:'04 도착',exact:true}).click();const focused=await focusText();
  await stale.evaluate(fn=>fn());await staleFilter.evaluate(fn=>fn());assert.equal(await focusText(),focused);assert.deepEqual(await read(page),stored);
  assert.equal(await page.locator('.primary').getAttribute('aria-pressed'),'false');
 }
 assert.equal(popups,0);
 await load(fixture);
 for(const theme of ['day','night'])for(const [width,height] of [[320,960],[390,1080]]){
  await page.setViewportSize({width,height});await page.goto(base+'/?state=arrived&theme='+theme);await reviewed.waitFor();
  await reviewed.focus();await page.keyboard.press('Space');assert.equal(await reviewed.getAttribute('aria-pressed'),'true');
  await page.locator('.saved-article h3').first().focus();
  const metrics=await page.evaluate(()=>{const el=document.querySelector('.saved-article h3');return {overflow:document.documentElement.scrollWidth>innerWidth,titleOverflow:el.scrollHeight>el.clientHeight,text:el.textContent};});
  assert.equal(metrics.overflow,false);assert.equal(metrics.titleOverflow,false);assert.equal(metrics.text,A);
  const path=out+'/'+theme+'-'+width+'.png';await page.screenshot({path,fullPage:true});report.captures.push({path,sha256:createHash('sha256').update(await readFile(path)).digest('hex')});report.readability.push({theme,width,height,...metrics});
 }
 report.checks.push('Reviewed filter: 0/1/32, original order and URLs, source/search intersection, reset and keyboard, persistence, failed save retains item/state/focus and retry, successful revert moves to next heading or empty guidance, review undo, restricted/unknown stale handlers, no auto writes/playback/popups, narrow day/night title and overflow.');
 await writeFile(out+'/report.json',JSON.stringify(report,null,2)+'\n');console.log('PASS: reviewed-only focused browser checks');
}finally{await browser.close();await new Promise(r=>server.close(r));}
