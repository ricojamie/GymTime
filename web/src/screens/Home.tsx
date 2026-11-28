import { useCallback, useEffect, useRef, useState, type CSSProperties } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useLiveQuery } from 'dexie-react-hooks'
import { ArrowRight, CalendarDays, ChevronRight, ClipboardList, HardDriveDownload, Play, Settings, X, Zap } from 'lucide-react'
import { db, defaultPreferences } from '../db/database'
import { useSaveBackup } from '../db/backup'
import { startWorkout } from '../db/repositories'
import { ApexKineticStage } from '../components/home/ApexKineticStage'
import { TrainingReadouts } from '../components/home/TrainingReadouts'
import { IronMascot } from '../components/mascot'
import { useIsGatorMode } from '../theme'
import './home.css'

const DAY = 86_400_000
function useCalendarDate() {
  const [date, setDate] = useState(() => new Date())
  useEffect(() => {
    let timer: number | undefined
    const refresh = () => setDate(previous => {
      const current = new Date()
      return current.toDateString() === previous.toDateString() ? previous : current
    })
    const stop = () => {
      if (timer !== undefined) {
        window.clearInterval(timer)
        timer = undefined
      }
    }
    const startTimer = () => {
      stop()
      if (document.visibilityState !== 'hidden') {
        timer = window.setInterval(refresh, 60_000)
      }
    }
    const onVisibilityChange = () => {
      if (document.visibilityState === 'hidden') {
        stop()
      } else {
        refresh()
        startTimer()
      }
    }
    startTimer()
    window.addEventListener('focus', refresh)
    document.addEventListener('visibilitychange', onVisibilityChange)
    return () => {
      stop()
      window.removeEventListener('focus', refresh)
      document.removeEventListener('visibilitychange', onVisibilityChange)
    }
  }, [])
  return date
}

function Elapsed({start}: { start: number }) {
  const [now, setNow] = useState(Date.now)
  useEffect(() => {
    let id: number | undefined
    const tick = () => setNow(Date.now())
    const stop = () => {
      if (id !== undefined) {
        window.clearInterval(id)
        id = undefined
      }
    }
    const startTimer = () => {
      stop()
      if (document.visibilityState !== 'hidden') {
        id = window.setInterval(tick, 1000)
      }
    }
    const onVisibilityChange = () => {
      if (document.visibilityState === 'hidden') {
        stop()
      } else {
        tick()
        startTimer()
      }
    }
    startTimer()
    document.addEventListener('visibilitychange', onVisibilityChange)
    return () => {
      stop()
      document.removeEventListener('visibilitychange', onVisibilityChange)
    }
  }, [])
  const t = Math.max(0, Math.floor((now - start) / 1000))
  const hh = Math.floor(t / 3600), mm = Math.floor(t / 60) % 60, ss = t % 60
  return <time>{hh > 0 ? `${hh.toString().padStart(2, '0')}:` : ''}{mm.toString().padStart(2, '0')}:{ss.toString().padStart(2, '0')}</time>
}

function BackupReminder({onDismiss}: {onDismiss: () => void}) {
  const {save, ready} = useSaveBackup()
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [dismissing, setDismissing] = useState(false)
  const dismissTimer = useRef<number | undefined>(undefined)

  useEffect(() => () => {
    if (dismissTimer.current !== undefined) window.clearTimeout(dismissTimer.current)
  }, [])

  async function handleSave() {
    if (saving || !ready || dismissing) return
    setSaving(true)
    setError('')
    try { await save() } catch (e) { setError(e instanceof Error ? e.message : 'Could not save this backup.') }
    finally { setSaving(false) }
  }

  function handleDismiss() {
    if (dismissing) return
    const fullMotion =
      document.documentElement.dataset.motion === 'full' &&
      !window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
    if (!fullMotion) {
      onDismiss()
      return
    }
    setDismissing(true)
    dismissTimer.current = window.setTimeout(() => {
      dismissTimer.current = undefined
      onDismiss()
    }, 280)
  }

  return <div className={`home-backup-wrap${dismissing ? ' is-dismissing' : ''}`}><div className="home-backup">
    <HardDriveDownload size={17}/><span>Backup due</span>
    <button onClick={handleSave} disabled={!ready || saving || dismissing}>{saving ? 'Saving…' : ready ? 'Save backup' : 'Preparing…'}</button>
    <button className="home-dismiss" onClick={handleDismiss} disabled={dismissing} aria-label="Dismiss backup reminder"><X size={17}/></button>
  </div>{error && <p role="alert">{error}</p>}</div>
}

function RoutineControl({ongoing}: {ongoing: boolean}) {
  const navigate = useNavigate()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const info = useLiveQuery(async () => {
    const routine = await db.routines.filter(r => r.isActive).first()
    if (!routine) return null
    const days = (await db.routine_days.where('routineId').equals(routine.id).toArray()).sort((a, b) => a.orderIndex - b.orderIndex || a.id - b.id)
    const day = days.find(d => d.orderIndex === routine.nextDayOrderIndex) ?? days[0]
    const plan = day ? (await db.routine_exercises.where('routineDayId').equals(day.id).toArray()).sort((a, b) => a.orderIndex - b.orderIndex || a.id - b.id) : []
    const exercises = await db.exercises.bulkGet(plan.slice(0, 3).map(p => p.exerciseId))
    return {routine, days, day, exercises, plan}
  }, [])
  async function start() {
    if (!info?.day || busy || ongoing) return
    setError('')
    setBusy(true)
    try {
      await startWorkout({routineDayId: info.day.id})
      navigate('/workout/active')
    } catch (e) { setError(e instanceof Error ? e.message : 'Could not start this routine.') }
    finally { setBusy(false) }
  }
  if (info === undefined) return null
  return <section aria-labelledby="home-routine">
    <div className="home-section-heading"><h2 id="home-routine">Routine</h2>{info && <Link className="home-text-link" to="/routines">Switch</Link>}</div>
    {!info ? <Link to="/routines" className="home-surface home-routine-link hover-lift">
      <ClipboardList size={20}/><span>Set up a routine</span><ChevronRight size={18}/>
    </Link> : <div className="home-surface home-routine-body">
      <div className="home-routine-title"><h3>{info.routine.name}</h3><Link className="home-text-link" to={`/routines/${info.routine.id}`}>Manage</Link></div>
      {info.day && <p>{info.day.name} · Day {info.days.findIndex(d => d.id === info.day.id) + 1} of {info.days.length}</p>}
      {info.exercises.map((ex, i) => ex && <div key={info.plan[i].id} className="home-routine-ex"><span>{ex.name}</span><small>{info.plan[i].targetSets} sets</small></div>)}
      {info.day && <button className="home-start-day" onClick={start} disabled={ongoing || busy || !info.plan.length}>{busy ? 'Starting…' : 'Start day'}<ArrowRight size={16}/></button>}
      {error && <p role="alert">{error}</p>}
    </div>}
  </section>
}

export default function Home() {
  const date = useCalendarDate()
  const gatorHook = useIsGatorMode()
  const prefs = useLiveQuery(() => db.preferences.get('default'), []) ?? defaultPreferences
  const gatorMode = gatorHook || prefs.appIcon === 'swole_alligator'
  const sessions = useLiveQuery(async () => {
    const workouts = await db.workouts.orderBy('startTime').reverse().toArray()
    return {
      active: workouts.find(w => w.endTime === null),
      completed: workouts.filter(w => w.endTime !== null),
    }
  }, [])
  const active = sessions?.active
  const completed = sessions?.completed ?? []
  const earliest = completed.length ? completed[completed.length - 1].endTime : null
  const backupAnchor = prefs.lastBackupAtEpochMs ?? earliest
  const due = completed.length > 0 && (
    prefs.workoutsSinceLastBackup >= 3 || backupAnchor !== null && Date.now() - backupAnchor >= 7 * DAY
  )
  const reminderKey = `${backupAnchor}:${prefs.workoutsSinceLastBackup}:${Math.floor(Date.now() / (7 * DAY))}`
  const [dismissed, setDismissed] = useState(() => {
    try { return localStorage.getItem('ironlog.dismissedBackupReminder') } catch { return null }
  })
  const [seismic, setSeismic] = useState(false)
  const seismicTimer = useRef<number | undefined>(undefined)
  const seismicRaf = useRef<number | undefined>(undefined)

  useEffect(() => () => {
    if (seismicTimer.current !== undefined) window.clearTimeout(seismicTimer.current)
    if (seismicRaf.current !== undefined) window.cancelAnimationFrame(seismicRaf.current)
  }, [])

  const triggerSeismic = useCallback(() => {
    if (document.documentElement.dataset.motion === 'reduced' ||
      window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return
    if (seismicTimer.current !== undefined) window.clearTimeout(seismicTimer.current)
    if (seismicRaf.current !== undefined) window.cancelAnimationFrame(seismicRaf.current)
    setSeismic(false)
    seismicRaf.current = window.requestAnimationFrame(() => {
      seismicRaf.current = undefined
      setSeismic(true)
      seismicTimer.current = window.setTimeout(() => {
        setSeismic(false)
        seismicTimer.current = undefined
      }, 340)
    })
  }, [])

  function dispatchSurge() {
    window.dispatchEvent(new Event('ironlog:apex-surge'))
  }

  function dismiss() {
    setDismissed(reminderKey)
    try { localStorage.setItem('ironlog.dismissedBackupReminder', reminderKey) } catch { /* In-memory dismissal still applies. */ }
  }
  const weekday = date.toLocaleDateString(undefined, {weekday: 'short'})
  const monthDay = date.toLocaleDateString(undefined, {month: 'short', day: 'numeric'})
  return <main className="screen home-screen iron-home" aria-labelledby="home-greeting">
    <header className="home-brandbar">
      <div className="home-brand" aria-label="IronLog">
        <IronMascot
          pose="badge"
          brand={gatorMode ? 'gator' : 'chimp'}
          size={38}
          interactive
          label={gatorMode ? 'IronLog UF emblem' : 'IronLog emblem'}
        />
        <span className="home-brand-text" aria-hidden="true">
          <span className="home-brand-wordmark">IRON<b>LOG</b></span>
          <span className="home-brand-sub">{gatorMode ? 'THE SWAMP' : 'SWOLE CHIMP'}</span>
        </span>
      </div>
      <span className="home-date"><CalendarDays size={15}/>{weekday} · {monthDay}</span>
      <Link className="home-settings" to="/settings" aria-label="Open settings"><Settings size={19}/></Link>
    </header>
    {due && dismissed !== reminderKey && <BackupReminder onDismiss={dismiss}/>}
    <h1 className="home-greeting home-stagger" style={{'--stagger-i': 0} as CSSProperties} id="home-greeting">Hey, {prefs.userName.trim() || 'Athlete'}</h1>
    <section
      className={`home-surface home-session home-stagger ${active ? 'is-active' : ''} ${seismic ? 'is-seismic' : ''}`}
      style={{'--stagger-i': 1} as CSSProperties}
      aria-labelledby="home-session-title"
    >
      <div className="home-session-stage">
        <div className="home-session-art">
          <ApexKineticStage
            active={Boolean(active)}
            gatorMode={gatorMode}
            onSurge={triggerSeismic}
          />
        </div>
        <div className="home-session-copy">
          <span className="home-session-state"><i className="home-status-dot"/>{active ? 'Session in progress' : 'Free session'}</span>
          <h2 id="home-session-title">{active ? active.name || active.routineDayNameSnapshot || 'Active workout' : <><span className="home-title-first">Quick</span> start</>}</h2>
          {active && <p className="home-live-meta"><Elapsed start={active.startTime}/> · {active.startedFromRoutine ? 'Routine' : 'Free log'}</p>}
          <Link className="home-primary" to={active ? '/workout/active' : '/exercises/select?workoutMode=true'}>
            <Play size={16} fill="currentColor" strokeWidth={1.75}/>{active ? 'Resume workout' : 'Start workout'}
          </Link>
        </div>
        <button
          type="button"
          className="home-art-surge"
          aria-label="Unleash apex surge"
          title="Unleash apex surge"
          onClick={dispatchSurge}
        >
          <Zap size={15} aria-hidden="true"/><span>Surge</span>
        </button>
      </div>
      {!active && <Link className="home-plan-row" to="/workout/builder"><ClipboardList size={18}/><span>Plan a workout</span><ArrowRight size={17}/></Link>}
    </section>
    <TrainingReadouts prefs={prefs} dayKey={date.toDateString()} routine={<RoutineControl ongoing={!!active}/>}/>
  </main>
}
