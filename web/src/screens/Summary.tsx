import { useEffect, useMemo, useState, type CSSProperties } from 'react'
import { Check, ChevronDown, FileDown, Star, Trophy } from 'lucide-react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { useSaveBackup } from '../db/backup'
import { updateWorkout } from '../db/repositories'
import type { Preferences, Workout } from '../db/types'
import { formatWeight, fromLbs, weightLabel } from '../domain/units'
import { calculateWeeklyVolume } from '../domain/weeklyVolume'
import { buildWorkoutShare } from '../domain/workoutShare'
import { dateLabel, describeWorkout } from '../history/presentation'
import { useHistoryData, type HistoryData } from '../history/sessionData'
import { ShareActions } from '../history/ShareActions'
import { AnimatedNumber } from '../components/primitives'
import { IronMascot } from '../components/mascot'
import { useIsGatorMode } from '../theme'
import './history.css'
import './summary-motion.css'

const burstParticles = [
  { bx: '-52px', by: '-44px' },
  { bx: '48px', by: '-46px' },
  { bx: '-58px', by: '12px' },
  { bx: '56px', by: '16px' },
  { bx: '-32px', by: '50px' },
  { bx: '36px', by: '48px' },
  { bx: '0px', by: '-58px' },
  { bx: '0px', by: '56px' },
] as const

function FinishStamp({icon, celebrate}:{icon:Preferences['appIcon']; celebrate?: boolean}) {
  const gatorHook = useIsGatorMode()
  const isGator = gatorHook || icon === 'swole_alligator'
  return <div className="finish-stamp-wrap">
    <span className="finish-stamp-burst" aria-hidden="true">
      {burstParticles.map((p, i) => (
        <i key={i} style={{'--bx': p.bx, '--by': p.by} as CSSProperties} />
      ))}
    </span>
    <div
      className={`finish-stamp finish-stamp--crest ${isGator ? 'finish-stamp--gator' : 'finish-stamp--chimp'}`}
      aria-hidden="true"
    >
      <IronMascot pose="stamp" brand={isGator ? 'gator' : 'chimp'} size="100%" celebrate={celebrate} />
    </div>
  </div>
}

export default function Summary() {
  const {workoutId} = useParams()
  const data = useHistoryData()
  if (!data) return <main className="screen"><p role="status">Loading workout…</p></main>
  const workout = data.workouts.find(w => w.id === Number(workoutId))
  if (!workout) return <main className="screen"><h1>Workout unavailable</h1><Link className="btn" to="/history">Open history</Link></main>
  if (workout.endTime === null) return <main className="screen"><h1>Workout in progress</h1><Link className="btn primary" to="/workout/active">Resume workout</Link></main>
  return <Completed key={workout.id} workout={workout} data={data}/>
}

function Completed({workout,data}:{workout:Workout;data:HistoryData}) {
  const {ready,save} = useSaveBackup()
  const [rating,setRating] = useState(workout.rating)
  const [starBurst,setStarBurst] = useState<{star:number;seq:number}|null>(null)
  const [note,setNote] = useState(workout.ratingNote || '')
  const [details,setDetails] = useState(false)
  const [saving,setSaving] = useState(false)
  const [savingBackup,setSavingBackup] = useState(false)
  const [message,setMessage] = useState('')
  const [error,setError] = useState('')
  const navigate = useNavigate()
  const location = useLocation()
  const [stampPhase, setStampPhase] = useState<'pending' | 'playing' | 'rest'>(() =>
    location.state?.justFinished === workout.id && document.documentElement.dataset.motion !== 'reduced' &&
      !window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ? 'pending' : 'rest')
  // Consume the finish entry so refreshing or returning to this record opens it at rest.
  useEffect(() => {
    if (location.state?.justFinished !== workout.id) return
    void navigate(location.pathname + location.search + location.hash, {replace:true, state:null, preventScrollReset:true})
  }, [location, navigate, workout.id])
  useEffect(() => {
    if (stampPhase === 'rest') return
    if (stampPhase === 'playing') {
      const settle = window.setTimeout(() => setStampPhase('rest'), 640)
      return () => window.clearTimeout(settle)
    }
    // The result gets two paint opportunities before the stamp clock starts.
    let paints = 0
    let frame = window.requestAnimationFrame(afterPaint)
    function afterPaint() {
      if (paints < 2) {
        paints++
        frame = window.requestAnimationFrame(afterPaint)
      } else {
        setStampPhase('playing')
      }
    }
    // A suspended or throttled page should settle without a delayed celebration.
    const deadline = window.setTimeout(() => setStampPhase('rest'), 900)
    return () => { window.cancelAnimationFrame(frame); window.clearTimeout(deadline) }
  }, [stampPhase])
  const unit = data.prefs.preferredWeightUnit
  const stats = useMemo(() => describeWorkout(workout, data.sets.filter(s => s.workoutId === workout.id), new Map(data.exercises.map(ex => [ex.id,ex]))), [data,workout])
  const share = useMemo(() => buildWorkoutShare(data,workout.id), [data,workout.id])
  const week = useMemo(() => {
    const end = workout.endTime!
    const completed = new Set(data.workouts.filter(w => w.endTime !== null && w.endTime <= end).map(w => w.id))
    return calculateWeeklyVolume(data.sets.filter(s => completed.has(s.workoutId) && s.timestamp <= end), end)
  }, [data.sets, data.workouts, workout.endTime])
  const mass = (lb:number) => formatWeight(lb,unit) + ' ' + weightLabel(unit)
  const convertedVolume = fromLbs(stats.volume, unit)

  return <main className="screen his-summary">
    <header className="his-summary-head summary-stagger" style={{'--stagger-i': 0} as CSSProperties}><div><span className="eyebrow"><Check size={14}/> Workout complete</span><h1>{stats.title}</h1></div></header>
    <p className="his-summary-date summary-stagger" style={{'--stagger-i': 0} as CSSProperties}>{dateLabel(workout.startTime)}</p>
    <section className="surface his-result summary-stagger" style={{'--stagger-i': 1} as CSSProperties} data-stamp-pending={stampPhase === 'pending' || undefined} data-stamping={stampPhase === 'playing' || undefined} aria-label="Workout totals">
      <div className="finish-record">
        <div className="finish-headline summary-headline-in"><span className="eyebrow">{stats.volume > 0 ? 'Total volume' : 'Working sets'}</span>
          <p className="his-result-volume">
            {stats.volume > 0
              ? <><AnimatedNumber value={convertedVolume} format={n => String(Number(n.toFixed(2)))} /> <small>{weightLabel(unit)}</small></>
              : <AnimatedNumber value={stats.sets} format={n => String(Math.round(n))} />}
          </p>
        </div>
        <FinishStamp icon={data.prefs.appIcon} celebrate={stampPhase === 'playing'}/>
      </div>
      <dl className="his-metrics">
        <div className="summary-kpi-item" style={{'--kpi-i': 0} as CSSProperties}><dt>Duration</dt><dd>{stats.duration}</dd></div>
        {stats.volume > 0
          ? <div className="summary-kpi-item" style={{'--kpi-i': 1} as CSSProperties}><dt>Working sets</dt><dd>{stats.sets}</dd></div>
          : <div className="summary-kpi-item" style={{'--kpi-i': 1} as CSSProperties}><dt>Total volume</dt><dd>{mass(stats.volume)}</dd></div>}
        <div className="summary-kpi-item" style={{'--kpi-i': 2} as CSSProperties}><dt>Exercises</dt><dd>{stats.exercises}</dd></div>
      </dl>
      {stats.muscles.length > 0 && <div className="his-muscles summary-kpi-item" style={{'--kpi-i': 3} as CSSProperties}><span className="eyebrow">Muscles trained</span><p>{stats.muscles.join(' · ')}</p></div>}
    </section>
    {share && (share.highlight || share.exercises.length > 0) && (
      <section className="surface his-recap-card summary-stagger" style={{'--stagger-i': 2} as CSSProperties} aria-label="Exercises recap">
        {share.highlight && <p className="his-recap">{share.highlight}</p>}
        {share.exercises.length > 0 && (
          <ul className="his-recap-list">
            {share.exercises.map((ex, idx) => {
              const workingCount = ex.sets.filter(s => !s.isWarmup).length
              const hasPr = ex.sets.some(s => s.isPersonalRecord)
              return (
                <li key={ex.id} className="summary-recap-row" style={{'--row-i': Math.min(idx, 10)} as CSSProperties}>
                  <span className="his-recap-name">
                    <strong>{ex.name}</strong>
                    <small>{ex.targetMuscle}</small>
                  </span>
                  <span className="his-recap-meta">
                    {hasPr && <span className="his-pr-badge"><Trophy size={13} aria-label="Personal record" className="his-pr"/>PR</span>}
                    <small>{workingCount} {workingCount === 1 ? 'set' : 'sets'}</small>
                  </span>
                </li>
              )
            })}
          </ul>
        )}
      </section>
    )}
    <div className="his-action-strip">
      <button className="surface his-save-backup hover-lift summary-action-item" style={{'--action-i': 0} as CSSProperties} disabled={!ready || savingBackup} onClick={async () => {
        setSavingBackup(true);setMessage('')
        try { const result = await save(); if (result !== 'cancelled') setMessage(result === 'shared' ? 'Backup shared.' : 'Backup downloaded.') }
        catch(err) { setMessage(err instanceof Error ? err.message : 'Couldn’t save your backup. Try again.') }
        finally { setSavingBackup(false) }
      }}><FileDown size={21}/><strong>Save backup to Files</strong><span aria-hidden="true">↗</span></button>
      {message && <p className="his-message" role="status">{message}</p>}
    </div>
    <section className="his-feedback summary-stagger" style={{'--stagger-i': 3} as CSSProperties}><h2>How did it feel?</h2><div className="his-stars" role="group" aria-label="Workout rating">{[1,2,3,4,5].map(n => {
      const filled = rating !== null && n <= rating
      const isPeak = rating === n
      const burstKey = starBurst && filled && n <= starBurst.star ? `pop-${starBurst.seq}-${n}` : 'idle'
      return <button
        type="button"
        key={n}
        className={`his-star-btn${filled ? ' is-selected' : ''}${isPeak ? ' is-peak' : ''}`}
        style={{'--star-i': n - 1} as CSSProperties}
        aria-label={n + (n === 1 ? ' star' : ' stars')}
        aria-pressed={isPeak}
        data-filled={filled}
        onClick={() => {
          const next = rating === n ? null : n
          setRating(next)
          if (next !== null) setStarBurst(prev => ({star: n, seq: (prev?.seq ?? 0) + 1}))
        }}
      ><span key={burstKey} className="his-star-glyph"><Star size={25}/></span></button>
    })}</div>
      <label className="his-note-field">Session note<textarea className="his-input" placeholder="Optional" rows={3} value={note} maxLength={2000} onChange={e => setNote(e.target.value)}/></label>
      {note.length >= 1800 && <small>{note.length} / 2,000</small>}
    </section>
    <section className="his-more summary-action-item" style={{'--action-i': 1} as CSSProperties}><button type="button" aria-expanded={details} className="his-action" onClick={() => setDetails(!details)}>Sharing &amp; details<ChevronDown size={19} style={{transform:details ? 'rotate(180deg)' : undefined}}/></button>
      {details && <div className="his-more-panel"><dl className="his-compare"><div><dt>This week</dt><dd>{mass(week.currentWeek)}</dd></div><div><dt>Previous week</dt><dd>{mass(week.lastWeek)}</dd></div></dl>
        {share && <ShareActions share={share} prefs={data.prefs}/>}</div >}
    </section>
    {error && <p role="alert">{error}</p>}
    <div className="his-footer"><button className="btn" disabled={saving} onClick={() => navigate('/',{replace:true})}>Skip</button><button className="btn primary" disabled={saving} onClick={async () => {
      if(saving) return
      setSaving(true)
      try { await updateWorkout(workout.id,{ rating, ratingNote:note.trim() || null }); navigate('/',{replace:true}) }
      catch(err) { setError(err instanceof Error ? err.message : 'Couldn’t save this rating.'); setSaving(false) }
    }}><Check size={18}/>Done</button></div>
  </main>
}
