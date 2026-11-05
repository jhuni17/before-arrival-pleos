import { useState } from 'react'
import { Stage } from '../../kit/stage'
import { Phone } from '../../kit/phone'
import { Count } from '../../kit/motion/Count'
import type { Act } from '../../kit/act'
import './showcase.css'

type Scene = { minutes: number; urgent: boolean; sent: boolean; received: boolean }
const opening: Scene = { minutes: 18, urgent: false, sent: false, received: false }
const priority: Scene = { ...opening, minutes: 4, urgent: true }
const ending: Scene = { ...priority, sent: true, received: true }

export async function sequence(act: Act<Scene>) {
  act.mark('overview')
  await act.wait(2700)
  await act.move('save', { duration: 580 })
  await act.click('save')
  await act.set({ sent: true })
  await act.wait(460)
  await act.set({ received: true })
  act.mark('phone-received')
  await act.wait(2100)
  act.mark('eta-update')
  await act.set({ minutes: 4 })
  await act.wait(420)
  await act.set({ urgent: true })
  act.mark('meeting-first')
  await act.wait(4000)
}

function RouteMap({ urgent }: { urgent: boolean }) {
  return <svg className="ba-map" viewBox="0 0 220 146" aria-hidden="true">
    <path className="ba-water" d="M0 111C42 119 76 98 102 68S166 15 220 20"/>
    <g className="ba-streets">
      <path d="M-10 20H75L109 52H233M-12 53H64L98 87H227M0 91H41L68 118H227M0 126H22L46 151"/>
      <path d="M22-6V153M50-6V42L84 77V152M82-6V20L120 57V146M146-6V149M174-6V149M202-6V149"/>
      <path d="M-6 145 141-7M76 146 218-5"/>
    </g>
    <path className="ba-route-base" d="M35 110H74Q85 110 85 98V83Q85 72 96 72H136Q148 72 148 60V41Q148 30 159 30H184"/>
    <path className="ba-route-run" data-urgent={urgent} pathLength="1" d="M35 110H74Q85 110 85 98V83Q85 72 96 72H136Q148 72 148 60V41Q148 30 159 30H184"/>
    <circle cx="184" cy="30" r="8" className="ba-destination-ring"/>
    <circle cx="184" cy="30" r="3" className="ba-destination"/>
  </svg>
}

function Vehicle({ minutes, urgent, sent }: Scene) {
  return <section className="ba-vehicle" aria-label="차량 브리핑" data-urgent={urgent} data-sent={sent}>
    <header className="ba-vehicle-bar">
      <div className="ba-vehicle-name">도착 전에</div>
      <div className="ba-parked"><b>P</b><span>주차 중</span></div>
    </header>
    <div className="ba-vehicle-content">
      <section className="ba-route" aria-label="남은 이동 시간">
        <div className="ba-kicker">목적지 · 회사</div>
        <div className="ba-eta"><Count value={minutes} duration={650}/><span>분</span></div>
        <div className="ba-route-label">도착까지</div>
        <RouteMap urgent={urgent}/>
      </section>
      <section className="ba-brief" aria-label="도착 전 브리핑">
        <div className="ba-brief-top">
          <div className="ba-kicker">도착 전 브리핑</div>
          <div className="ba-count">{urgent ? '우선 1건' : '오늘 3건'}</div>
        </div>
        <div className="ba-lead">
          <div className="ba-meeting ba-game" aria-hidden={urgent}>
            <div className="ba-meeting-tag">게임 패치 · 내가 정한 우선순위</div>
            <div className="ba-meeting-title">이번 주말, 협동 모드가 열려요</div>
            <span className="ba-save" data-target="save" data-cursor="pointer">
              <span aria-hidden="true">{sent ? '✓' : '↗'}</span>
              <span>{sent ? '휴대폰에 저장됨' : '휴대폰에 저장'}</span>
            </span>
          </div>
          <div className="ba-meeting ba-schedule" aria-hidden={!urgent}>
            <div className="ba-meeting-tag">일정 변경</div>
            <div className="ba-meeting-title">9시 팀 회의 3층 라운지로 변경</div>
            <div className="ba-urgent-note">도착 직후 확인할 일정</div>
          </div>
        </div>
        <div className="ba-secondary" aria-hidden={urgent}>
          <div>
            <div className="ba-secondary-row"><span>일정 변경</span><b>9시 회의 · 3층 라운지</b></div>
            <div className="ba-secondary-row"><span>커뮤니티</span><b>출근길 팟캐스트 추천</b></div>
          </div>
        </div>
        <div className="ba-deferred" aria-hidden={!urgent}><span>게임 패치 · 커뮤니티</span><b>나중에 보기</b></div>
      </section>
    </div>
  </section>
}

function Pocket({ received, compact }: { received: boolean; compact: boolean }) {
  return <Phone className="ba-phone" width={compact ? 236 : 270} frame="pro" wallpaper="#f0f2e9" time="8:56" aria-label="휴대폰 보관함">
    <div className="ba-pocket" data-received={received}>
      <div className="ba-pocket-kicker">도착 전에</div>
      <div className="ba-pocket-title">도착해서 보기</div>
      <div className="ba-pocket-list">
        <div className="ba-pocket-empty" aria-hidden={received}>차량에서 저장한 소식이<br/>여기에 모여요.</div>
        <div className="ba-pocket-item" aria-hidden={!received}>
          <span>✓&nbsp; 차량에서 저장됨</span>
          <b>주말 협동 모드 오픈</b>
          <div>즐겨찾는 게임 · 패치 노트</div>
        </div>
      </div>
    </div>
  </Phone>
}

export function Showcase({ state }: { state: string | null }) {
  const still = state === 'opening' ? opening : state === 'priority' ? priority : state === 'saved' ? ending : undefined
  const [replay, setReplay] = useState(0)
  return <main className="ba-page">
    <header className="ba-masthead">
      <span>PLEOS APP CHALLENGE</span>
      <span>콘셉트 데모</span>
    </header>
    <section className="ba-intro" aria-labelledby="title">
      <h1 id="title">도착 전에<span>.</span></h1>
      <p>남은 시간에 맞춰,<br/>먼저 챙길 소식만.</p>
    </section>
    <div id="example">
      <Stage key={replay} width={1080} height={417} compact={{ below: 810, width: 358, height: 680 }}
        className="ba-scene" theme="dark" accent="#d5ed9a" play="once"
        initial={still || opening} poster={still || ending} script={still ? undefined : sequence}
        cursor={still ? undefined : { at: { wide: { x: 735, y: 160 }, compact: { x: 319, y: 167 } }, style: 'arrow', ripple: true, idle: 1100 }}>
        {(scene, _, layout) => <>
          <Vehicle {...scene}/>
          <Pocket received={scene.received} compact={layout === 'compact'}/>
        </>}
      </Stage>
    </div>
    <footer className="ba-foot">
      <span>주차 상태와 기기 간 저장을 연출한 시연 · 실제 동기화 미구현</span>
      {!still && <button type="button" onClick={() => setReplay(current => current + 1)}><span aria-hidden="true">↻</span> 다시 보기</button>}
    </footer>
  </main>
}

