import { useEffect, useRef, useState, type CSSProperties } from 'react'
import { useLiveQuery } from 'dexie-react-hooks'
import { Bell, Check, Minus, Play, Plus, RotateCcw, SkipForward, Timer, Vibrate } from 'lucide-react'
import { db, defaultPreferences } from '../../db/database'
import { updatePreferences } from '../../db/repositories'
import { adjustRestTimer, formatRestTime, startRestTimer, stopRestTimer, useRestTimerRemaining } from '../../store/restTimer'
import { useIsGatorMode } from '../../theme'
import { SwoleGator } from '../mascot'
import { BottomSheet } from '../primitives'
import './timer.css'

export default function RestTimerBar({ defaultSeconds }: { defaultSeconds: number }) {
  const remaining = useRestTimerRemaining()
  const isGator = useIsGatorMode()
  const [open, setOpen] = useState(false)
  const [override, setOverride] = useState<{ base: number; value: number } | null>(null)
  const [error, setError] = useState('')
  const [total, setTotal] = useState(() => Math.max(1, defaultSeconds))
  const [completedFlash, setCompletedFlash] = useState(false)
  const [justStarted, setJustStarted] = useState(false)
  const [nudge, setNudge] = useState<{ dir: 'up' | 'down'; tick: number } | null>(null)
  const [lastNudge, setLastNudge] = useState<{ dir: 'up' | 'down'; count: number }>({ dir: 'up', count: 0 })
  const prevRemaining = useRef(remaining)
  const prevRunning = useRef(remaining > 0)
  const manualSkip = useRef(false)

  const preferences = useLiveQuery(() => db.preferences.get('default'), []) ?? defaultPreferences
  const preset = Math.max(0, defaultSeconds)
  const idleSeconds = override?.base === preset ? override.value : preset
  const running = remaining > 0
  const shown = running ? remaining : idleSeconds
  const time = formatRestTime(shown)

  useEffect(() => {
    if (remaining > total) setTotal(remaining)
  }, [remaining, total])

  useEffect(() => {
    if (!prevRunning.current && running) {
      setJustStarted(true)
    }
    prevRunning.current = running
  }, [running])

  useEffect(() => {
    if (!justStarted) return
    const t = window.setTimeout(() => setJustStarted(false), 440)
    return () => window.clearTimeout(t)
  }, [justStarted])

  useEffect(() => {
    if (prevRemaining.current > 0 && remaining === 0) {
      if (!manualSkip.current) {
        setCompletedFlash(true)
      }
      manualSkip.current = false
    }
    prevRemaining.current = remaining
  }, [remaining])

  useEffect(() => {
    if (!completedFlash) return
    const t = window.setTimeout(() => setCompletedFlash(false), 1400)
    return () => window.clearTimeout(t)
  }, [completedFlash])

  useEffect(() => {
    if (!nudge) return
    const t = window.setTimeout(() => setNudge(null), 340)
    return () => window.clearTimeout(t)
  }, [nudge])

  function adjust(by: number) {
    const dir: 'up' | 'down' = by > 0 ? 'up' : 'down'
    const nextCount = lastNudge.count + 1
    setLastNudge({ dir, count: nextCount })
    setNudge({ dir, tick: nextCount })
    if (running) {
      if (remaining + by > total) setTotal(remaining + by)
      adjustRestTimer(by)
    } else {
      setOverride({ base: preset, value: Math.max(0, idleSeconds + by) })
    }
  }

  function reset() {
    setOverride(null)
    if (running) {
      setTotal(Math.max(1, preset))
      startRestTimer(preset)
    }
  }

  async function toggle(key: 'timerAudioEnabled' | 'timerVibrateEnabled') {
    setError('')
    try {
      await updatePreferences({ [key]: !preferences[key] })
    } catch {
      setError('Could not save timer settings.')
    }
  }

  function startOrSkip() {
    if (running) {
      manualSkip.current = true
      stopRestTimer()
    } else {
      manualSkip.current = false
      setCompletedFlash(false)
      setTotal(Math.max(1, idleSeconds))
      startRestTimer(idleSeconds)
    }
  }

  const progress = running ? Math.min(1, Math.max(0, remaining / Math.max(1, total))) : 0
  const urgent = running && remaining <= 10

  return <>
    <div
      className={
        'rest-strip' +
        (running ? ' is-running' : '') +
        (justStarted ? ' is-starting' : '') +
        (completedFlash ? ' is-complete' : '')
      }
      data-urgent={urgent || undefined}
      data-nudge={nudge?.dir}
      style={{ '--rest-progress': progress } as CSSProperties}
    >
      <span className="rest-progress-track" aria-hidden="true">
        <span className="rest-progress" />
      </span>
      <button type="button" className="rest-readout" aria-label={'Rest timer settings, ' + time} onClick={() => setOpen(true)}>
        <span className="rest-icon" aria-hidden="true">
          {isGator
            ? <SwoleGator pose="spotter" size={26} celebrate={completedFlash} />
            : completedFlash
              ? <Check size={19} />
              : <Timer size={19} />}
        </span>
        <span>
          <span className="rest-label">{completedFlash ? 'Rest complete' : 'Rest timer'}</span>
          <strong key={lastNudge.count} className="rest-time-val" data-nudge-dir={lastNudge.count > 0 ? lastNudge.dir : undefined}>{time}</strong>
        </span>
      </button>
      <button type="button" className={'rest-nudge rest-nudge-down' + (nudge?.dir === 'down' ? ' is-pulsing' : '')} disabled={shown <= 0} onClick={() => adjust(-15)} aria-label="Subtract 15 seconds">-15s</button>
      <button type="button" className={'rest-nudge rest-nudge-up' + (nudge?.dir === 'up' ? ' is-pulsing' : '')} onClick={() => adjust(15)} aria-label="Add 15 seconds">+15s</button>
      <button type="button" className="rest-action" disabled={!running && !idleSeconds} onClick={startOrSkip}>
        {running ? <SkipForward size={15} /> : <Play size={15} />} {running ? 'Skip' : 'Start'}
      </button>
    </div>
    {open && <BottomSheet title="Rest timer" onClose={() => setOpen(false)}>
      <div className="rest-sheet" data-urgent={urgent || undefined}>
        <div className="rest-sheet-time">
          <button type="button" className={'btn' + (nudge?.dir === 'down' ? ' is-pulsing' : '')} disabled={shown <= 0} onClick={() => adjust(-5)} aria-label="Subtract 5 seconds"><Minus size={15} />5s</button>
          <strong key={lastNudge.count} role="timer" className="rest-time-val" data-nudge-dir={lastNudge.count > 0 ? lastNudge.dir : undefined}>{time}</strong>
          <button type="button" className={'btn' + (nudge?.dir === 'up' ? ' is-pulsing' : '')} onClick={() => adjust(5)} aria-label="Add 5 seconds"><Plus size={15} />5s</button>
        </div>
        <button className="rest-sheet-row" onClick={reset}><RotateCcw size={18} /><span>Reset to {formatRestTime(preset)}</span></button>
        <button className="rest-sheet-row" role="switch" aria-checked={preferences.timerAudioEnabled} onClick={() => void toggle('timerAudioEnabled')}>
          <Bell size={18} /><span>Timer sound</span><span className="rest-switch" aria-hidden="true">{preferences.timerAudioEnabled && <Check size={14} />}</span>
        </button>
        <button className="rest-sheet-row" role="switch" aria-checked={preferences.timerVibrateEnabled} onClick={() => void toggle('timerVibrateEnabled')}>
          <Vibrate size={18} /><span>Timer vibration</span><span className="rest-switch" aria-hidden="true">{preferences.timerVibrateEnabled && <Check size={14} />}</span>
        </button>
        {error && <p role="alert">{error}</p>}
        <button type="button" className="btn primary rest-sheet-cta" onClick={startOrSkip} disabled={!running && !idleSeconds}>
          {running ? <SkipForward size={17} /> : <Play size={17} />}{running ? 'Skip rest' : 'Start timer'}
        </button>
      </div>
    </BottomSheet>}
  </>
}
