import { useEffect, useState } from "react";
import { Stage } from "../../kit/stage";
import type { Act } from "../../kit/act";
import "./showcase.css";
import { items, planSample, type Source } from "./planner";
export type Frame =
  | "opening"
  | "saved"
  | "priority"
  | "arrived"
  | "settings"
  | "restricted"
  | "unknown";
export type Scene = { frame: Frame };
export const frames: Frame[] = [
  "opening",
  "saved",
  "priority",
  "arrived",
  "settings",
  "restricted",
  "unknown",
];
export async function sequence(act: Act<Scene>) {
  act.mark("18-minutes-game");
  await act.wait(5000);
  await act.set({ frame: "saved" });
  act.mark("save-simulation");
  await act.wait(3500);
  await act.set({ frame: "priority" });
  act.mark("4-minutes-meeting");
  await act.wait(5000);
  await act.set({ frame: "arrived" });
  act.mark("arrival-review");
  await act.wait(4500);
}
const gameTitle = "이번 주말, 협동 모드가 열려요";
const storageKey = "before-arrival-showcase-v1";
type LocalData = {
  saved: boolean;
  savedTitle: string;
  game: boolean;
  meeting: boolean;
  community: boolean;
  first: Source;
};
const defaults: LocalData = {
  saved: false,
  savedTitle: "",
  game: true,
  meeting: true,
  community: true,
  first: "game",
};
function readLocal(): LocalData {
  try {
    const d = JSON.parse(localStorage.getItem(storageKey) || "null");
    if (
      ["saved", "game", "meeting", "community"].every(
        (k) => typeof d?.[k] === "boolean",
      ) &&
      typeof d?.savedTitle === "string" &&
      ["game", "meeting", "community"].includes(d?.first)
    )
      return d;
  } catch {}
  return defaults;
}
function Glyph({ kind }: { kind: "play" | "save" | "check" | "pause" }) {
  return (
    <svg
      viewBox="0 0 24 24"
      width="24"
      height="24"
      fill="none"
      aria-hidden="true"
    >
      {kind === "play" ? (
        <path d="m9 5 11 7-11 7V5Z" fill="currentColor" />
      ) : kind === "check" ? (
        <path d="m4 12 5 5L20 6" stroke="currentColor" strokeWidth="2.5" />
      ) : kind === "pause" ? (
        <path d="M8 5v14M16 5v14" stroke="currentColor" strokeWidth="4" />
      ) : (
        <path
          d="M6 3h12v18l-6-4-6 4V3Z"
          stroke="currentColor"
          strokeWidth="2"
        />
      )}
    </svg>
  );
}
function Vehicle({
  frame,
  data,
  save,
  long,
}: {
  frame: Frame;
  data: LocalData;
  save: (title: string) => void;
  long: boolean;
}) {
  const blocked = frame === "restricted" || frame === "unknown",
    urgent = frame === "priority" || frame === "arrived";
  const arrived = frame === "arrived";
  const plan = planSample(arrived ? 0 : urgent ? 4 : 18, data);
  const lead = plan.selected[0];
  const meeting = lead === "meeting", community = lead === "community";
  const any = data.game || data.meeting || data.community;
  const title = meeting && long
    ? "오전 9시 팀 회의 장소가 본관 3층 라운지로 변경되었어요"
    : lead ? items[lead].title : "";
  const pending = (urgent ? plan.deferred : plan.selected.slice(1))
    .map((key) => items[key].label).join(" · ") || "없음";
  const [playing, setPlaying] = useState(false);
  useEffect(() => setPlaying(false), [frame, data]);
  useEffect(() => {
    if (!playing) return;
    const timer = setTimeout(() => setPlaying(false), 4000);
    return () => clearTimeout(timer);
  }, [playing]);
  return (
    <section className="vehicle" aria-label="차량 화면" data-blocked={blocked}>
      <div className="system-bar">
        <span>
          PLEOS CONNECT <small>시스템 UI 예시</small>
        </span>
        <span>
          {frame === "arrived" ? "09:00" : urgent ? "08:54" : "08:40"} ·{" "}
          {frame === "unknown" ? "?" : blocked ? "D" : "P"}
        </span>
      </div>
      <div className="application-window" aria-label="애플리케이션 영역">
        <header className="app-bar">
          <b>도착 전에</b>
          <span className="status">
            {frame === "unknown"
              ? "차량 상태 미확인 · 조작·음성 중지"
              : blocked
                ? "주행 제한 · 조작·음성 중지"
                : "주차 시 사용 가능 · 샘플 상태"}
          </span>
        </header>
        <div className="vehicle-content">
          <section className="eta-panel" aria-label="남은 시간">
            <span className="eyebrow">목적지 · 회사</span>
            <div className="eta">
              {frame === "arrived" ? (
                "도착"
              ) : (
                <>
                  <strong>{urgent ? 4 : 18}</strong>
                  <span>분</span>
                </>
              )}
            </div>
            <p>
              {frame === "arrived"
                ? "저장한 소식을 확인하세요"
                : "도착까지 남은 시간"}
            </p>
            <div className="route-track" aria-hidden="true">
              <i />
              <span />
              <i />
            </div>
            <span className="route-caption">경로·시간은 샘플</span>
          </section>
          <section className="brief" aria-label="이번 브리핑">
            <div className="brief-meta">
              <span className="eyebrow">
                {blocked
                  ? "이용 일시 중지"
                  : urgent
                    ? "도착 전에 먼저 확인"
                    : "이번에 들을 브리핑"}
              </span>
              <span>
                {blocked ? "안전 상태" : arrived ? "재생 종료" : `예상 ${plan.seconds}초 · 예산 ${plan.budget}초`}
              </span>
            </div>
            <div className="lead-card">
              <span className="category">
                {blocked
                  ? "주차 후 다시 이용"
                  : arrived
                    ? "도착 후 확인 · 연출"
                    : !lead
                    ? any ? "듣기 예산 부족" : "관심사 설정 필요"
                    : meeting
                      ? "일정 변경 · 샘플"
                      : community
                        ? "커뮤니티 · 샘플"
                        : "게임 업데이트 · 샘플"}
              </span>
              <h1>
                {blocked
                  ? frame === "unknown"
                    ? "차량 상태를 확인해 주세요"
                    : "안전하게 주차한 뒤 이용해 주세요"
                  : arrived
                    ? "저장한 소식을 확인하세요"
                    : !lead
                    ? any ? "이 소식은 도착해서 확인해 주세요" : "들을 출처를 선택해 주세요"
                    : title}
              </h1>
              <p>
                {blocked
                  ? "브리핑 화면 조작과 음성을 중지했어요."
                  : arrived
                    ? "휴대폰 화면은 저장 흐름을 보여주는 연출이에요."
                    : !lead
                    ? any ? "예상 낭독 길이가 남은 듣기 예산을 넘었어요." : "휴대폰 설정에서 들을 출처를 켜 주세요."
                    : meeting
                      ? "도착 직후 시작하는 회의라 먼저 알려드려요."
                      : community
                        ? "관심 있는 커뮤니티의 추천 소식을 모았어요."
                        : "새로운 협동 모드와 주말 업데이트를 짧게 정리했어요."}
              </p>
              <button
                className="primary"
                disabled={blocked || !lead}
                onClick={() => setPlaying(!playing)}
                aria-pressed={playing}
              >
                <Glyph kind={playing ? "pause" : "play"} />
                {playing ? "샘플 재생 중지" : "샘플 브리핑 재생"}
              </button>
              <button
                className="secondary"
                disabled={blocked || !lead}
                onClick={() => save(title)}
              >
                <Glyph
                  kind={
                    (data.saved && data.savedTitle === title) ||
                    frame === "saved"
                      ? "check"
                      : "save"
                  }
                />
                {data.saved && data.savedTitle === title
                  ? "이 브라우저에 저장됨"
                  : frame === "saved"
                    ? "저장 완료 · 연출"
                    : "도착해서 볼 항목에 저장"}
              </button>
              <span className="play-status" role="status">
                {playing
                  ? "재생 상태 연출 · 실제 음성 없음"
                  : frame === "saved"
                    ? "저장 완료 장면 · 기기 간 동기화 연출"
                    : "샘플 콘텐츠 · 실제 음성 없음"}
              </span>
            </div>
            <div className="next-up">
              {blocked
                ? "제한 상태에서는 목록 전환과 재생을 중지해요."
                : arrived ? "도착 후 보관함 · 기기 간 동기화 연출"
                : urgent
                  ? `나중에 확인 · ${pending}`
                  : `다음 소식 · ${pending}`}
            </div>
          </section>
        </div>
        <div className="app-boundary">
          애플리케이션 영역 끝 · 차량 연동이 없는 HTML 시연
        </div>
      </div>
      <div className="system-nav" aria-label="시스템 내비게이션 예시">
        <span>앱 라이브러리</span>
        <span>내비게이션</span>
        <span>공조 22°</span>
        <small>시스템 GNB 예시</small>
      </div>
    </section>
  );
}
function Pocket({
  frame,
  data,
  update,
}: {
  frame: Frame;
  data: LocalData;
  update: (d: LocalData) => void;
}) {
  return (
    <aside
      className="phone"
      aria-label={
        frame === "settings" ? "휴대폰 관심사 설정" : "휴대폰 도착 후 확인"
      }
    >
      <div className="phone-system">
        {frame === "settings" ? "8:30" : "9:00"} <span>휴대폰 예시</span>
      </div>
      <div className="phone-app">
        <span className="eyebrow">도착 전에</span>
        <h2>{frame === "settings" ? "내 출근길 관심사" : "도착해서 보기"}</h2>
        {frame === "settings" ? (
          <>
            <p>켜 둔 출처만 브리핑에 담아요.</p>
            {(
              [
                ["game", "게임 패치"],
                ["meeting", "일정 변경"],
                ["community", "커뮤니티"],
              ] as const
            ).map(([key, title]) => (
              <label className="source" key={key}>
                {title}
                <input
                  type="checkbox"
                  checked={data[key]}
                  onChange={(e) => update({ ...data, [key]: e.target.checked })}
                />
              </label>
            ))}
            <label className="priority-select">
              먼저 들을 소식
              <select
                value={data.first}
                onChange={(e) =>
                  update({
                    ...data,
                    first: e.target.value as LocalData["first"],
                  })
                }
              >
                <option value="game">게임 패치</option>
                <option value="meeting">일정 변경</option>
                <option value="community">커뮤니티</option>
              </select>
            </label>
            <p>기한이 가까운 일정은 먼저 안내해요.</p>
          </>
        ) : (
          <>
            <span className="category">
              {data.saved ? "이 브라우저에 실제 저장" : "저장 흐름 샘플"}
            </span>
            <h3>{data.saved ? data.savedTitle : gameTitle}</h3>
            <p>
              {data.saved
                ? "이 브라우저에서 저장한 항목"
                : "즐겨찾는 게임 · 패치 노트"}
            </p>
            {!data.saved || data.savedTitle === gameTitle ? (
              <>
                <a
                  href="https://store.steampowered.com/news/app/570"
                  target="_blank"
                  rel="noreferrer"
                >
                  공식 소식 허브 확인 ↗
                </a>
                <p className="sample-note">
                  이 제목은 가상 예시예요. 링크의 실제 소식과 일치하지 않아요.
                </p>
              </>
            ) : (
              <p className="sample-note">
                일정·커뮤니티는 원문 없는 샘플 항목이에요.
              </p>
            )}
          </>
        )}
        <div className="phone-scope">
          같은 브라우저 저장 · 기기 간 동기화 미구현
        </div>
      </div>
      <div className="phone-home" aria-hidden="true" />
    </aside>
  );
}
function SceneView({
  scene,
  data,
  update,
  long,
}: {
  scene: Scene;
  data: LocalData;
  update: (d: LocalData) => void;
  long: boolean;
}) {
  return (
    <>
      <div
        className="scene-composition"
        data-phone={scene.frame === "settings" || scene.frame === "arrived"}
      >
        <Vehicle
          frame={scene.frame}
          data={data}
          save={(title) => update({ ...data, saved: true, savedTitle: title })}
          long={long}
        />
        {(scene.frame === "settings" || scene.frame === "arrived") && (
          <Pocket frame={scene.frame} data={data} update={update} />
        )}
      </div>
      <div className="scene-caption" role="status">
        {scene.frame === "saved"
          ? "02 / 저장 · 자동 시연은 저장 완료 장면을 연출합니다."
          : scene.frame === "priority"
            ? "03 / 4분 · 기한이 가까운 회의 변경을 먼저 안내합니다."
            : scene.frame === "arrived"
              ? "04 / 도착 · 휴대폰 보관함 확인은 연출입니다."
              : scene.frame === "settings"
                ? "설정 / 관심사와 순서는 이 브라우저에 보존됩니다."
                : scene.frame === "restricted" || scene.frame === "unknown"
                  ? "안전 / 화면 조작과 음성을 중지합니다."
                  : "01 / 18분 · 선택한 출처와 순서대로 브리핑을 준비합니다."}
      </div>
    </>
  );
}
export function Showcase({ state }: { state: string | null }) {
  const params =
    typeof location === "undefined"
      ? new URLSearchParams()
      : new URLSearchParams(location.search);
  const [theme, setTheme] = useState(
    params.get("theme") === "day" ? "day" : "night",
  );
  const [manual, setManual] = useState<Frame | null>(
    frames.includes(state as Frame) ? (state as Frame) : null,
  );
  const [data, setData] = useState<LocalData>(defaults),
    [replay, setReplay] = useState(0),
    [error, setError] = useState("");
  useEffect(() => setData(readLocal()), []);
  function update(next: LocalData) {
    try {
      localStorage.setItem(storageKey, JSON.stringify(next));
      setData(next);
      setError("");
    } catch {
      setError("저장하지 못했어요. 브라우저 저장 공간을 확인해 주세요.");
    }
  }
  return (
    <main className="ba-page" data-theme={theme}>
      <div className="page-inner">
        <header className="masthead">
          <span>
            도착 전에 <small>PLEOS CONNECT · 콘셉트 시연</small>
          </span>
          <button onClick={() => setTheme(theme === "night" ? "day" : "night")}>
            {theme === "night" ? "주간으로 보기" : "야간으로 보기"}
          </button>
        </header>
        <div className="intro">
          <div>
            <span className="eyebrow">시간에 맞춘 나만의 출근길</span>
            <h2>
              남은 시간만큼,
              <br className="mobile-break" /> 필요한 소식부터.
            </h2>
          </div>
          <p>
            18분엔 게임 업데이트.
            <br />
            4분엔 곧 시작할 회의.
          </p>
        </div>
        <nav className="scene-controls" aria-label="시연 장면 선택">
          {(
            [
              ["opening", "01  18분"],
              ["saved", "02  저장"],
              ["priority", "03  4분"],
              ["arrived", "04  도착"],
              ["settings", "관심사 설정"],
              ["restricted", "주행 제한"],
              ["unknown", "상태 미확인"],
            ] as const
          ).map(([key, title]) => (
            <button
              key={key}
              aria-pressed={manual === key}
              onClick={() => setManual(key)}
            >
              {title}
            </button>
          ))}
          <button
            className="replay"
            onClick={() => {
              setManual(null);
              setReplay(replay + 1);
            }}
          >
            자동 시연 다시 보기
          </button>
        </nav>
        <Stage
          key={`${manual}-${replay}`}
          width={1440}
          height={680}
          className="ba-scene"
          theme={theme === "night" ? "dark" : "light"}
          play="once"
          initial={{ frame: manual || "opening" } as Scene}
          poster={{ frame: manual || "arrived" } as Scene}
          script={manual ? undefined : sequence}
        >
          {(scene) => (
            <SceneView
              scene={scene}
              data={data}
              update={update}
              long={params.has("long")}
            />
          )}
        </Stage>
        {error && <p role="alert">{error}</p>}
        <footer className="evidence-note">
          <span>
            샘플 경로·일정·게임 소식 · 저장 버튼은 같은 브라우저에 실제 보존 ·
            기기 간 동기화 연출
          </span>
          <span>Steam API 연결 실패 · 샘플 제공</span>
        </footer>
      </div>
    </main>
  );
}
