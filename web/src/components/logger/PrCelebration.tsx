import { useEffect, useMemo } from 'react'
import { Flame, Sparkles, Trophy, X } from 'lucide-react'
import { SwoleGator } from '../mascot'
import './pr-celebration.css'

export type PrCelebrationProps = {
  id: number
  labels: string[]
  exerciseName?: string
  isGator?: boolean
  audioEnabled?: boolean
  onDismiss: () => void
}

type Particle = {
  tx: string
  ty: string
  rot: string
  delay: string
  size: string
  kind: 'spark' | 'confetti' | 'ember'
  tone: 0 | 1 | 2 | 3
}

function buildParticles(seed: number): Particle[] {
  const count = 32
  const out: Particle[] = []
  for (let i = 0; i < count; i++) {
    // Deterministic pseudo-random spread so each PR id feels fresh without impure render math
    const h1 = Math.sin(seed * 12.9898 + i * 78.233) * 43758.5453
    const r1 = h1 - Math.floor(h1)
    const h2 = Math.sin(seed * 39.346 + i * 11.135) * 24634.6345
    const r2 = h2 - Math.floor(h2)
    const angle = ((i / count) * Math.PI * 2) + (r1 - 0.5) * 0.35
    const dist = 90 + r2 * 185
    const tx = Math.round(Math.cos(angle) * dist) + 'px'
    const ty = Math.round(Math.sin(angle) * dist - 20) + 'px'
    const rot = Math.round((r1 - 0.5) * 540) + 'deg'
    const delay = Math.round((i % 6) * 26) + 'ms'
    const size = (6 + Math.round(r2 * 7)) + 'px'
    const kind = i % 3 === 0 ? 'spark' : i % 3 === 1 ? 'confetti' : 'ember'
    const tone = (i % 4) as 0 | 1 | 2 | 3
    out.push({ tx, ty, rot, delay, size, kind, tone })
  }
  return out
}

function playVictoryChime() {
  try {
    const AudioCtx = window.AudioContext || (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext
    if (!AudioCtx) return
    const ctx = new AudioCtx()
    if (ctx.state === 'suspended') {
      void ctx.resume().catch(() => {})
    }
    const now = ctx.currentTime
    // Crisp ascending triad (C5 -> E5 -> G5 -> C6)
    const notes = [523.25, 659.25, 783.99, 1046.5]
    notes.forEach((freq, idx) => {
      const osc = ctx.createOscillator()
      const gain = ctx.createGain()
      osc.type = idx === notes.length - 1 ? 'triangle' : 'sine'
      osc.frequency.setValueAtTime(freq, now + idx * 0.065)
      gain.gain.setValueAtTime(0.0001, now + idx * 0.065)
      gain.gain.exponentialRampToValueAtTime(0.08, now + idx * 0.065 + 0.02)
      gain.gain.exponentialRampToValueAtTime(0.0001, now + idx * 0.065 + 0.32)
      osc.connect(gain)
      gain.connect(ctx.destination)
      osc.start(now + idx * 0.065)
      osc.stop(now + idx * 0.065 + 0.34)
    })
    window.setTimeout(() => {
      void ctx.close().catch(() => {})
    }, 900)
  } catch {
    // AudioContext is optional and may be blocked or absent in test environments.
  }
}

export default function PrCelebration({ id, labels, exerciseName, isGator = false, audioEnabled = false, onDismiss }: PrCelebrationProps) {
  const particles = useMemo(() => buildParticles(id), [id])

  useEffect(() => {
    if (audioEnabled) playVictoryChime()
  }, [id, audioEnabled])

  return (
    <div className={'pr-overlay' + (isGator ? ' is-gator' : '')} data-pr-celebration={id}>
      <div className="pr-burst-stage" aria-hidden="true">
        <span className="pr-shockwave pr-shockwave-1" />
        <span className="pr-shockwave pr-shockwave-2" />
        <div className="pr-particles">
          {particles.map((p, i) => (
            <i
              key={i}
              className={`pr-particle pr-particle-${p.kind} pr-tone-${p.tone}`}
              style={{
                '--tx': p.tx,
                '--ty': p.ty,
                '--rot': p.rot,
                '--delay': p.delay,
                '--size': p.size,
              } as React.CSSProperties}
            />
          ))}
        </div>
      </div>

      <div
        className="pr-card"
        role="status"
        aria-live="polite"
        onClick={onDismiss}
        title="Tap to dismiss"
      >
        <div className="pr-glare" aria-hidden="true" />
        <div className="pr-medallion-wrap" aria-hidden="true">
          <span className="pr-halo" />
          {isGator ? (
            <div className="pr-gator-mascot">
              <SwoleGator pose="pr" celebrate size={68} />
            </div>
          ) : (
            <span className="pr-medallion">
              <Trophy size={28} />
            </span>
          )}
        </div>

        <div className="pr-body">
          <div className="pr-kicker">
            <Sparkles size={13} aria-hidden="true" />
            <span>{isGator ? 'CHOMP! NEW PR' : 'PR UNLOCKED'}</span>
          </div>
          <strong className="pr-title">New personal record</strong>
          {exerciseName && <span className="pr-exercise">{exerciseName}</span>}
          {labels.length > 0 && (
            <div className="pr-chips">
              {labels.map((label, i) => (
                <span
                  key={label}
                  className="pr-chip"
                  style={{ '--chip-i': i } as React.CSSProperties}
                >
                  {label}
                </span>
              ))}
            </div>
          )}
        </div>

        <button
          type="button"
          className="pr-dismiss"
          aria-label="Dismiss personal record"
          onClick={e => { e.stopPropagation(); onDismiss() }}
        >
          <X size={16} aria-hidden="true" />
        </button>
      </div>
    </div>
  )
}
