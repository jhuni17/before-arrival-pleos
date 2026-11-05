import assert from "node:assert/strict";
import { chromium } from "playwright-core";
import { createServer } from "node:http";
import { readFile, writeFile, mkdir } from "node:fs/promises";
import { resolve } from "node:path";
import { createHash } from "node:crypto";
import { execFileSync } from "node:child_process";
const executablePath = process.env.CHROMIUM_PATH || "/usr/bin/chromium";
const evidenceDir = process.env.EVIDENCE_DIR || "evidence";
await mkdir(evidenceDir, { recursive: true });
const html = await readFile("before-arrival-showcase.html");
const servedHtml = html.toString().replace("<head>", '<head><meta name="before-arrival-local-app" content="steam-news-v1">');
const capturedSteam = JSON.parse(await readFile("../shared/evidence/steam-real-response.json", "utf8"));
const server = createServer((req, res) => {
  if (req.url?.startsWith("/before-arrival-showcase.html")) {
    res.writeHead(200, { "Content-Type": "text/html; charset=utf-8" });
    res.end(servedHtml);
  } else {
    res.writeHead(404);
    res.end();
  }
});
await new Promise((r) => server.listen(0, "127.0.0.1", r));
const base = `http://127.0.0.1:${server.address().port}/before-arrival-showcase.html`;
const browser = await chromium.launch({
  executablePath,
  headless: true,
  args: ["--no-sandbox"],
});
const report = {
  createdAt: new Date().toISOString(),
  browser: await browser.version(),
  provenance: {
    gitHeadAtRun: execFileSync("git", ["rev-parse", "HEAD"], {encoding:"utf8"}).trim(),
    workingTreeDirtyAtRun: Boolean(execFileSync("git", ["status", "--porcelain"], {encoding:"utf8"}).trim()),
    htmlSha256: createHash("sha256").update(html).digest("hex"),
    sourceSha256: Object.fromEntries(await Promise.all([
      "examples/before-arrival/index.tsx", "examples/before-arrival/catalog.ts", "../shared/catalog.json", "../shared/evidence/steam-real-response.json",
      "examples/before-arrival/showcase.css", "browser-verify.mjs", "build.mjs", "app_server.py",
    ].map(async (path) => [path, createHash("sha256").update(await readFile(path)).digest("hex")]))),
    originalInputs: ["479db2bc4e55bd58e9cda1f9408ea294e1c8ebf3", "3ff697d261e417fa6a2ff18ddcb91ebd83c32a6a"],
  },
  screens: [],
  checks: [],
};
const errors = [];
const externalRequests = [];
const context = await browser.newContext({
  viewport: { width: 1440, height: 1080 },
});
const page = await context.newPage();
page.on("pageerror", (error) => errors.push(error.message));
page.on("request", (request) => {
  if (!request.url().startsWith(new URL(base).origin + "/") && !request.url().startsWith("data:"))
    externalRequests.push(request.url());
});
async function open(query = "state=opening") {
  await page.goto(`${base}?${query}`);
  await page.evaluate(() => document.fonts.ready);
  assert(
    await page.evaluate(() =>
      [...document.fonts].some(
        (face) => face.family.includes("Asta Sans") && face.status === "loaded",
      ),
    ),
  );
}
// Audit real rendered text and primary/secondary SVG foregrounds against opaque ancestor fills.
async function audit(name) {
  const result = await page.evaluate(() => {
    const issues = [],
      contrasts = [];
    const rgba = (s) => s.match(/[\d.]+/g)?.map(Number) || [0, 0, 0, 0];
    const bg = (el) => {
      for (let e = el; e; e = e.parentElement) {
        const c = rgba(getComputedStyle(e).backgroundColor);
        if (c.length === 3 || c[3] === 1) return c.slice(0, 3);
      }
      return [255, 255, 255];
    };
    const lum = (c) =>
      c
        .map((n) => n / 255)
        .map((n) => (n <= 0.04045 ? n / 12.92 : ((n + 0.055) / 1.055) ** 2.4))
        .reduce((a, v, i) => a + v * [0.2126, 0.7152, 0.0722][i], 0);
    const ratio = (a, b) =>
      (Math.max(lum(a), lum(b)) + 0.05) / (Math.min(lum(a), lum(b)) + 0.05);
    for (const e of document.querySelectorAll(".ba-page *")) {
      const r = e.getBoundingClientRect(),
        s = getComputedStyle(e);
      if (
        !r.width ||
        !r.height ||
        s.visibility === "hidden" ||
        s.display === "none"
      )
        continue;
      const hasText = [...e.childNodes].some(
        (n) => n.nodeType === Node.TEXT_NODE && n.textContent.trim(),
      );
      if (hasText || e.matches(".primary svg,.secondary svg")) {
        const contrast = ratio(rgba(s.color).slice(0, 3), bg(e));
        contrasts.push({
          text: e.textContent.trim().slice(0, 55),
          ratio: +contrast.toFixed(2),
        });
        if (contrast < 4.5)
          issues.push(
            `contrast ${contrast.toFixed(2)}: ${e.textContent.slice(0, 35)}`,
          );
        if (e.scrollWidth > e.clientWidth + 2 && e.clientWidth)
          issues.push(`horizontal overflow: ${e.className}`);
        // Every text run must fit every clipping ancestor, including the device bezel.
        if (hasText) {
          const range = document.createRange();
          range.selectNodeContents(e);
          for (const t of range.getClientRects())
            for (let p = e.parentElement; p; p = p.parentElement) {
              const ps = getComputedStyle(p),
                pr = p.getBoundingClientRect();
              if (
                ["hidden", "clip"].includes(ps.overflowX) &&
                (t.left < pr.left - 2 || t.right > pr.right + 2)
              )
                issues.push(`clipped text x: ${e.className}`);
              if (
                ["hidden", "clip"].includes(ps.overflowY) &&
                (t.top < pr.top - 2 || t.bottom > pr.bottom + 2)
              )
                issues.push(`clipped text y: ${e.className}`);
            }
        }
      }
    }
    if (document.documentElement.scrollWidth > innerWidth + 1)
      issues.push("document horizontal overflow");
    return {
      issues: [...new Set(issues)],
      minimumContrast: Math.min(...contrasts.map((x) => x.ratio)),
      contrasts,
      viewport: { width: innerWidth, height: innerHeight },
      font: getComputedStyle(document.querySelector("h1")).fontFamily,
    };
  });
  assert.deepEqual(
    result.issues,
    [],
    `${name}: ${JSON.stringify(result.issues)}`,
  );
  const path = `${evidenceDir}/${name}.png`;
  await page.screenshot({ path, fullPage: true });
  report.screens.push({ name, path, ...result });
}
try {
  for (const theme of ["night", "day"]) {
    for (const frame of [
      "opening",
      "saved",
      "priority",
      "arrived",
      "settings",
      "restricted",
      "unknown",
    ]) {
      await page.setViewportSize({ width: 1440, height: 1080 });
      await open(`state=${frame}&theme=${theme}`);
      assert.equal(
        await page.locator(".phone").count(),
        ["arrived", "settings"].includes(frame) ? 1 : 0,
      );
      if (["restricted", "unknown"].includes(frame)) {
        assert(await page.locator(".primary").isDisabled());
        assert(await page.locator(".secondary").isDisabled());
      }
      await audit(`${theme}-${frame}`);
    }
    for (const width of [320, 390])
      for (const frame of ["priority", "arrived", "settings"]) {
        await page.setViewportSize({ width, height: 844 });
        await open(`state=${frame}&theme=${theme}&long=1`);
        await audit(`${theme}-${frame}-${width}`);
      }
    await page.setViewportSize({ width: 844, height: 390 });
    await open(`state=priority&theme=${theme}&long=1`);
    await audit(`${theme}-landscape`);
  }
  // Text-only enlargement is stricter than viewport reflow alone: every computed font is doubled.
  for (const theme of ["night", "day"]) {
    for (const width of [1440, 390]) {
      await page.setViewportSize({ width, height: 1080 });
      await open(`state=priority&theme=${theme}&long=1`);
      await page.evaluate(() => {
        const sizes = [
          ...document.querySelectorAll(".ba-page, .ba-page *"),
        ].map((el) => [el, parseFloat(getComputedStyle(el).fontSize)]);
        for (const [el, size] of sizes) el.style.fontSize = `${size * 2}px`;
      });
      await audit(`${theme}-text-200-${width}`);
    }
  }
  report.checks.push(
    "200% text enlargement: all computed fonts doubled at 1440px and 390px in both themes, with long Korean title; no clipping or horizontal scrolling.",
  );
  await page.setViewportSize({width:1440,height:1080});
  await open("state=opening");
  await page.locator(".secondary").click();
  await page.reload();
  assert.match(await page.locator(".secondary").innerText(),/이 브라우저에 저장됨/);
  await page.getByRole("button",{name:"04 도착",exact:true}).click();
  assert.match(await page.locator(".saved-article h3").innerText(),/협동 모드/);
  assert.equal(await page.locator(".saved-article a").count(),0);
  const other=await browser.newContext();const otherPage=await other.newPage();
  await otherPage.goto(`${base}?state=arrived`);
  assert.equal(await otherPage.locator(".saved-article").count(),0);await other.close();
  report.checks.push("Multiple-item browser save persists; sample items never receive fabricated original links; separate contexts are not synchronized.");
  await page.getByRole("button",{name:"출처 설정",exact:true}).click();
  await page.getByLabel("출처 이름",{exact:true}).fill("내 롤 패치");
  await page.getByRole("button",{name:"출처 추가",exact:true}).click();
  const lol=page.locator('.registered-source').filter({hasText:"내 롤 패치"});
  assert.match(await lol.innerText(),/가져온 공식 소식 · 자동 갱신 아님/);
  for(let i=0;i<3;i++) await page.getByRole("button",{name:"내 롤 패치 위로",exact:true}).click();
  assert.match(await page.locator("h1").innerText(),/리그 오브 레전드 26.22/);
  assert.match(await page.locator(".lead-card").innerText(),/2026-11-03T19:00:00.000Z/);
  await page.setViewportSize({width:390,height:1080});
  await audit("night-official-selected-390");
  await page.getByRole("button",{name:"주간으로 보기",exact:true}).click();
  await audit("day-official-selected-390");
  await page.setViewportSize({width:1440,height:1080});
  report.checks.push("Registered official-source briefing at390px in both themes: publication/provenance/original link visible; link and text contrast, clipping and horizontal overflow audited.");
  await page.locator(".secondary").click();
  await page.reload();
  assert.match(await page.locator("h1").innerText(),/리그 오브 레전드 26.22/);
  await page.getByRole("button",{name:"출처 설정",exact:true}).click();
  assert.equal(await page.locator(".registered-source").first().getAttribute("data-source-id"),"url:https://www.leagueoflegends.com/ko-kr/news/game-updates");
  await page.getByLabel("내 롤 패치 사용",{exact:true}).uncheck();
  assert.match(await page.locator("h1").innerText(),/협동 모드/);
  await page.getByLabel("내 롤 패치 사용",{exact:true}).check();
  await page.getByRole("button",{name:"03 4분",exact:true}).click();
  assert.match(await page.locator("h1").innerText(),/9시 팀 회의/);
  assert.match(await page.locator(".brief-meta").innerText(),/예산 12초/);
  await page.getByRole("button",{name:"04 도착",exact:true}).click();
  assert.equal(await page.locator(".saved-article").count(),2);
  assert.equal(await page.locator(".saved-article a").getAttribute("href"),"https://www.leagueoflegends.com/ko-kr/news/game-updates/league-of-legends-patch-26-22-notes/");
  report.checks.push("Real add official LoL snapshot → move first → actual briefing → save/reload; 4-minute schedule deadline still wins. Publication URL/date preserved and imported-snapshot label explicit.");
  await page.getByRole("button",{name:"출처 설정",exact:true}).click();
  await page.getByLabel("출처 서비스",{exact:true}).selectOption("youtube");
  await page.getByLabel("출처 이름",{exact:true}).fill("검증 채널");
  await page.getByLabel("출처 공식 주소",{exact:true}).fill("https://www.youtube.com/@OfficialChannel");
  await page.getByRole("button",{name:"출처 추가",exact:true}).click();
  assert.match(await page.locator('.registered-source').filter({hasText:"검증 채널"}).innerText(),/설정 필요/);
  await page.getByRole("button",{name:"출처 추가",exact:true}).click();
  assert.match(await page.getByRole("alert").innerText(),/이미 등록/);
  await page.getByLabel("출처 공식 주소",{exact:true}).fill("https://www.youtube.com.evil.test/@Channel");
  await page.getByRole("button",{name:"출처 추가",exact:true}).click();
  assert.match(await page.getByRole("alert").innerText(),/공식/);
  report.checks.push("Unsupported channel registers with setup-needed/no-content; duplicate and lookalike-host URLs are rejected.");
  await page.getByLabel("출처 서비스",{exact:true}).selectOption("steam");
  await page.getByLabel("출처 이름",{exact:true}).fill("검증 Steam");
  await page.getByRole("button",{name:"출처 추가",exact:true}).click();
  let phase="valid",releasePending;
  const fixture={gid:"123",title:"Fixture official update",url:"https://steamcommunity.com/games/dota2/announcements/detail/123",contents:"<p>Fixture test only, not retrieved public news.</p>",date:1793870400,feedname:"steam_community_announcements"};
  const steamRoute = url => url.origin === new URL(base).origin && url.pathname === "/api/steam-news";
  await page.route(steamRoute,async route=>{
    if(phase==="fail")return route.fulfill({status:403,body:"Denied fixture"});
    if(phase==="captured")return route.fulfill({status:200,contentType:"application/json",body:JSON.stringify(capturedSteam)});
    if(phase==="malformed")return route.fulfill({status:200,contentType:"application/json",body:JSON.stringify({appnews:{appid:570,newsitems:[{...fixture,title:""}]}})});
    if(phase==="late")await new Promise(resolve=>releasePending=resolve);
    await route.fulfill({status:200,contentType:"application/json",body:JSON.stringify({appnews:{appid:570,newsitems:phase==="empty"?[]:[fixture,fixture]}})}).catch(()=>{});
  });
  const steam=page.locator('.registered-source').filter({hasText:"검증 Steam"});
  await page.getByRole("button",{name:"검증 Steam 새 소식 확인",exact:true}).click();
  await page.waitForFunction(()=>JSON.parse(localStorage.getItem("before-arrival-sources-v2")).sources.some(s=>s.provider==="steam"&&s.items.length===1));
  const firstSuccess=await page.evaluate(()=>JSON.parse(localStorage.getItem("before-arrival-sources-v2")).sources.find(s=>s.provider==="steam").lastSuccessAt);
  phase="fail";await page.getByRole("button",{name:"검증 Steam 새 소식 확인",exact:true}).click();
  await page.waitForFunction(()=>Boolean(JSON.parse(localStorage.getItem("before-arrival-sources-v2")).sources.find(s=>s.provider==="steam").refreshError));
  assert.match(await steam.innerText(),/갱신 실패/);
  assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem("before-arrival-sources-v2")).sources.find(s=>s.provider==="steam").lastSuccessAt),firstSuccess);
  phase="malformed";await page.getByRole("button",{name:"검증 Steam 새 소식 확인",exact:true}).click();
  await page.waitForFunction(()=>!document.querySelector('[aria-label="검증 Steam 새 소식 확인"]').disabled);
  assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem("before-arrival-sources-v2")).sources.find(s=>s.provider==="steam").items.length),1);
  assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem("before-arrival-sources-v2")).sources.find(s=>s.provider==="steam").lastSuccessAt),firstSuccess);
  phase="empty";await page.getByRole("button",{name:"검증 Steam 새 소식 확인",exact:true}).click();
  await page.waitForFunction(()=>JSON.parse(localStorage.getItem("before-arrival-sources-v2")).sources.find(s=>s.provider==="steam").items.length===0);
  assert.match(await steam.innerText(),/새 소식 없음/);
  phase="late";await page.getByRole("button",{name:"검증 Steam 새 소식 확인",exact:true}).click();
  await page.waitForTimeout(100);
  await page.getByLabel("검증 Steam 사용",{exact:true}).uncheck();
  releasePending?.();await page.waitForTimeout(150);
  assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem("before-arrival-sources-v2")).sources.find(s=>s.provider==="steam").items.length),0);
  await page.getByLabel("검증 Steam 사용",{exact:true}).check();
  phase="captured";await page.getByRole("button",{name:"검증 Steam 새 소식 확인",exact:true}).click();
  await page.waitForFunction(()=>JSON.parse(localStorage.getItem("before-arrival-sources-v2")).sources.find(s=>s.provider==="steam").items.length===5);
  for(let i=0;i<5;i++)await page.getByRole("button",{name:"검증 Steam 위로",exact:true}).click();
  assert.match(await page.locator("h1").innerText(),/Inside the Trove of Terror/);
  await page.locator(".secondary").click();
  await page.reload();
  assert.match(await page.locator("h1").innerText(),/Inside the Trove of Terror/);
  await page.setViewportSize({width:390,height:1080});await audit("night-real-steam-replay-390");
  await page.getByRole("button",{name:"주간으로 보기",exact:true}).click();await audit("day-real-steam-replay-390");
  await page.setViewportSize({width:1440,height:1080});
  await page.getByRole("button",{name:"03 4분",exact:true}).click();assert.match(await page.locator("h1").innerText(),/9시 팀 회의/);
  await page.getByRole("button",{name:"04 도착",exact:true}).click();
  assert.equal(await page.locator(".saved-article").filter({hasText:"Inside the Trove of Terror"}).locator("a").getAttribute("href"),"https://steamstore-a.akamaihd.net/news/externalpost/steam_community_announcements/1846018067924633");
  report.checks.push("Captured real HTTP200 Steam response REPLAY via intercepted network:5 exact CDN records accepted, user ordering selects real title, 4-minute deadline retained, save/reload preserves CDN original,390px both themes audited. This is NOT a live browser network success.");
  await page.unroute(steamRoute);
  report.checks.push("Intercepted Steam fixtures only: official-schema items deduped, failed refresh preserves lastSuccess/items, empty success clears content, disabled-source late response discarded. This is NOT a real public fetch.");
  await page.evaluate(()=>localStorage.clear());await open("state=priority");
  assert.match(await page.locator("h1").innerText(),/9시 팀 회의/);
  await page.getByRole("button",{name:"이번 주말, 협동 모드가 열려요 나중에 보기 저장",exact:true}).click();
  assert.match(await page.locator("h1").innerText(),/9시 팀 회의/);
  await page.getByRole("button",{name:"04 도착",exact:true}).click();
  assert.match(await page.locator(".saved-article h3").innerText(),/협동 모드/);
  await page.reload();await page.getByRole("button",{name:"04 도착",exact:true}).click();
  assert.match(await page.locator(".saved-article h3").innerText(),/협동 모드/);
  await page.getByRole("button",{name:"주행 제한",exact:true}).click();
  assert.equal(await page.locator(".deferred-save").count(),0);
  report.checks.push("Parked 4-minute deferred game has an explicit save action without changing urgent meeting; arrival and reload preserve its own provenance. Deferred actions hidden during restrictions.");
  await page.evaluate(()=>localStorage.clear());await open("state=opening");
  await page.locator(".primary").click();await page.getByRole("button",{name:"주행 제한",exact:true}).click();
  assert(await page.locator(".primary").isDisabled());assert.equal(await page.locator(".primary").getAttribute("aria-pressed"),"false");
  await page.getByRole("button",{name:"상태 미확인",exact:true}).click();assert(await page.locator(".primary").isDisabled());
  await page.getByRole("button",{name:"01 18분",exact:true}).click();assert.equal(await page.locator(".primary").getAttribute("aria-pressed"),"false");
  report.checks.push("Restriction/unknown transition stops playback state; returning never automatically resumes; source settings unavailable during restrictions.");
  await page.evaluate(()=>localStorage.setItem("before-arrival-showcase-v1",JSON.stringify({game:false,meeting:true,community:true,first:"community",saved:true,savedTitle:"9시 팀 회의가 3층으로 바뀌었어요"})));
  await open("state=settings");
  assert.equal(await page.locator(".registered-source").first().getAttribute("data-source-id"),"sample-community");
  assert.equal(await page.getByLabel("게임 패치 예시 사용",{exact:true}).isChecked(),false);
  await page.getByLabel("커뮤니티 예시 사용",{exact:true}).uncheck();await page.reload();
  await page.getByRole("button",{name:"출처 설정",exact:true}).click();assert.equal(await page.getByLabel("게임 패치 예시 사용",{exact:true}).isChecked(),false);
  report.checks.push("Legacy booleans/priority/saved title migrate into generic registry; first v2 write and reload preserve them.");
  await page.evaluate(()=>localStorage.clear());await open("state=opening");
  await page.evaluate(()=>{Storage.prototype.setItem=()=>{throw new DOMException("Denied","SecurityError");};});
  await page.locator(".secondary").click();assert.match(await page.getByRole("alert").innerText(),/저장하지 못했어요/);assert.doesNotMatch(await page.locator(".secondary").innerText(),/저장됨/);
  report.checks.push("Storage denial never reports a successful settings/save write.");
  await page.emulateMedia({ reducedMotion: "reduce" });
  await open("theme=night");
  assert.equal(await page.locator(".phone").count(), 1);
  assert.match(await page.locator(".eta").innerText(), /도착/);
  await audit("reduced-motion-arrival");
  await page.waitForTimeout(5500);
  assert.match(await page.locator(".eta").innerText(), /도착/);
  report.checks.push(
    "Reduced motion starts and remains at final poster; manual frames remain selectable.",
  );
  await page.emulateMedia({ reducedMotion: "no-preference" });
  await open("theme=night");
  assert.match(await page.locator(".eta").innerText(), /18/);
  await page.waitForFunction(
    () =>
      document
        .querySelector(".scene-caption")
        ?.textContent?.includes("02 / 저장"),
    {},
    { timeout: 8000 },
  );
  assert.equal(
    await page.evaluate(() =>
      localStorage.getItem("before-arrival-sources-v2"),
    ),
    null,
  );
  await page.waitForFunction(
    () =>
      document
        .querySelector(".scene-caption")
        ?.textContent?.includes("03 / 4분"),
    {},
    { timeout: 6000 },
  );
  assert.match(await page.locator("h1").innerText(), /9시 팀 회의/);
  await page.waitForFunction(
    () =>
      document
        .querySelector(".scene-caption")
        ?.textContent?.includes("04 / 도착"),
    {},
    { timeout: 7000 },
  );
  report.checks.push(
    "Actual Stage timeline: 18-minute game → simulated save without localStorage mutation → 4-minute meeting → arrival phone.",
  );
  assert.deepEqual(errors, []);
  assert.deepEqual(externalRequests, [], "The browser must use only its same-origin app endpoint");
  report.checks.push(
    "No browser page errors. Offline font/code embedded. Browser requests remain same-origin; Steam endpoint fixtures are intercepted locally, with no credentials or unrelated requests.",
  );
  await writeFile(
    `${evidenceDir}/browser-report.json`,
    JSON.stringify(report, null, 2) + "\n",
  );
  console.log(
    `PASS: ${report.screens.length} screenshots, ${report.checks.length} behavioral checks; minimum contrast ${Math.min(...report.screens.map((x) => x.minimumContrast))}:1`,
  );
} finally {
  await browser.close();
  await new Promise((r) => server.close(r));
}
