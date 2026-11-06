import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {chromium} from 'playwright-core';
import {createServer} from 'node:http';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {createHash} from 'node:crypto';
const out=process.env.EVIDENCE_DIR||'validation/review-next';await mkdir(out,{recursive:true});
const html=await readFile('before-arrival-showcase.html');const server=createServer((req,res)=>{res.writeHead(200,{'Content-Type':'text/html;charset=utf-8'});res.end(html);});await new Promise(r=>server.listen(0,'127.0.0.1',r));
const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/usr/bin/chromium',headless:true,args:['--no-sandbox']});const base=`http://127.0.0.1:${server.address().port}`;
const A='리그 오브 레전드 26.22 패치 노트',B='발로란트 13.09 패치 노트',C='수정2 클라이언트 1.2.420 업데이트 안내';
const key='before-arrival-sources-v2';const report={candidateSha:process.env.CANDIDATE_SHA||execFileSync('git',['rev-parse','HEAD'],{encoding:'utf8'}).trim(),workingTreeDirty:Boolean(execFileSync('git',['status','--porcelain'],{encoding:'utf8'}).trim()),ime:'Synthetic composition events only; physical Korean IME remains a manual check.',scope:'Browser sample only; no APK installation, emulator or actual Pleos SDK evidence.',captures:[],checkedAt:new Date().toISOString(),htmlSha256:createHash('sha256').update(html).digest('hex'),sourceSha256:{},upstream:'official snapshots registered through UI; no external network',checks:[],readability:[]};
for(const path of ['examples/before-arrival/index.tsx','examples/before-arrival/saved-undo.ts','examples/before-arrival/showcase.css','review-next-browser-verify.mjs','package.json'])report.sourceSha256[path]=createHash('sha256').update(await readFile(path)).digest('hex');
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
 const button=i=>page.getByRole('button',{name:fixture.savedItems[i].title+' 확인하고 다음 미확인',exact:true});
 async function load(unread,count=32){const data={...fixture,savedItems:fixture.savedItems.slice(0,count).map((item,i)=>({...item,reviewed:!unread.includes(i)}))};await page.evaluate(({key,data})=>{localStorage.clear();localStorage.setItem(key,JSON.stringify(data));localStorage.setItem('before-arrival-saved-position-v1','unchanged');},{key,data});await page.goto(base+'/?state=arrived');return data;}
 async function advance(i,to,method='Enter'){
  const before=await read(page);await button(i).focus();await page.keyboard.press(method);
  assert.deepEqual(await read(page),{...before,savedItems:before.savedItems.map((item,j)=>j===i?{...item,reviewed:true}:item)});
  assert.equal(await page.evaluate(()=>document.activeElement.textContent),to===null?'현재 조건에 맞는 미확인 소식이 없어요. 모두 확인했어요.':fixture.savedItems[to].title);
  assert.equal(await page.evaluate(()=>localStorage.getItem('before-arrival-saved-position-v1')),'unchanged');
 }
 for(const source of ['',fixture.savedItems[17].sourceId])for(const query of ['','홀수'])for(const unread of [false,true]){
  const data=await load([2,17,31]);await page.getByLabel('저장한 출처',{exact:true}).selectOption(source);await page.getByRole('searchbox').fill(query);await page.getByRole('button',{name:unread?'미확인만':'전체',exact:true}).click();
  const indices=data.savedItems.map((item,i)=>({item,i})).filter(({item})=>(!source||item.sourceId===source)&&item.title.includes(query)&&!item.reviewed).map(x=>x.i);
  if(indices.length)await advance(indices.at(-1),indices.length>1?indices[0]:null,'Space');
 }
 await load([],0);assert.equal(await page.locator('.saved-review-next').count(),0);
 await load([0],1);await advance(0,null);await page.getByRole('button',{name:'확인 실행 취소',exact:true}).click();assert.equal((await read(page)).savedItems[0].reviewed,false);await advance(0,null);await page.reload();assert.equal((await read(page)).savedItems[0].reviewed,true);
 await load([2,17,31]);await button(2).focus();const before=await read(page);
 await page.evaluate(()=>{window.realSet=Storage.prototype.setItem;Storage.prototype.setItem=function(k,v){if(k==='before-arrival-sources-v2')throw new Error('quota');return window.realSet.call(this,k,v);};});
 await page.keyboard.press('Enter');assert.deepEqual(await read(page),before);assert.equal(await button(2).evaluate(el=>el===document.activeElement),true);assert.match(await page.locator('.phone [role=alert]').innerText(),/다시/);
 await page.evaluate(()=>Storage.prototype.setItem=window.realSet);await advance(2,17);
 for(const blocked of ['주행 제한','상태 미확인']){
  await load([2,17]);const stale=await button(2).evaluateHandle(el=>el[Object.keys(el).find(k=>k.startsWith('__reactProps$'))].onClick),before=await read(page);
  await page.getByRole('button',{name:blocked,exact:true}).click();await stale.evaluate(fn=>fn());assert.deepEqual(await read(page),before);
  await page.getByRole('button',{name:'04 도착',exact:true}).click();const focus=await page.evaluate(()=>document.activeElement.textContent);await stale.evaluate(fn=>fn());assert.equal(await page.evaluate(()=>document.activeElement.textContent),focus);assert.deepEqual(await read(page),before);assert.equal(await page.locator('.primary').getAttribute('aria-pressed'),'false');
 }
 await page.emulateMedia({reducedMotion:'reduce'});
 for(const theme of ['day','night'])for(const [width,height] of [[320,960],[390,1080]]){
  await load([2,17,31]);await page.setViewportSize({width,height});await page.goto(base+'/?state=arrived&theme='+theme);await button(2).scrollIntoViewIfNeeded();await tabTo(page,button(2));await page.evaluate(()=>document.fonts.ready);
  assert.notEqual(await button(2).evaluate(el=>getComputedStyle(el).outlineStyle),'none');await advance(2,17);
  const box=await page.locator('.saved-article h3:focus').boundingBox();assert(box&&box.y>=0&&box.y+box.height<=height);assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1),false);
  const path=theme+'-'+width+'-review-next.png';await page.screenshot({path:out+'/'+path});report.captures.push({path,sha256:createHash('sha256').update(await readFile(out+'/'+path)).digest('hex')});
 }
 report.checks.push('0/1/32; source/search/unread combinations; wrap; exact single-item persistence; keyboard; failure/retry; reload/undo; unchanged position; restricted/unknown stale handlers; day/night 320x960 and 390x1080 focus/overflow.');
 await writeFile(out+'/review-next-report.json',JSON.stringify(report,null,2)+'\n');console.log('PASS: review and next unread');
}finally{await browser.close();server.closeAllConnections();await new Promise(r=>server.close(r));}
