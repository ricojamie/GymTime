import { useState } from 'react'
import { useLiveQuery } from 'dexie-react-hooks'
import { Bell, Check, Minus, Play, Plus, RotateCcw, SkipForward, Timer, Vibrate } from 'lucide-react'
import { db, defaultPreferences } from '../../db/database'
import { updatePreferences } from '../../db/repositories'
import { adjustRestTimer, formatRestTime, startRestTimer, stopRestTimer, useRestTimerRemaining } from '../../store/restTimer'
import { BottomSheet } from '../primitives'
import './timer.css'

export default function RestTimerBar({ defaultSeconds }: { defaultSeconds: number }) {
  const remaining = useRestTimerRemaining()
  const [open, setOpen] = useState(false)
  const [override, setOverride] = useState<{ base: number; value: number } | null>(null)
  const [error, setError] = useState('')
  const preferences = useLiveQuery(() => db.preferences.get('default'), []) ?? defaultPreferences
  const preset = Math.max(0, defaultSeconds)
  const idleSeconds = override?.base === preset ? override.value : preset
  const running = remaining > 0
  const shown = running ? remaining : idleSeconds
  const time = formatRestTime(shown)

  function adjust(by: number) {
    if (running) adjustRestTimer(by)
    else setOverride({ base: preset, value: Math.max(0, idleSeconds + by) })
  }

  function reset() {
    setOverride(null)
    if (running) startRestTimer(preset)
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
    if (running) stopRestTimer()
    else startRestTimer(idleSeconds)
  }

  return <>
    <div className="rest-strip" data-urgent={running && remaining <= 3 || undefined}>
      <button type="button" className="rest-readout" aria-label={'Rest timer settings, ' + time} onClick={() => setOpen(true)}>
        <Timer size={19} aria-hidden="true" />
        <span><span className="rest-label">Rest timer</span><strong>{time}</strong></span>
      </button>
      <button type="button" className="rest-nudge" onClick={() => adjust(30)} aria-label="Add 30 seconds">+30s</button>
      <button type="button" className="rest-action" disabled={!running && !idleSeconds} onClick={startOrSkip}>
        {running ? <SkipForward size={15} /> : <Play size={15} />} {running ? 'Skip' : 'Start'}
      </button>
    </div>
    {open && <BottomSheet title="Rest timer" onClose={() => setOpen(false)}>
      <div className="rest-sheet" data-urgent={running && remaining <= 3 || undefined}>
        <div className="rest-sheet-time">
          <button type="button" className="btn" disabled={shown <= 0} onClick={() => adjust(-5)} aria-label="Subtract 5 seconds"><Minus size={15} />5s</button>
          <strong role="timer">{time}</strong>
          <button type="button" className="btn" onClick={() => adjust(5)} aria-label="Add 5 seconds"><Plus size={15} />5s</button>
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
