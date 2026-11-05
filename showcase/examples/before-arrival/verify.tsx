import assert from 'node:assert/strict'
import { renderToStaticMarkup } from 'react-dom/server'
import { Showcase, sequence } from './index'

const render = (state: string) => renderToStaticMarkup(<Showcase state={state}/>)
const opening = render('opening')
assert.match(opening, /aria-label="18"/)
assert.match(opening, /오늘 3건/)
assert.match(opening, /class="ba-meeting ba-game" aria-hidden="false"/)
assert.match(opening, /data-received="false"/)
const priority = render('priority')
assert.match(priority, /aria-label="4"/)
assert.match(priority, /우선 1건/)
assert.match(priority, /class="ba-meeting ba-schedule" aria-hidden="false"/)
assert.match(priority, /class="ba-secondary" aria-hidden="true"/)
assert.match(priority, /class="ba-deferred" aria-hidden="false"/)
const saved = render('saved')
assert.match(saved, /휴대폰에 저장됨/)
assert.match(saved, /data-received="true"/)
assert.match(saved, /주말 협동 모드 오픈/)
assert.match(saved, /실제 동기화 미구현/)

type StageAct = Parameters<typeof sequence>[0]
const trace: unknown[] = []
const record = (name: string, ...args: unknown[]) => { trace.push([name, ...args]) }
const act = {
  mark: (name: string) => record('mark', name),
  wait: async (duration: number) => record('wait', duration),
  set: async (state: object) => record('set', state),
  move: async (target: string) => record('move', target),
  click: async (target: string) => record('click', target),
} as unknown as StageAct
await sequence(act)
assert.deepEqual(trace.filter(item => (item as string[])[0] !== 'wait'), [
  ['mark', 'overview'],
  ['move', 'save'],
  ['click', 'save'],
  ['set', { sent: true }],
  ['set', { received: true }],
  ['mark', 'phone-received'],
  ['mark', 'eta-update'],
  ['set', { minutes: 4 }],
  ['set', { urgent: true }],
  ['mark', 'meeting-first'],
])
console.log('Rendered all three frames and verified the sequence state order.')
