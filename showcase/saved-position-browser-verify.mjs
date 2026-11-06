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
 async function load(unread){fixture.savedItems.forEach((item,i)=>item.reviewed=!unread.includes(i));await page.evaluate(({key,seed})=>localStorage.setItem(key,JSON.stringify(seed)),{key,seed:fixture});await page.goto(base+'/?state=arrived');await page.getByRole('button',{name:'전체',exact:true}).click();await button(31).waitFor();}
 const mark=i=>page.getByRole('button',{name:fixture.savedItems[i].title+' 여기까지 봤어요',exact:true});
 const resume=()=>page.getByRole('button',{name:'지난 위치로 이동',exact:true});
 let popups=0;page.on('popup',()=>popups++);
 const positionKey='before-arrival-saved-position-v1';
 const position=()=>page.evaluate(k=>localStorage.getItem(k),positionKey);
 await load([2,17,31]);assert(await resume().isDisabled());
 for(const i of [17,31]){await mark(i).click();assert.equal(await position(),'url:'+fixture.savedItems[i].originalUrl.replace(/\/$/,''));assert.deepEqual(await read(page),fixture);}
 await page.reload();await resume().waitFor();assert.notEqual(await page.evaluate(()=>document.activeElement.textContent),fixture.savedItems[31].title);
 await page.emulateMedia({reducedMotion:'reduce'});await resume().click();assert.equal(await page.evaluate(()=>document.activeElement.textContent),fixture.savedItems[31].title);
 for(const source of ['',fixture.savedItems[31].sourceId,fixture.savedItems[0].sourceId])for(const query of ['','홀수','짝수'])for(const unread of [false,true]){
  await page.getByLabel('저장한 출처',{exact:true}).selectOption(source);await search.fill(query);await page.getByRole('button',{name:unread?'미확인만':'전체',exact:true}).click();
  const hidden=Boolean(source&&source!==fixture.savedItems[31].sourceId)||!fixture.savedItems[31].title.includes(query);
  assert.equal(await resume().isDisabled(),hidden);if(hidden)assert.match(await page.locator('#saved-position-help').innerText(),/필터/);assert.deepEqual(await read(page),fixture);
 }
 await load([2,17,31]);await mark(18).click();await page.getByRole('button',{name:'미확인만',exact:true}).click();assert(await resume().isDisabled());assert.match(await page.locator('#saved-position-help').innerText(),/필터/);
 await page.getByRole('button',{name:'전체',exact:true}).click();await mark(31).click();
 await page.evaluate(()=>{window.originalPositionWrite=Storage.prototype.setItem;Storage.prototype.setItem=function(k,v){if(k==='before-arrival-saved-position-v1')throw Error('denied');return window.originalPositionWrite.call(this,k,v);};});
 const previous=await position();await mark(17).click();assert.match(await page.locator('.saved-position [role=status]').last().innerText(),/저장하지 못/);assert.equal(await position(),previous);
 await page.evaluate(()=>Storage.prototype.setItem=window.originalPositionWrite);await mark(17).click();assert.match(await page.locator('.saved-position [role=status]').last().innerText(),/저장했/);
 for(const blocked of ['주행 제한','상태 미확인']){
  const stale=[];for(const control of [mark(31),resume()])stale.push(await control.evaluateHandle(el=>el[Object.keys(el).find(k=>k.startsWith('__reactProps$'))].onClick));
  const before=await position();await page.getByRole('button',{name:blocked,exact:true}).click();
  for(const fn of stale)await fn.evaluate(fn=>fn());assert.equal(await position(),before);assert.equal(await page.locator('.saved-position').count(),0);assert.equal(await page.locator('.primary').getAttribute('aria-pressed'),'false');
  await page.getByRole('button',{name:'04 도착',exact:true}).click();const focus=await page.evaluate(()=>document.activeElement.textContent);
  for(const fn of stale)await fn.evaluate(fn=>fn());assert.equal(await position(),before);assert.equal(await page.evaluate(()=>document.activeElement.textContent),focus);assert.equal(await page.locator('.primary').getAttribute('aria-pressed'),'false');
 }
 await page.getByRole('button',{name:fixture.savedItems[17].title+' 저장 취소',exact:true}).click();assert(await resume().isDisabled());assert.match(await page.locator('#saved-position-help').innerText(),/삭제/);
 await page.getByRole('button',{name:'삭제 실행 취소',exact:true}).click();assert.equal(await resume().isDisabled(),false);assert.deepEqual(await read(page),fixture);
 await page.emulateMedia({reducedMotion:'reduce'});
 for(const theme of ['day','night'])for(const width of [320,390]){
  await load([2,17,31]);await page.setViewportSize({width,height:1080});await page.goto(base+'/?state=arrived&theme='+theme);await page.evaluate(()=>document.fonts.ready);
  await tabTo(page,mark(17));await page.keyboard.press(width===320?'Enter':'Space');
  async function capture(kind){const path=`${theme}-${width}-${kind}.png`;await page.screenshot({path:out+'/'+path});report.captures.push({path,sha256:createHash('sha256').update(await readFile(out+'/'+path)).digest('hex')});}
  await capture('saved');await tabTo(page,resume());await capture('control');await page.keyboard.press(width===320?'Space':'Enter');
  assert.equal(await page.evaluate(()=>document.activeElement.textContent),fixture.savedItems[17].title);const box=await page.locator('.saved-article h3:focus').boundingBox();assert(box.y>=0&&box.y+box.height<=1080);assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1),false);await capture('target');assert.deepEqual(await read(page),fixture);
 }

 assert.equal(popups,0);
 report.checks.push('32 fixed items: position replacement, reload without auto-jump, write failure/retry, source/search/unread combinations, deleted target/undo, stale save/jump callbacks before/after restricted/unknown, unchanged registry/URLs/order/review, day/night 320/390 Tab/Enter/Space and reduced-motion immediate title focus.');
 await writeFile(out+'/saved-position-report.json',JSON.stringify(report,null,2)+'\n');console.log('PASS: saved position');
}finally{await browser.close();server.closeAllConnections();await new Promise(r=>server.close(r));}
