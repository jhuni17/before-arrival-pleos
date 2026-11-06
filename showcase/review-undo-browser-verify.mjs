import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {chromium} from 'playwright-core';
import {createServer} from 'node:http';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {createHash} from 'node:crypto';
const out=process.env.EVIDENCE_DIR||'validation/review-undo';await mkdir(out,{recursive:true});
const html=await readFile('before-arrival-showcase.html');const server=createServer((req,res)=>{res.writeHead(200,{'Content-Type':'text/html;charset=utf-8'});res.end(html);});await new Promise(r=>server.listen(0,'127.0.0.1',r));
const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/usr/bin/chromium',headless:true,args:['--no-sandbox']});const base=`http://127.0.0.1:${server.address().port}`;
const A='리그 오브 레전드 26.22 패치 노트',B='발로란트 13.09 패치 노트',C='수정2 클라이언트 1.2.420 업데이트 안내',D='이번 주말, 협동 모드가 열려요';
const key='before-arrival-sources-v2';const report={candidateSha:process.env.CANDIDATE_SHA||execFileSync('git',['rev-parse','HEAD'],{encoding:'utf8'}).trim(),workingTreeDirty:Boolean(execFileSync('git',['status','--porcelain'],{encoding:'utf8'}).trim()),ime:'Synthetic composition events only; physical Korean IME remains a manual check.',scope:'Browser sample only; no APK installation, emulator or actual Pleos SDK evidence.',captures:[],checkedAt:new Date().toISOString(),htmlSha256:createHash('sha256').update(html).digest('hex'),sourceSha256:{},upstream:'official snapshots registered through UI; no external network',checks:[],readability:[]};
for(const path of ['examples/before-arrival/index.tsx','examples/before-arrival/saved-undo.ts','examples/before-arrival/showcase.css','review-undo-browser-verify.mjs','package.json'])report.sourceSha256[path]=createHash('sha256').update(await readFile(path)).digest('hex');
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
 let requests=0,popups=0;page.on('request',()=>requests++);page.on('popup',()=>popups++);
 await page.evaluate(()=>{const original=Storage.prototype.setItem;window.__writes=0;Storage.prototype.setItem=function(k,v){window.__writes++;if(window.__undoFailWrites)throw new DOMException('Denied','QuotaExceededError');return original.call(this,k,v);};});

 const review=title=>page.getByRole('button',{name:title+' 확인 완료',exact:true});
 const undoReview=page.getByRole('button',{name:'확인 실행 취소',exact:true});
 const unread=page.getByRole('button',{name:'미확인만',exact:true});
 const search=page.getByRole('searchbox',{name:'저장한 제목 검색'});
 await unread.click();await search.fill('패치');
 await failWrites(page,true);await review(A).click();assert.deepEqual(await read(page),seed);assert.equal(await undoReview.count(),0);
 await failWrites(page,false);await tabTo(page,review(A));await page.keyboard.press('Enter');
 assert.deepEqual(await titles(page),[B]);assert.match(await page.locator('.saved-review-undo').innerText(),new RegExp(A));
 assert.notEqual(await page.evaluate(()=>document.activeElement.tagName),'BODY');
 await tabTo(page,undoReview);await failWrites(page,true);const checked=await read(page);await page.keyboard.press('Enter');assert.deepEqual(await read(page),checked);assert.equal(await undoReview.count(),1);
 await failWrites(page,false);await page.keyboard.press('Enter');assert.deepEqual(await read(page),{...seed,savedItems:seed.savedItems.map(i=>i.title===A?{...i,reviewed:false}:i)});assert.deepEqual(await titles(page),[A,B]);assert.equal(await search.inputValue(),'패치');assert.equal(await unread.getAttribute('aria-pressed'),'true');assert.equal(await page.evaluate(()=>document.activeElement.textContent),A);
 await review(A).click();await review(B).click();await undoReview.click();assert.deepEqual(await titles(page),[B]);
 await review(B).click();await page.getByRole('button',{name:'전체',exact:true}).click();await remove(page,B).click();await saveDeferred(page,B);const resaved=await read(page);assert.equal(await undoReview.count(),0);assert.deepEqual(await read(page),resaved);
 for(const blocked of ['주행 제한','상태 미확인']){
  await review(B).click();const stale=await undoReview.elementHandle();const before=await read(page);
  await page.getByRole('button',{name:blocked,exact:true}).click();await stale.evaluate(el=>el.click());assert.deepEqual(await read(page),before);
  await page.getByRole('button',{name:'04 도착',exact:true}).click();await stale.evaluate(el=>el.click());assert.deepEqual(await read(page),before);assert.equal(await undoReview.count(),0);assert.equal(await page.locator('.primary').getAttribute('aria-pressed'),'false');
  await page.getByRole('button',{name:B+' 미확인으로 되돌리기',exact:true}).click();
 }
 await review(B).click();await page.reload();assert.equal(await undoReview.count(),0);
 for(const theme of ['day','night'])for(const width of [320,390]){
  await page.setViewportSize({width,height:1080});await page.goto(base+'/?state=arrived&theme='+theme);
  await page.getByRole('button',{name:B+' 미확인으로 되돌리기',exact:true}).click();await unread.click();await search.fill('발로란트');
  await tabTo(page,review(B));await page.keyboard.press('Enter');assert.deepEqual(await titles(page),[]);
  await page.evaluate(()=>document.fonts.ready);await page.screenshot({path:out+'/'+theme+'-'+width+'-offer.png',fullPage:true});
  await tabTo(page,undoReview);await page.keyboard.press('Enter');assert.deepEqual(await titles(page),[B]);
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1),false);
  await page.screenshot({path:out+'/'+theme+'-'+width+'-restored.png',fullPage:true});
  for(const suffix of ['offer','restored']){const path=theme+'-'+width+'-'+suffix+'.png';report.captures.push({path,sha256:createHash('sha256').update(await readFile(out+'/'+path)).digest('hex')});}
  await page.getByRole('button',{name:'전체',exact:true}).click();await review(B).click();
 }
 report.checks.push('Successful/failed review and undo, keyboard focus, current filter/search, latest only, deleted/resaved item, stale restricted/unknown handlers, reload, day/night 320/390 offer and restoration.');
 await writeFile(out+'/review-undo-report.json',JSON.stringify(report,null,2)+'\n');console.log('PASS: review undo');
}finally{await browser.close();server.closeAllConnections();await new Promise(r=>server.close(r));}
