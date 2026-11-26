import { useEffect, useId, useLayoutEffect, useMemo, useRef, useState } from 'react'
import { ArrowRight, Check, Delete, Minus, Plus } from 'lucide-react'
import { BottomSheet } from '../primitives'
import type { MetricField } from '../../domain/loggingValidation'
import type { WeightUnit } from '../../db/types'

export type PadField = MetricField | 'rpe'
export const fieldLabels: Record<PadField, string> = {
  weight: 'Weight', reps: 'Reps', rpe: 'RPE', durationSeconds: 'Time', distanceValue: 'Distance', calories: 'Calories',
}
export function clockValue(seconds: number) {
  return Math.floor(seconds / 60) + ':' + String(Math.floor(seconds % 60)).padStart(2, '0')
}
function readValue(field: PadField, raw: string): number | null {
  if (!raw || raw === '.' || raw.endsWith(':')) return null
  if (field === 'durationSeconds' && raw.includes(':')) {
    const [min, sec] = raw.split(':')
    return Number(sec) < 60 ? Number(min) * 60 + Number(sec) : null
  }
  const n = Number(raw)
  return Number.isFinite(n) ? n : null
}
function textValue(field: PadField, value: number | null) {
  if (value === null) return ''
  return field === 'durationSeconds' ? clockValue(value) : String(Number(value.toFixed(3)))
}

export type DialPin = { label: 'LAST' | 'GOAL' | 'BEST'; value: number }
type Props = {
  field: PadField; value: number | null; unit: string; weightUnit: WeightUnit; pins: DialPin[]
  onChange: (value: number | null) => void; onNext?: () => void; onCommit: () => void
  onClose: () => void; action: string; error: string; busy: boolean
}

function VernierDial({ value, step, unit, pins, onChange }: {
  value: number; step: number; unit: string; pins: DialPin[]; onChange: (value: number) => void
}) {
  const track = useRef<HTMLDivElement>(null)
  const interacting = useRef(false)
  const lastFromScroll = useRef<number | null>(null)
  const [start] = useState(value)
  const [dragging, setDragging] = useState(false)
  const [tickPulse, setTickPulse] = useState(0)
  const max = Math.max(unit === 'reps' ? 200 : 1000, start + 150 * step, ...pins.map(p => p.value + 20 * step))
  const count = Math.min(20000, Math.ceil(max / step))
  const spacing = 12
  const patternId = useId().replace(/:/g, '')
  useLayoutEffect(() => {
    const el = track.current
    if (!el || (lastFromScroll.current !== null && Math.abs(lastFromScroll.current - value) < 0.000001)) return
    interacting.current = false
    el.scrollLeft = Math.round(value / step) * spacing
  }, [value, step])
  useEffect(() => {
    const el = track.current
    if (!el) return
    let settle: ReturnType<typeof setTimeout> | undefined
    const endInteraction = () => { interacting.current = false; setDragging(false) }
    const snap = () => {
      clearTimeout(settle)
      settle = setTimeout(() => {
        interacting.current = false
        setDragging(false)
        const tick = Math.round(el.scrollLeft / spacing) * spacing
        if (Math.abs(el.scrollLeft - tick) >= .5) el.scrollLeft = tick
      }, 130)
    }
    el.addEventListener('scroll', snap, {passive:true})
    window.addEventListener('pointerup', endInteraction)
    window.addEventListener('pointercancel', endInteraction)
    return () => {
      el.removeEventListener('scroll', snap)
      window.removeEventListener('pointerup', endInteraction)
      window.removeEventListener('pointercancel', endInteraction)
      clearTimeout(settle)
    }
  }, [])
  const labels = useMemo(() => Array.from({ length: Math.floor(count / 10) + 1 }, (_, index) => {
    const tick = index * 10
    return <text key={tick} x={tick * spacing} y="70" textAnchor="middle">{tick * step}</text>
  }), [count, step])
  const read = () => {
    if (!interacting.current || !track.current) return
    const n = Math.max(0, Math.min(count, Math.round(track.current.scrollLeft / spacing))) * step
    if (n === lastFromScroll.current) return
    lastFromScroll.current = n
    setTickPulse(t => t + 1)
    onChange(n)
  }
  const pinGroups = new Map<number, string[]>()
  for (const p of pins) {
    if (!Number.isFinite(p.value) || p.value < 0 || p.value > count * step) continue
    const idx = Math.round(p.value / step)
    pinGroups.set(idx, [...(pinGroups.get(idx) ?? []), p.label])
  }
  return <div className={'log-dial' + (dragging ? ' is-dragging' : '')}>
    <div role="slider" tabIndex={0} aria-label={unit === 'reps' ? 'Repetition dial' : 'Weight dial'}
      aria-valuemin={0} aria-valuemax={count * step} aria-valuenow={value} aria-valuetext={value + ' ' + unit}
      className="log-dial-track" ref={track} onScroll={read}
      onPointerDown={() => { interacting.current = true; setDragging(true) }}
      onPointerUp={() => { interacting.current = false; setDragging(false) }}
      onPointerCancel={() => { interacting.current = false; setDragging(false) }}
      onWheel={() => { interacting.current = true; setDragging(true) }}
      onKeyDown={event => {
        let next = value
        if (event.key === 'ArrowRight' || event.key === 'ArrowUp') next += step
        else if (event.key === 'ArrowLeft' || event.key === 'ArrowDown') next -= step
        else if (event.key === 'Home') next = 0
        else if (event.key === 'End') next = count * step
        else return
        event.preventDefault()
        setTickPulse(t => t + 1)
        onChange(Math.max(0, Math.min(count * step, next)))
      }}>
      <div className="log-dial-bed" style={{ width: 'calc(100% + ' + (count * spacing) + 'px)' }}>
        <svg aria-hidden="true" className="log-dial-scale" style={{ left: 'calc((100% - ' + (count * spacing) + 'px) / 2)' }} height="82" width={count * spacing + 1}>
          <defs><pattern id={patternId} width={spacing * 10} height={82} patternUnits="userSpaceOnUse">
            {Array.from({ length: 10 }, (_, i) => (
              <path
                key={i}
                className={i === 0 ? 'log-dial-major' : i === 5 ? 'log-dial-mid' : 'log-dial-minor'}
                d={'M' + (i * spacing + .5) + ' 29v' + (i === 0 ? 25 : i === 5 ? 18 : 9)}
              />
            ))}
          </pattern></defs>
          <rect width="100%" height="82" fill={'url(#' + patternId + ')'} />
          {labels}
          {[...pinGroups.entries()].map(([i, names]) => <g key={i} className="log-dial-pin" transform={'translate(' + i * spacing + ',0)'}><path d="M0 24v-6" /><text y="13" textAnchor="middle">{names.join(' / ')}</text></g>)}
        </svg>
      </div>
    </div>
    <span key={tickPulse} aria-hidden="true" className={'log-dial-pointer' + (tickPulse > 0 ? ' is-tick' : '')} />
  </div>
}

export default function Keypad({ field, value, unit, weightUnit, pins, onChange, onNext, onCommit, onClose, action, error, busy }: Props) {
  const [raw, setRaw] = useState(() => textValue(field, value))
  const [fresh, setFresh] = useState(true)
  const [dial, setDial] = useState(false)
  const [tapped, setTapped] = useState<{ id: string; tick: number } | null>(null)
  const tapTick = useRef(0)
  const [valAnim, setValAnim] = useState<{ dir: 'up' | 'down' | 'edit'; tick: number }>({ dir: 'edit', tick: 0 })
  useEffect(() => {
    if (!tapped) return
    const t = window.setTimeout(() => setTapped(null), 220)
    return () => window.clearTimeout(t)
  }, [tapped])
  function markTap(id: string) {
    tapTick.current += 1
    setTapped({ id, tick: tapTick.current })
  }
  const canDial = field === 'weight' || field === 'reps'
  const punctuation = field === 'durationSeconds' ? ':' : '.'
  const allowFraction = field !== 'reps' && field !== 'durationSeconds'
  function write(key: string) {
    let next = fresh ? '' : raw
    if (key === 'back') next = fresh ? '' : next.slice(0, -1)
    else if (key === '.' || key === ':') {
      if (key !== punctuation || next.includes(punctuation) || (!allowFraction && key === '.')) return
      next = (next || '0') + punctuation
    } else {
      if (!/^\d$/.test(key) || next.length >= 9 || (next.includes(':') && next.split(':')[1].length >= 2)) return
      next = next === '0' ? key : next + key
    }
    markTap(key)
    setFresh(false)
    setRaw(next)
    setValAnim(a => ({ dir: key === 'back' ? 'down' : 'edit', tick: a.tick + 1 }))
    onChange(readValue(field, next))
  }
  function setExact(n: number, tapId?: string) {
    const bounded = Math.max(0, Math.min(field === 'rpe' ? 10 : 1e9, n))
    const prev = value ?? 0
    if (tapId) markTap(tapId)
    setRaw(textValue(field, bounded))
    setFresh(true)
    setValAnim(a => ({ dir: bounded > prev ? 'up' : bounded < prev ? 'down' : 'edit', tick: a.tick + 1 }))
    onChange(bounded)
  }
  const step = field === 'weight' ? weightUnit === 'kg' ? 2.5 : 5 : 1
  const stepUnit = field === 'durationSeconds' ? ' second' : field === 'weight' || field === 'distanceValue' ? ' ' + unit : ''
  const adjustment = fieldLabels[field].toLowerCase() + ' by ' + step + stepUnit
  const writeRef = useRef(write)
  writeRef.current = write
  useEffect(() => {
    function keydown(e: KeyboardEvent) {
      if (e.altKey || e.metaKey || e.ctrlKey || e.defaultPrevented) return
      if (/^[0-9.:]$/.test(e.key) || e.key === 'Backspace') {
        e.preventDefault()
        writeRef.current(e.key === 'Backspace' ? 'back' : e.key)
      }
    }
    window.addEventListener('keydown', keydown)
    return () => window.removeEventListener('keydown', keydown)
  }, [])
  return <BottomSheet title={fieldLabels[field]} onClose={onClose}>
    <div className="log-pad">
      <div className="log-pad-display">
        <output aria-label={fieldLabels[field] + ' value'}>
          <span key={valAnim.tick + ':' + (raw || 'empty')} className="log-pad-val" data-dir={valAnim.tick > 0 ? valAnim.dir : undefined}>
            {raw || '—'}
          </span>
        </output>
        <span>{unit}</span>
      </div>
      {canDial && <div className="log-mode" data-dial={dial || undefined} role="group" aria-label="Entry method">
        <button aria-pressed={!dial} onClick={() => setDial(false)}>Keypad</button><button aria-pressed={dial} onClick={() => setDial(true)}>Dial</button>
      </div>}
      <div key={dial && canDial ? 'dial' : 'keys'} className="log-pad-panel">
        {dial && canDial
          ? <VernierDial value={value ?? 0} unit={unit} step={field === 'weight' && weightUnit === 'kg' ? .5 : 1} pins={pins} onChange={n => setExact(n)} />
          : <div className="log-pad-grid">{['1','2','3','4','5','6','7','8','9',punctuation,'0','back'].map(k =>
            <button key={k} className={tapped?.id === k ? 'is-tapped' : undefined} data-key-action={k === 'back' ? 'back' : undefined} disabled={k === '.' && !allowFraction} aria-label={k === 'back' ? 'Backspace' : k} onClick={() => write(k)}><span aria-hidden="true">{k === 'back' ? <Delete size={20} /> : k}</span></button>)}</div>}
      </div>
      <div className="log-pad-nudges">
        <button className={'log-pad-step log-pad-step-dec' + (tapped?.id === 'dec' ? ' is-tapped' : '')} aria-label={'Decrease ' + adjustment} onClick={() => setExact((value ?? 0) - step, 'dec')}><Minus size={15} /><span>{step}</span></button>
        <button className={'log-pad-step log-pad-step-inc' + (tapped?.id === 'inc' ? ' is-tapped' : '')} aria-label={'Increase ' + adjustment} onClick={() => setExact((value ?? 0) + step, 'inc')}><Plus size={15} /><span>{step}</span></button>
        {onNext && <button className={'log-pad-next' + (tapped?.id === 'next' ? ' is-tapped' : '')} onClick={() => { markTap('next'); onNext() }}><span>Next</span> <ArrowRight size={17} /></button>}</div>
      {error && <p role="alert" className="log-error">{error}</p>}
      <button disabled={busy} className="btn primary log-commit" onClick={onCommit}><Check size={19} />{action}</button>
    </div>
  </BottomSheet>
}
