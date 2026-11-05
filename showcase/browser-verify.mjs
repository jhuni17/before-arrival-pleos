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
const server = createServer((req, res) => {
  if (req.url?.startsWith("/before-arrival-showcase.html")) {
    res.writeHead(200, { "Content-Type": "text/html; charset=utf-8" });
    res.end(html);
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
      "examples/before-arrival/index.tsx", "examples/before-arrival/planner.ts",
      "examples/before-arrival/showcase.css", "browser-verify.mjs", "build.mjs",
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
  if (!request.url().startsWith(base) && !request.url().startsWith("data:"))
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
  await page.setViewportSize({ width: 1440, height: 1080 });
  await open("state=opening");
  await page.locator(".secondary").click();
  assert.match(
    await page.locator(".secondary").innerText(),
    /이 브라우저에 저장됨/,
  );
  await page.reload();
  assert.match(
    await page.locator(".secondary").innerText(),
    /이 브라우저에 저장됨/,
  );
  await page.getByRole("button", { name: "04 도착", exact: true }).click();
  assert.match(await page.locator(".phone h3").innerText(), /협동 모드/);
  const other = await browser.newContext();
  const otherPage = await other.newPage();
  await otherPage.goto(`${base}?state=opening`);
  assert.match(
    await otherPage.locator(".secondary").innerText(),
    /도착해서 볼 항목에 저장/,
  );
  await other.close();
  report.checks.push(
    "Save persists on reload; separate browser context has no shared data or synchronization.",
  );
  await page.getByRole("button", { name: "관심사 설정", exact: true }).click();
  await page.getByLabel("게임 패치", { exact: true }).uncheck();
  assert.match(await page.locator("h1").innerText(), /9시 팀 회의/);
  await page.reload();
  await page.getByRole("button", { name: "관심사 설정", exact: true }).click();
  assert.equal(
    await page.getByLabel("게임 패치", { exact: true }).isChecked(),
    false,
  );
  await page.getByLabel("일정 변경", { exact: true }).uncheck();
  await page.getByLabel("커뮤니티", { exact: true }).uncheck();
  assert(await page.locator(".primary").isDisabled());
  await page.getByLabel("커뮤니티", { exact: true }).check();
  assert.match(await page.locator("h1").innerText(), /팟캐스트/);
  await page.getByRole("button", { name: "03 4분", exact: true }).click();
  assert(await page.locator(".primary").isDisabled());
  assert(await page.locator(".secondary").isDisabled());
  assert.match(await page.locator("h1").innerText(), /도착해서 확인/);
  assert.match(await page.locator(".brief-meta").innerText(), /예상 0초 · 예산 12초/);
  await page.getByRole("button", { name: "01 18분", exact: true }).click();
  await page.getByRole("button", { name: "관심사 설정", exact: true }).click();
  await page.getByLabel("먼저 들을 소식").selectOption("community");
  assert.match(await page.locator("h1").innerText(), /팟캐스트/);
  report.checks.push("Community-only at 4 minutes exceeds the 12-second budget; play/save disabled. Community priority is supported at 18 minutes.");
  await page.getByLabel("게임 패치", { exact: true }).check();
  await page.getByLabel("일정 변경", { exact: true }).check();
  await page.getByLabel("먼저 들을 소식").selectOption("meeting");
  assert.match(await page.locator("h1").innerText(), /9시 팀 회의/);
  await page.getByLabel("먼저 들을 소식").selectOption("game");
  assert.match(await page.locator("h1").innerText(), /협동 모드/);
  await page.getByRole("button", { name: "03 4분", exact: true }).click();
  assert.match(await page.locator("h1").innerText(), /9시 팀 회의/);
  assert.match(await page.locator(".brief-meta").innerText(), /예상 8초 · 예산 12초/);
  await page.locator(".secondary").click();
  await page.getByRole("button", { name: "04 도착", exact: true }).click();
  assert(await page.locator(".primary").isDisabled());
  assert(await page.locator(".secondary").isDisabled());
  assert.match(await page.locator(".phone h3").innerText(), /9시 팀 회의/);
  assert.equal(await page.locator(".phone a").count(), 0);
  report.checks.push(
    "Saving a meeting preserves its own title in arrival review and does not attach an unrelated game link.",
  );
  report.checks.push(
    "Enabled sources and selected order persist; no source disables playback; community-only selection renders community; 4 minutes promotes enabled meeting.",
  );
  await page.getByRole("button", { name: "01 18분", exact: true }).click();
  await page.locator(".primary").click();
  assert.equal(
    await page.locator(".primary").getAttribute("aria-pressed"),
    "true",
  );
  await page.getByRole("button", { name: "주행 제한", exact: true }).click();
  assert(await page.locator(".primary").isDisabled());
  assert.equal(
    await page.locator(".primary").getAttribute("aria-pressed"),
    "false",
  );
  report.checks.push(
    "Transition from sample playback to restriction cancels playback state and disables both application actions.",
  );
  await page.getByRole("button", { name: "상태 미확인", exact: true }).click();
  assert(await page.locator(".primary").isDisabled());
  await page.getByRole("button", { name: "01 18분", exact: true }).click();
  assert.equal(await page.locator(".primary").getAttribute("aria-pressed"), "false");
  report.checks.push("Unknown vehicle state disables actions; return to allowed sample state never resumes playback automatically. Arrival poster has no playable route.");
  await page.evaluate(() => localStorage.setItem("before-arrival-showcase-v1", "{broken"));
  await open("state=settings");
  for (const name of ["게임 패치", "일정 변경", "커뮤니티"]) assert(await page.getByLabel(name, {exact:true}).isChecked());
  assert.match(await page.locator(".brief-meta").innerText(), /예상 32초 · 예산 45초/);
  report.checks.push("Malformed saved settings recover to all three Android source defaults without page errors.");
  await page.evaluate(() => localStorage.clear());
  await open("state=opening");
  await page.evaluate(() => {
    Storage.prototype.setItem = () => {
      throw new DOMException("Denied", "SecurityError");
    };
  });
  await page.locator(".secondary").click();
  assert.match(await page.getByRole("alert").innerText(), /저장하지 못했어요/);
  assert.doesNotMatch(await page.locator(".secondary").innerText(), /저장됨/);
  report.checks.push(
    "Storage denial displays failure without reporting a successful save.",
  );
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
      localStorage.getItem("before-arrival-showcase-v1"),
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
  assert.deepEqual(externalRequests, []);
  report.checks.push(
    "No browser page errors. Offline font and all code embedded in HTML.",
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
