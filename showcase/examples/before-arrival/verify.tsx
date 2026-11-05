import assert from "node:assert/strict";
import { renderToStaticMarkup } from "react-dom/server";
import { Showcase, frames, sequence } from "./index";
const render = (state: string) =>
  renderToStaticMarkup(<Showcase state={state} />);
const opening = render("opening");
assert.match(opening, /<strong>18<\/strong>/);
assert.match(opening, /이번 주말, 협동 모드가 열려요/);
assert.doesNotMatch(opening, /class="phone"/);
assert.match(render("saved"), /저장 완료 · 연출/);
assert.match(render("priority"), /<strong>4<\/strong>/);
assert.match(render("priority"), /9시 회의, 3층 라운지로 변경/);
assert.match(render("arrived"), /휴대폰 도착 후 확인/);
assert.match(render("settings"), /휴대폰 관심사 설정/);
for (const state of ["restricted", "unknown"]) {
  const html = render(state);
  assert.match(html, /조작·음성 중지/);
  assert.match(html, /class="primary" disabled=""/);
  assert.match(html, /class="secondary" disabled=""/);
}
for (const state of frames) assert.match(render(state), /기기 간 동기화 연출/);
const trace: unknown[] = [];
await sequence({
  mark: (n: string) => trace.push(["mark", n]),
  wait: async () => {},
  set: async (s: object) => trace.push(["set", s]),
} as unknown as Parameters<typeof sequence>[0]);
assert.deepEqual(trace, [
  ["mark", "18-minutes-game"],
  ["set", { frame: "saved" }],
  ["mark", "save-simulation"],
  ["set", { frame: "priority" }],
  ["mark", "4-minutes-meeting"],
  ["set", { frame: "arrived" }],
  ["mark", "arrival-review"],
]);
console.log(
  "PASS: seven frames, safety restrictions, phone scope and ordered story. Browser persistence/contrast/layout checked separately.",
);
