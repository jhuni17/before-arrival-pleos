// Matches android Briefing: sample clocks 08:40 / 08:54, deadline 08:58.
export type Source = "meeting" | "game" | "community";
export type Preferences = Record<Source, boolean> & { first: Source };
export const items = {
  meeting: { title: "9시 팀 회의가 3층으로 바뀌었어요", label: "일정 변경", seconds: 8 },
  game: { title: "이번 주말, 협동 모드가 열려요", label: "게임 패치", seconds: 11 },
  community: { title: "출근길에 듣기 좋은 10분짜리 팟캐스트", label: "커뮤니티", seconds: 13 },
} as const;
export function planSample(minutes: number, data: Preferences) {
  const budget = Math.min(45, Math.floor(Math.max(0, minutes * 60 - 180) / 5));
  const order: Source[] = ["meeting", "game", "community"];
  const candidates = order.filter((key) => data[key]);
  candidates.sort((a, b) => {
    const urgency = Number(b === "meeting" && minutes === 4) - Number(a === "meeting" && minutes === 4);
    return urgency || Number(b === data.first) - Number(a === data.first) || order.indexOf(a) - order.indexOf(b);
  });
  const selected: Source[] = [], deferred: Source[] = [];
  let seconds = 0;
  for (const key of candidates) {
    if (seconds + items[key].seconds <= budget) { selected.push(key); seconds += items[key].seconds; }
    else deferred.push(key);
  }
  return { selected, deferred, budget, seconds };
}
