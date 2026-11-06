import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {chromium} from 'playwright-core';
import {createServer} from 'node:http';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {createHash} from 'node:crypto';
const out=process.env.EVIDENCE_DIR||'validation/saved-item-next';await mkdir(out,{recursive:true});
const html=await readFile('before-arrival-showcase.html');const server=createServer((req,res)=>{res.writeHead(200,{'Content-Type':'text/html;charset=utf-8'});res.end(html);});await new Promise(r=>server.listen(0,'127.0.0.1',r));
const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/usr/bin/chromium',headless:true,args:['--no-sandbox']});const base=`http://127.0.0.1:${server.address().port}`;
const A='리그 오브 레전드 26.22 패치 노트',B='발로란트 13.09 패치 노트',C='수정2 클라이언트 1.2.420 업데이트 안내';
const key='before-arrival-sources-v2';const report={candidateSha:process.env.CANDIDATE_SHA||execFileSync('git',['rev-parse','HEAD'],{encoding:'utf8'}).trim(),workingTreeDirty:Boolean(execFileSync('git',['status','--porcelain'],{encoding:'utf8'}).trim()),ime:'Synthetic composition events only; physical Korean IME remains a manual check.',scope:'Browser sample only; no APK installation, emulator or actual Pleos SDK evidence.',captures:[],checkedAt:new Date().toISOString(),htmlSha256:createHash('sha256').update(html).digest('hex'),sourceSha256:{},upstream:'official snapshots registered through UI; no external network',checks:[],readability:[]};
for(const path of ['examples/before-arrival/index.tsx','examples/before-arrival/saved-undo.ts','examples/before-arrival/showcase.css','saved-item-next-browser-verify.mjs','package.json'])report.sourceSha256[path]=createHash('sha256').update(await readFile(path)).digest('hex');
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
 async function jump(from,to,method='click'){
  const before=await read(page);
  if(method==='click')await button(from).click();else{await tabTo(page,button(from));await page.keyboard.press(method);}
  assert.equal(await page.evaluate(()=>document.activeElement.textContent),fixture.savedItems[to].title);assert.deepEqual(await read(page),before);
 }
 let popups=0;page.on('popup',()=>popups++);
 for(const unread of [[],[17],[2,17,31]]){
  await load(unread);assert.equal(await page.locator('.saved-item-next').count(),32);
  if(!unread.length){assert.equal(await page.locator('.saved-item-next:disabled').count(),32);assert.match(await page.locator('#saved-next-help').innerText(),/없어/);continue;}
  await jump(15,17);await jump(31,unread[0],'Enter');await jump(17,unread.length===1?17:31,'Space');
  for(const sourceId of ['',fixture.savedItems[17].sourceId])for(const query of ['','홀수'])for(const onlyUnread of [false,true]){
   await page.getByLabel('저장한 출처',{exact:true}).selectOption(sourceId);await search.fill(query);await page.getByRole('button',{name:onlyUnread?'미확인만':'전체',exact:true}).click();
   const visible=fixture.savedItems.filter(i=>(!sourceId||i.sourceId===sourceId)&&i.title.includes(query)&&(!onlyUnread||!i.reviewed));
   for(const anchor of [visible[Math.floor(visible.length/2)],visible.at(-1)].filter(Boolean)){
    const index=visible.indexOf(anchor),target=[...visible.slice(index+1),...visible.slice(0,index+1)].find(i=>!i.reviewed);
    if(target)await jump(fixture.savedItems.indexOf(anchor),fixture.savedItems.indexOf(target));else assert.equal(await button(fixture.savedItems.indexOf(anchor)).isDisabled(),true);
   }
  }
 }
 for(const blocked of ['주행 제한','상태 미확인']){
  await load([2,17,31]);const stale=await button(15).evaluateHandle(el=>el[Object.keys(el).find(k=>k.startsWith('__reactProps$'))].onClick),before=await read(page);
  await page.getByRole('button',{name:blocked,exact:true}).click();await stale.evaluate(fn=>fn());assert.deepEqual(await read(page),before);
  await page.getByRole('button',{name:'04 도착',exact:true}).click();const focus=await page.evaluate(()=>document.activeElement.textContent);await stale.evaluate(fn=>fn());assert.equal(await page.evaluate(()=>document.activeElement.textContent),focus);assert.deepEqual(await read(page),before);assert.equal(await page.locator('.primary').getAttribute('aria-pressed'),'false');
 }
 await page.emulateMedia({reducedMotion:'reduce'});
 for(const theme of ['day','night'])for(const width of [320,390]){
  await load([2,17,31]);await page.setViewportSize({width,height:1080});await page.goto(base+'/?state=arrived&theme='+theme);
  for(const [from,to,kind] of [[15,17,'middle'],[31,2,'wrap']]){
   await button(from).scrollIntoViewIfNeeded();await button(from).focus();await page.evaluate(()=>document.fonts.ready);
   async function capture(suffix){const path=`${theme}-${width}-${kind}-${suffix}.png`;await page.screenshot({path:out+'/'+path});report.captures.push({path,sha256:createHash('sha256').update(await readFile(out+'/'+path)).digest('hex')});}
   if(kind==='middle')await capture('control');
   await jump(from,to,'Enter');const box=await page.locator('.saved-article h3:focus').boundingBox();assert(box&&box.y>=0&&box.y+box.height<=1080);assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1),false);await capture('target');
  }
 }
 assert.equal(popups,0);
 report.checks.push('32 fixed items; middle/end anchors; zero/one/many unread; search/source/unread combinations; click, Tab/Enter/Space; unchanged registry; restricted/unknown stale handlers before/after parking; reduced-motion immediate visible focus; day/night 320/390.');
 await writeFile(out+'/saved-item-next-report.json',JSON.stringify(report,null,2)+'\n');console.log('PASS: saved item next unread');
}finally{await browser.close();server.closeAllConnections();await new Promise(r=>server.close(r));}
