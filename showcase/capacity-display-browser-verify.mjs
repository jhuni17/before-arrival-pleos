import assert from 'node:assert/strict';
import {build} from 'esbuild';
import {execFileSync} from 'node:child_process';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {resolve} from 'node:path';
import {createHash} from 'node:crypto';
import {createServer} from 'node:http';
import {chromium} from 'playwright-core';
const out='validation/capacity-display';await mkdir(out,{recursive:true});
const load=async options=>{const r=await build({...options,bundle:true,write:false,platform:'node',format:'esm'});return import('data:text/javascript;base64,'+Buffer.from(r.outputFiles[0].text).toString('base64'));};
const c=await load({entryPoints:['examples/before-arrival/catalog.ts']}),u=await load({entryPoints:['examples/before-arrival/saved-undo.ts']});
const candidate=execFileSync('git',['rev-parse','HEAD'],{encoding:'utf8'}).trim();
const seed=c.defaults(),item=seed.sources[0].items[0];seed.savedItems=Array.from({length:200},(_,i)=>({...item,id:'capacity-'+i,title:'보관 소식 '+i,originalUrl:null,reviewed:i%2===0}));
const extra={...item,id:'extra',originalUrl:null};
assert.throws(()=>c.saveItem(seed,extra),/200건/);assert.equal(c.saveItem(seed,seed.savedItems[0]),seed);
const removal=u.prepareSavedRemoval(seed,seed.savedItems[0]);assert.throws(()=>u.prepareSavedRestore(c.saveItem(removal.next,extra),removal.removal),/200건/);assert(u.prepareSavedRestore(seed,removal.removal).alreadyPresent);
const full=c.saveItem({...seed,savedItems:seed.savedItems.slice(0,199)},extra);assert.deepEqual(c.readRegistry({getItem:()=>JSON.stringify(full)}),full);
const html=await readFile('before-arrival-showcase.html'),server=createServer((req,res)=>res.end(html));await new Promise(r=>server.listen(0,'127.0.0.1',r));
const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH,headless:true,args:['--no-sandbox']});const base=`http://127.0.0.1:${server.address().port}`,key=c.registryKey;
const report={candidate,checks:['Model capacity, duplicate, restore, 199→200 persistence'],captures:[],hashes:{}};
try{
const p=await browser.newPage(),errors=[];p.on('pageerror',e=>errors.push(e.message));const read=async()=>{const value=await p.evaluate(k=>JSON.parse(localStorage.getItem(k)),key);if(await p.locator('.saved-capacity').count())await capacity(value.savedItems.length);return value;},save=()=>p.locator('.secondary'),undo=()=>p.getByRole('button',{name:'삭제 실행 취소',exact:true}),scene=name=>p.getByRole('button',{name,exact:true}).click(),remove=title=>p.getByRole('button',{name:title+' 저장 취소',exact:true}).click();
async function setup(value,theme='day'){await p.goto(base+'/?state=opening&theme='+theme);await p.evaluate(({key,value})=>localStorage.setItem(key,JSON.stringify(value)),{key,value});await p.reload();await p.evaluate(k=>{window.writes=0;window.fail=false;const orig=Storage.prototype.setItem;Storage.prototype.setItem=function(a,b){if(a===k){window.writes++;if(window.fail)throw Error('denied');}return orig.call(this,a,b);};},key);}
async function capacity(n){assert.match(await p.locator('.saved-capacity').innerText(),new RegExp('보관 '+n+'/200건 · 전체 용량'));}
for(const n of [0,199,200]){
await setup({...seed,savedItems:seed.savedItems.slice(0,n)});await scene('04 도착');await capacity(n);
await p.locator('#saved-title-search').fill('보관 소식 0');await capacity(n);
for(const name of ['미확인만','확인 완료만','전체']){await p.getByRole('button',{name,exact:true}).click();await capacity(n);}
const options=await p.locator('#saved-source-filter option').evaluateAll(els=>els.map(el=>el.value));
for(const value of options){await p.locator('#saved-source-filter').selectOption(value);await capacity(n);}
await p.reload();await scene('04 도착');await capacity(n);
}
for(const theme of ['day','night'])for(const width of [320,390]){
await p.setViewportSize({width,height:width===320?960:1080});await setup(seed,theme);await save().focus();await p.keyboard.press('Enter');assert.match(await p.locator('.storage-error').innerText(),/200건.*직접 삭제.*다시/);assert.equal(await p.evaluate(()=>window.writes),0);assert.deepEqual(await read(),seed);assert(await save().evaluate(el=>el===document.activeElement));assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1),false);await p.evaluate(()=>document.fonts.ready);const capture=`${out}/${theme}-${width}.png`;await p.screenshot({path:capture});report.captures.push(capture);
await setup(seed,theme);await scene('04 도착');await capacity(200);assert.match(await p.locator('.saved-capacity').innerText(),/직접 삭제.*재시도/);
await p.evaluate(()=>document.fonts.ready);
for(const selector of ['.phone h2','.saved-article h3']){const box=await p.locator(selector).first().boundingBox();assert(box.y>=0&&box.y+box.height<=p.viewportSize().height,selector+' fully in first viewport '+JSON.stringify(box));}
assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1),false);
const arrivalCapture=out+'/'+theme+'-'+width+'-arrival.png';await p.screenshot({path:arrivalCapture});report.captures.push(arrivalCapture);
assert.equal(await p.locator('.saved-article h3').first().innerText(),'보관 소식 0');await remove('보관 소식 0');await capacity(199);await scene('01 18분');await save().focus();await p.keyboard.press('Enter');const saved=await read();assert.equal(saved.savedItems.length,200);await p.reload();assert.deepEqual(await read(),saved);await scene('04 도착');await capacity(200);
}
await setup(seed);await scene('04 도착');await remove('보관 소식 0');await capacity(199);await scene('01 18분');await save().click();await scene('04 도착');const fullState=await read(),writes=await p.evaluate(()=>window.writes);await undo().focus();await p.keyboard.press('Enter');assert.match(await p.locator('.saved-undo-status').innerText(),/복원하지.*200건/);assert.deepEqual(await read(),fullState);assert.equal(await p.evaluate(()=>window.writes),writes);assert(await undo().evaluate(el=>el===document.activeElement));
await remove('보관 소식 1');const space=await read();await p.evaluate(()=>window.fail=true);await undo().focus();await p.keyboard.press('Enter');assert.deepEqual(await read(),space);assert(await undo().evaluate(el=>el===document.activeElement));await p.evaluate(()=>window.fail=false);await p.keyboard.press('Enter');assert.equal((await read()).savedItems.length,200);assert.deepEqual((await read()).savedItems[0],seed.savedItems[0]);
await setup({...seed,savedItems:seed.savedItems.slice(0,199)});await p.evaluate(()=>window.fail=true);await save().focus();await p.keyboard.press('Enter');assert.equal((await read()).savedItems.length,199);assert(await save().evaluate(el=>el===document.activeElement));await p.evaluate(()=>window.fail=false);await p.keyboard.press('Enter');const duplicate=await read(),dupWrites=await p.evaluate(()=>window.writes);await save().click();assert.deepEqual(await read(),duplicate);assert.equal(await p.evaluate(()=>window.writes),dupWrites);assert.equal(await p.locator('.storage-error').count(),0);
for(const blocked of ['주행 제한','상태 미확인']){await setup({...seed,savedItems:seed.savedItems.slice(0,199)});const staleSave=await save().elementHandle();await scene('04 도착');const staleDelete=await p.getByRole('button',{name:'보관 소식 0 저장 취소',exact:true}).elementHandle();await remove('보관 소식 1');const staleUndo=await undo().elementHandle(),snapshot=await read();await scene('01 18분');await p.locator('.primary').click();await scene(blocked);for(const h of [staleSave,staleDelete,staleUndo])await h.evaluate(el=>el.click());assert.deepEqual(await read(),snapshot);assert.equal(await p.locator('.primary').getAttribute('aria-pressed'),'false');await scene('01 18분');for(const h of [staleSave,staleDelete,staleUndo])await h.evaluate(el=>el.click());assert.deepEqual(await read(),snapshot);assert.equal(await p.locator('.primary').getAttribute('aria-pressed'),'false');}
assert.deepEqual(errors,[]);report.checks.push('Browser no-write refusal, keyboard delete/save/reload, duplicate no-write, full restore retry after space, failed writes preserve data/focus, restricted/unknown stale handlers and stopped playback','Day/night 320×960, 390×1080 error/focus/overflow/arrival first title');
}finally{await browser.close();server.closeAllConnections();await new Promise(r=>server.close(r));}
for(const path of ['examples/before-arrival/catalog.ts','examples/before-arrival/saved-undo.ts','examples/before-arrival/index.tsx','capacity-display-browser-verify.mjs','examples/before-arrival/showcase.css','before-arrival-showcase.html',...report.captures])report.hashes[path]=createHash('sha256').update(await readFile(path)).digest('hex');await writeFile(out+'/report.json',JSON.stringify(report,null,2)+'\n');console.log('PASS focused saved capacity checks');
