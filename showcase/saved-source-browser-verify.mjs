import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {chromium} from 'playwright-core';
import {createServer} from 'node:http';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {createHash} from 'node:crypto';
const out=process.env.EVIDENCE_DIR||'validation/saved-source';await mkdir(out,{recursive:true});
const html=await readFile('before-arrival-showcase.html');const server=createServer((req,res)=>{res.writeHead(200,{'Content-Type':'text/html;charset=utf-8'});res.end(html);});await new Promise(r=>server.listen(0,'127.0.0.1',r));
const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/usr/bin/chromium',headless:true,args:['--no-sandbox']});const base=`http://127.0.0.1:${server.address().port}`;
const A='리그 오브 레전드 26.22 패치 노트',B='발로란트 13.09 패치 노트',C='수정2 클라이언트 1.2.420 업데이트 안내',D='이번 주말, 협동 모드가 열려요';
const key='before-arrival-sources-v2';const report={candidateSha:process.env.CANDIDATE_SHA||execFileSync('git',['rev-parse','HEAD'],{encoding:'utf8'}).trim(),workingTreeDirty:Boolean(execFileSync('git',['status','--porcelain'],{encoding:'utf8'}).trim()),ime:'Synthetic composition events only; physical Korean IME remains a manual check.',scope:'Browser sample only; no APK installation, emulator or actual Pleos SDK evidence.',captures:[],checkedAt:new Date().toISOString(),htmlSha256:createHash('sha256').update(html).digest('hex'),sourceSha256:{},upstream:'official snapshots registered through UI; no external network',checks:[],readability:[]};
for(const path of ['examples/before-arrival/index.tsx','examples/before-arrival/saved-undo.ts','examples/before-arrival/showcase.css','saved-source-browser-verify.mjs','package.json'])report.sourceSha256[path]=createHash('sha256').update(await readFile(path)).digest('hex');
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

 const ids=[...new Set(seed.savedItems.map(i=>i.sourceId))];assert.equal(ids.length,3);
 const name='아주 긴 출처 이름으로 좁은 화면에서도 선택할 수 있는 저장 소식 출처';seed.sources.filter(s=>ids.slice(0,2).includes(s.id)).forEach(s=>s.name=name);seed.savedItems.forEach((item,index)=>item.reviewed=index===2);
 await page.evaluate(({key,seed})=>localStorage.setItem(key,JSON.stringify(seed)),{key,seed});await page.reload();
 const source=page.getByLabel('저장한 출처',{exact:true}),search=page.getByRole('searchbox'),unread=page.getByRole('button',{name:'미확인만',exact:true}),all=page.getByRole('button',{name:'전체',exact:true});
 async function expectItems(items){assert.deepEqual(await titles(page),items.map(i=>i.title));assert.match(await page.locator('.saved-filters').innerText(),new RegExp(`표시 ${items.length}건`));}
 assert.equal(await source.inputValue(),'');assert.equal(new Set(await source.locator('option').allTextContents()).size,4);
 for(const id of ids){await source.selectOption(id);await expectItems(seed.savedItems.filter(i=>i.sourceId===id));}
 await source.selectOption(ids[0]);await search.fill('PATCH');await unread.click();await expectItems(seed.savedItems.filter(i=>i.sourceId===ids[0]&&!i.reviewed&&i.title.toLowerCase().includes('patch')));assert.deepEqual(await read(page),seed);
 await search.fill('없음');assert.match(await page.locator('.empty-saved').innerText(),/조건에 맞는 항목이 없어요/);await page.getByRole('button',{name:'전체 출처로 돌아가기'}).click();assert.equal(await source.inputValue(),'');assert.equal(await search.inputValue(),'없음');
 await search.fill('');await all.click();await source.selectOption(ids[1]);const target=seed.savedItems.find(i=>i.sourceId===ids[1]);
 await remove(page,target.title).click();assert.equal(await source.inputValue(),ids[1]);await expectItems([]);assert.match(await page.locator('.empty-saved').innerText(),/조건에 맞는 항목이 없어요/);await undo(page).click();await expectItems([target]);assert.equal(await source.inputValue(),ids[1]);assert.equal(await page.evaluate(()=>document.activeElement.textContent),target.title);assert.deepEqual(await read(page),seed);
 await page.getByRole('button',{name:target.title+' 확인 완료',exact:true}).click();await unread.click();await expectItems([]);await page.getByRole('button',{name:'확인 실행 취소',exact:true}).click();await expectItems([target]);assert.deepEqual(await read(page),seed);await all.click();
 for(const blocked of ['주행 제한','상태 미확인']){const stale=await source.elementHandle();await page.getByRole('button',{name:'01 18분',exact:true}).click();await page.getByRole('button',{name:'듣기 시연',exact:true}).click();await page.getByRole('button',{name:blocked,exact:true}).click();await stale.evaluate(el=>{el.value='';el.dispatchEvent(new Event('change',{bubbles:true}));});assert.equal(await source.count(),0);assert.equal(await page.locator('.primary').getAttribute('aria-pressed'),'false');await page.getByRole('button',{name:'04 도착',exact:true}).click();assert.equal(await source.inputValue(),ids[1]);assert.deepEqual(await read(page),seed);assert.equal(await page.locator('.primary').getAttribute('aria-pressed'),'false');}
 await page.reload();assert.equal(await source.inputValue(),'');await expectItems(seed.savedItems);
 for(const theme of ['day','night'])for(const width of [320,390]){
 await page.setViewportSize({width,height:1080});await page.goto(base+'/?state=arrived&theme='+theme);await tabTo(page,source);await page.keyboard.press('Home');await page.keyboard.press('ArrowDown');await page.keyboard.press('Enter');assert.equal(await source.inputValue(),ids[0]);await page.evaluate(()=>document.fonts.ready);
 const audit=await source.evaluate(el=>{const c=getComputedStyle(el),rgb=s=>s.match(/[\d.]+/g).map(Number).slice(0,3),lum=c=>c.map(v=>v/255).map(v=>v<=.04045?v/12.92:((v+.055)/1.055)**2.4).reduce((n,v,i)=>n+v*[.2126,.7152,.0722][i],0),a=lum(rgb(c.color)),b=lum(rgb(c.backgroundColor));return {contrast:(Math.max(a,b)+.05)/(Math.min(a,b)+.05),fontSize:parseFloat(c.fontSize),pageOverflow:document.documentElement.scrollWidth>innerWidth+1};});assert(audit.contrast>=4.5);assert(audit.fontSize>=16);assert.equal(audit.pageOverflow,false);report.readability.push({theme,width,...audit});
 for(const empty of [false,true]){await search.fill(empty?'없는 제목':'');const path=`${theme}-${width}-${empty?'empty':'source'}.png`;await page.screenshot({path:out+'/'+path,fullPage:true});report.captures.push({path,sha256:createHash('sha256').update(await readFile(out+'/'+path)).digest('hex')});}
 }
 report.checks.push('Three source IDs; duplicate long names; exact counts/order; source/search/unread intersection; unchanged storage; empty reset; last deletion/undo; review undo; session reset; stale blocked handlers; stopped playback; keyboard, contrast and overflow at 320/390 day/night.');
 await writeFile(out+'/source-report.json',JSON.stringify(report,null,2)+'\n');console.log('PASS: saved source filter');
}finally{await browser.close();server.closeAllConnections();await new Promise(r=>server.close(r));}
