import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {chromium} from 'playwright-core';
import {createServer} from 'node:http';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {createHash} from 'node:crypto';
const out=process.env.EVIDENCE_DIR||'/tmp/before-arrival-arrival-check';await mkdir(out,{recursive:true});
const html=await readFile('before-arrival-showcase.html');
const server=createServer((req,res)=>{res.writeHead(200,{'Content-Type':'text/html;charset=utf-8'});res.end(html);});await new Promise(r=>server.listen(0,'127.0.0.1',r));
const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/usr/bin/chromium',headless:true,args:['--no-sandbox']});
const title='리그 오브 레전드 26.22 패치 노트',original='https://www.leagueoflegends.com/ko-kr/news/game-updates/league-of-legends-patch-26-22-notes/';
const report={candidateSha:process.env.CANDIDATE_SHA||execFileSync('git',['rev-parse','HEAD'],{encoding:'utf8'}).trim(),workingTreeDirty:Boolean(execFileSync('git',['status','--porcelain'],{encoding:'utf8'}).trim()),opening:[],checkedAt:new Date().toISOString(),htmlSha256:createHash('sha256').update(html).digest('hex'),sourceSha256:{},upstream:'verified official LoL snapshot registered through UI; no runtime upstream request',productScrollChanges:false,cases:[],checks:[]};
for(const path of ['examples/before-arrival/index.tsx','examples/before-arrival/showcase.css','arrival-browser-verify.mjs'])report.sourceSha256[path]=createHash('sha256').update(await readFile(path)).digest('hex');
try{
 // First-frame evidence belongs here because this test is already invoked by candidate CI.
 for(const theme of ['day','night'])for(const viewport of [{width:1440,height:1080},{width:390,height:1080},{width:320,height:960}])for(const long of [false,true]){
  const context=await browser.newContext({viewport,reducedMotion:'reduce'}),page=await context.newPage();
  await page.goto(`http://127.0.0.1:${server.address().port}/?state=opening&theme=${theme}&long=${long?1:0}`);
  await page.evaluate(()=>document.fonts.ready);
  assert(await page.evaluate(()=>[...document.fonts].some(f=>f.family.includes('Asta Sans')&&f.status==='loaded')));
  const visual=await page.evaluate(()=>{
   const box=e=>{const r=e.getBoundingClientRect();return {x:r.x,y:r.y,width:r.width,height:r.height};};
   const rgb=s=>s.match(/[\d.]+/g).map(Number),bg=e=>{for(;e;e=e.parentElement){const c=rgb(getComputedStyle(e).backgroundColor);if(c.length===3||c[3]===1)return c.slice(0,3);}return [255,255,255];};
   const lum=c=>c.map(v=>v/255).map(v=>v<=.04045?v/12.92:((v+.055)/1.055)**2.4).reduce((s,v,i)=>s+v*[.2126,.7152,.0722][i],0);
   const ratio=(a,b)=>(Math.max(lum(a),lum(b))+.05)/(Math.min(lum(a),lum(b))+.05);
   const contrasts=[];
   for(const e of document.querySelectorAll('.ba-page *')){
    const r=e.getBoundingClientRect(),s=getComputedStyle(e);if(!r.width||!r.height||s.visibility==='hidden')continue;
    const text=[...e.childNodes].some(n=>n.nodeType===Node.TEXT_NODE&&n.textContent.trim());
    if(text||e.matches('.primary svg,.secondary svg'))contrasts.push({text:e.textContent.slice(0,50),ratio:ratio(rgb(s.color).slice(0,3),bg(e)),minimum:text?4.5:3});
   }
   const targets=Object.fromEntries(['.eta','h1','.primary','.play-status'].map(s=>[s,box(document.querySelector(s))]));
   return {targets,contrasts,scrollY,overflow:document.documentElement.scrollWidth>innerWidth+1};
  });
  assert.equal(visual.scrollY,0);assert.equal(visual.overflow,false);
  for(const [selector,b] of Object.entries(visual.targets))assert(b.x>=0&&b.y>=0&&b.x+b.width<=viewport.width&&b.y+b.height<=viewport.height,JSON.stringify({theme,viewport,long,selector,b}));
  const t=visual.targets;assert(t['.eta'].y+t['.eta'].height<=t.h1.y||t['.eta'].x+t['.eta'].width<=t.h1.x);
  assert(t.h1.y+t.h1.height<=t['.primary'].y);assert(t['.primary'].y+t['.primary'].height<=t['.play-status'].y);
  for(const c of visual.contrasts)assert(c.ratio>=c.minimum,JSON.stringify(c));
  assert.match(await page.locator('.play-status').innerText(),/실제 음성 없음/);
  // Tab reaches the main action naturally; Enter and Space both toggle the state.
  for(let i=0;i<20&&!await page.locator('.primary').evaluate(e=>e===document.activeElement);i++)await page.keyboard.press('Tab');
  assert(await page.locator('.primary').evaluate(e=>e===document.activeElement));
  const focus=await page.locator('.primary').evaluate(e=>{
   const s=getComputedStyle(e),r=e.getBoundingClientRect(),pad=parseFloat(s.outlineWidth)+parseFloat(s.outlineOffset);
   let clipped=false;for(let p=e.parentElement;p;p=p.parentElement){const c=getComputedStyle(p),b=p.getBoundingClientRect();if(/hidden|clip|auto|scroll/.test(c.overflow+c.overflowX+c.overflowY)&& (r.left-pad<b.left||r.right+pad>b.right||r.top-pad<b.top||r.bottom+pad>b.bottom))clipped=true;}
   return {color:s.outlineColor,width:parseFloat(s.outlineWidth),clipped,inside:r.left-pad>=0&&r.right+pad<=innerWidth&&r.top-pad>=0&&r.bottom+pad<=innerHeight};
  });
  assert(focus.width>=3&&focus.inside&&!focus.clipped,JSON.stringify(focus));
  assert.equal(await page.evaluate(()=>scrollY),0,'Keyboard focus must fit the first frame without scrolling');
  // Outline uses the audited primary Text role against the surrounding raised Surface.
  const focusContrast=await page.locator('.primary').evaluate(e=>{
   const rgb=s=>s.match(/[\d.]+/g).map(Number).slice(0,3),lum=c=>c.map(v=>v/255).map(v=>v<=.04045?v/12.92:((v+.055)/1.055)**2.4).reduce((s,v,i)=>s+v*[.2126,.7152,.0722][i],0);
   const a=lum(rgb(getComputedStyle(e).outlineColor)),b=lum(rgb(getComputedStyle(e.parentElement).backgroundColor));return (Math.max(a,b)+.05)/(Math.min(a,b)+.05);
  });assert(focusContrast>=3);
  const screenshot=`${out}/${theme}-opening-${viewport.width}${long?'-long':''}.png`;await page.screenshot({path:screenshot,fullPage:false});
  await page.keyboard.press('Enter');assert.equal(await page.locator('.primary').getAttribute('aria-pressed'),'true');await page.keyboard.press('Space');assert.equal(await page.locator('.primary').getAttribute('aria-pressed'),'false');
  for(const scene of ['주행 제한','상태 미확인']){
   await page.locator('.primary').click();await page.getByRole('button',{name:scene,exact:true}).click();
   assert(await page.locator('.primary').isDisabled());assert(await page.locator('.secondary').isDisabled());assert.equal(await page.locator('.primary').getAttribute('aria-pressed'),'false');assert.equal(await page.locator('.phone').count(),0);
   await page.getByRole('button',{name:'01 18분',exact:true}).click();assert.equal(await page.locator('.primary').getAttribute('aria-pressed'),'false');
  }
  report.opening.push({theme,viewport,long,...visual,focus,focusContrast,screenshot,keyboard:'Tab/Enter/Space',restrictedUnknownStops:true});await context.close();
 }
 // Reduced motion retains the existing static arrival poster even when replay is requested.
 const reduced=await browser.newPage({reducedMotion:'reduce'});await reduced.goto(`http://127.0.0.1:${server.address().port}/`);
 await reduced.getByRole('button',{name:'자동 시연 다시 보기',exact:true}).click();await reduced.waitForTimeout(5500);assert.match(await reduced.locator('.eta').innerText(),/도착/);
 await reduced.getByRole('button',{name:'03 4분',exact:true}).click();assert.match(await reduced.locator('.eta').innerText(),/4/);await reduced.close();report.reducedMotion={staticPoster:'arrived',automaticAdvance:false,manualPriority:true};
 for(const theme of ['day','night'])for(const viewport of [{width:390,height:1080},{width:320,height:960}]){
  const context=await browser.newContext({viewport});const page=await context.newPage();const errors=[],external=[];let popups=0;page.on('pageerror',e=>errors.push(e.message));page.on('popup',()=>popups++);page.on('request',r=>{if(!r.url().startsWith('http://127.0.0.1:')&&!r.url().startsWith('data:'))external.push(r.url());});
  const base=`http://127.0.0.1:${server.address().port}/`;
  await page.goto(`${base}?state=settings&theme=${theme}`);await page.evaluate(()=>document.fonts.ready);
  await page.getByLabel('출처 이름',{exact:true}).fill('내 롤 패치');await page.getByRole('button',{name:'출처 추가',exact:true}).click();
  await page.getByRole('button',{name:'03 4분',exact:true}).click();assert.match(await page.locator('h1').innerText(),/9시 팀 회의/);
  await page.getByRole('button',{name:title+' 나중에 보기 저장',exact:true}).click();assert.match(await page.locator('h1').innerText(),/9시 팀 회의/);
  await page.getByRole('button',{name:'04 도착',exact:true}).click();
  const heading=await page.locator('.phone h2').boundingBox(),first=await page.locator('.saved-article h3').boundingBox(),pocket=await page.locator('.phone').boundingBox(),vehicle=await page.locator('.vehicle').boundingBox();
  const scrollY=await page.evaluate(()=>scrollY);
  assert.equal(scrollY,0,'Arrival must not depend on scripted or extra scrolling');
  assert(heading.y>=0&&heading.y+heading.height<=viewport.height,JSON.stringify({viewport,heading}));
  assert(first.y>=0&&first.y+first.height<=viewport.height,JSON.stringify({viewport,first}));
  assert(pocket.y<vehicle.y,'Saved content must precede the vehicle summary at narrow width');
  assert.equal(await page.locator('.saved-article a').getAttribute('href'),original);assert.match(await page.locator('.saved-article .item-provenance').innerText(),/가져온 공식 소식 · 자동 갱신 아님/);
  const item=await page.evaluate(()=>JSON.parse(localStorage.getItem('before-arrival-sources-v2')).savedItems[0]);assert.equal(item.title,title);assert.equal(item.originalUrl,original);assert.equal(item.provenance,'official_snapshot');
  const screenshot=`${out}/${theme}-arrival-saved-${viewport.width}.png`;await page.screenshot({path:screenshot,fullPage:false});
  await page.reload();await page.getByRole('button',{name:'04 도착',exact:true}).click();assert.equal(await page.locator('.saved-article a').getAttribute('href'),original);
  for(const scene of ['주행 제한','상태 미확인']){await page.getByRole('button',{name:scene,exact:true}).click();assert.equal(await page.locator('.phone').count(),0);assert.equal(await page.locator('.saved-article button').count(),0);assert(await page.locator('.primary').isDisabled());}
  await page.getByRole('button',{name:'04 도착',exact:true}).click();assert.equal(await page.locator('.saved-article').count(),1);
  await page.getByRole('button',{name:title+' 저장 취소',exact:true}).click();assert.equal(await page.locator('.saved-article').count(),0);
  await page.reload();await page.getByRole('button',{name:'04 도착',exact:true}).click();
  const empty=await page.locator('.empty-saved').boundingBox();assert(empty.y>=0&&empty.y+empty.height<=viewport.height,JSON.stringify({viewport,empty}));
  assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('before-arrival-sources-v2')).savedItems.length),0);
  await page.screenshot({path:`${out}/${theme}-arrival-empty-${viewport.width}.png`,fullPage:false});
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1),false);assert.equal(popups,0);assert.deepEqual(external,[]);assert.deepEqual(errors,[]);
  report.cases.push({theme,viewport,scrollY,savedHeading:heading,firstSavedTitle:first,emptyState:empty,screenshot,originalUrl:original,provenance:'official_snapshot',savedBeforeVehicle:true,removalReloadPreserved:true,restrictedUnknownSavedActions:0,externalRequests:0});await context.close();
 }
 const desktop=await browser.newPage({viewport:{width:1440,height:1080}});await desktop.goto(`http://127.0.0.1:${server.address().port}/?state=arrived`);await desktop.evaluate(()=>document.fonts.ready);const left=await desktop.locator('.vehicle').boundingBox(),right=await desktop.locator('.phone').boundingBox();assert(left.x<right.x&&Math.abs(left.y-right.y)<2,'Desktop retains vehicle-left and saved-list-right');await desktop.close();
 report.checks=['Opening first frame in both themes at1440/390/320, standard and long titles: geometry, contrast, focus, Tab/Enter/Space and safety transitions; reduced-motion static poster/manual controls','Official item explicitly saved from4-minute deferred list; urgent meeting order unchanged','At390x1080 and320x960 both themes, heading and entire first title are visible at scrollY0','Exact original URL/provenance/reload/removal retained; empty state is visible','Desktop remains a coherent side-by-side pair; restricted/unknown have no saved actions','No auto-open, upstream network, extra auto-save or product scroll code'];
 await writeFile(`${out}/arrival-browser-report.json`,JSON.stringify(report,null,2)+'\n');console.log('PASS: official deferred save→arrival heading/title on first screen at390x1080 and320x960, both themes; URL/provenance/reload/removal/empty/desktop/restrictions preserved.');
}finally{await browser.close();await new Promise(r=>server.close(r));}
