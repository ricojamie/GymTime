import { useEffect, useMemo, useState } from 'react'
import { Check, ChevronDown, FileDown, Star } from 'lucide-react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { useSaveBackup } from '../db/backup'
import { updateWorkout } from '../db/repositories'
import type { Preferences, Workout } from '../db/types'
import { formatWeight, weightLabel } from '../domain/units'
import { calculateWeeklyVolume } from '../domain/weeklyVolume'
import { buildWorkoutShare } from '../domain/workoutShare'
import { dateLabel, describeWorkout } from '../history/presentation'
import { useHistoryData, type HistoryData } from '../history/sessionData'
import { ShareActions } from '../history/ShareActions'
import { iconUrls } from '../theme'
import './history.css'
import './summary-motion.css'

function FinishStamp({icon}:{icon:Preferences['appIcon']}) {
  return <svg className="finish-stamp" viewBox="0 0 128 128" aria-hidden="true" focusable="false">
    <g className="finish-stamp-register" fill="none" stroke="currentColor" strokeWidth="1.5">
      <path d="M5 56v-4h10m98 24h10v-4M8 64h8m96 0h8"/>
    </g>
    <path className="finish-stamp-impact" fill="none" stroke="currentColor" d="m44 16 40 0 28 28v40L84 112H44L16 84V44z"/>
    <g className="finish-stamp-die">
      <path className="finish-stamp-rim" d="M45 19h38l26 26v38l-26 26H45L19 83V45Z"/>
      <path className="finish-stamp-groove" d="M47 25h34l22 22v34l-22 22H47L25 81V47Z"/>
      <path fill="none" stroke="currentColor" strokeWidth="2.5" d="m37 40 10-10h12m10 68h12l10-10"/>
      {icon === 'swole_alligator'
        ? <image href={iconUrls(icon).stamp} x="37" y="37" width="54" height="54"/>
        : <g transform="translate(15 15) scale(.76)">
          <path fill="currentColor" d="m27 88 18-48h9L36 88z"/>
          <path className="finish-stamp-monogram" d="m42 88 18-48h12L54 88zm19 0 18-48h12L77 76h25l-4 12z"/>
        </g>}
    </g>
  </svg>
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
  const [note,setNote] = useState(workout.ratingNote || '')
  const [details,setDetails] = useState(false)
  const [saving,setSaving] = useState(false)
  const [savingBackup,setSavingBackup] = useState(false)
  const [message,setMessage] = useState('')
  const [error,setError] = useState('')
  const navigate = useNavigate()
  const location = useLocation()
  const [stamping, setStamping] = useState(location.state?.justFinished === workout.id)
  // Consume the finish entry so refreshing or returning to this record opens it at rest.
  useEffect(() => {
    if (location.state?.justFinished !== workout.id) return
    void navigate(location.pathname + location.search + location.hash, {replace:true, state:null, preventScrollReset:true})
  }, [location, navigate, workout.id])
  useEffect(() => {
    if (!stamping) return
    const timeout = window.setTimeout(() => setStamping(false), 720)
    return () => window.clearTimeout(timeout)
  }, [stamping])
  const unit = data.prefs.preferredWeightUnit
  const stats = useMemo(() => describeWorkout(workout, data.sets.filter(s => s.workoutId === workout.id), new Map(data.exercises.map(ex => [ex.id,ex]))), [data,workout])
  const share = useMemo(() => buildWorkoutShare(data,workout.id), [data,workout.id])
  const week = useMemo(() => {
    const end = workout.endTime!
    const completed = new Set(data.workouts.filter(w => w.endTime !== null && w.endTime <= end).map(w => w.id))
    return calculateWeeklyVolume(data.sets.filter(s => completed.has(s.workoutId) && s.timestamp <= end), end)
  }, [data.sets, data.workouts, workout.endTime])
  const mass = (lb:number) => formatWeight(lb,unit) + ' ' + weightLabel(unit)

  return <main className="screen his-summary">
    <header className="his-summary-head"><div><span className="eyebrow"><Check size={14}/> Workout complete</span><h1>{stats.title}</h1></div></header>
    <p className="his-summary-date">{dateLabel(workout.startTime)}</p>
    <section className="surface his-result" data-stamping={stamping || undefined} aria-label="Workout totals">
      <div className="finish-record">
        <div className="finish-headline"><span className="eyebrow">{stats.volume > 0 ? 'Total volume' : 'Working sets'}</span>
          <p className="his-result-volume">{stats.volume > 0 ? <>{formatWeight(stats.volume,unit)} <small>{weightLabel(unit)}</small></> : stats.sets}</p>
        </div>
        <FinishStamp icon={data.prefs.appIcon}/>
      </div>
      <dl className="his-metrics"><div><dt>Duration</dt><dd>{stats.duration}</dd></div>
        {stats.volume > 0
          ? <div><dt>Working sets</dt><dd>{stats.sets}</dd></div>
          : <div><dt>Total volume</dt><dd>{mass(stats.volume)}</dd></div>}
        <div><dt>Exercises</dt><dd>{stats.exercises}</dd></div>
      </dl>
      {stats.muscles.length > 0 && <div className="his-muscles"><span className="eyebrow">Muscles trained</span><p>{stats.muscles.join(' · ')}</p></div>}
    </section>
    {share?.highlight && <p className="his-recap">{share.highlight}</p>}
    <button className="surface his-save-backup" disabled={!ready || savingBackup} onClick={async () => {
      setSavingBackup(true);setMessage('')
      try { const result = await save(); if (result !== 'cancelled') setMessage(result === 'shared' ? 'Backup shared.' : 'Backup downloaded.') }
      catch(err) { setMessage(err instanceof Error ? err.message : 'Couldn’t save your backup. Try again.') }
      finally { setSavingBackup(false) }
    }}><FileDown size={21}/><strong>Save backup to Files</strong><span aria-hidden="true">↗</span></button>
    {message && <p className="his-message" role="status">{message}</p>}
    <section className="his-feedback"><h2>How did it feel?</h2><div className="his-stars" role="group" aria-label="Workout rating">{[1,2,3,4,5].map(n => <button type="button" aria-label={n + (n === 1 ? ' star' : ' stars')} aria-pressed={rating === n} data-filled={rating !== null && n <= rating} key={n} onClick={() => setRating(rating === n ? null : n)}><Star size={25}/></button>)}</div>
      <label className="his-note-field">Session note<textarea className="his-input" placeholder="Optional" rows={3} value={note} maxLength={2000} onChange={e => setNote(e.target.value)}/></label>
      {note.length >= 1800 && <small>{note.length} / 2,000</small>}
    </section>
    <section className="his-more"><button type="button" aria-expanded={details} className="his-action" onClick={() => setDetails(!details)}>Sharing &amp; details<ChevronDown size={19} style={{transform:details ? 'rotate(180deg)' : undefined}}/></button>
      {details && <><dl className="his-compare"><div><dt>This week</dt><dd>{mass(week.currentWeek)}</dd></div><div><dt>Previous week</dt><dd>{mass(week.lastWeek)}</dd></div></dl>
        {share && <ShareActions share={share} prefs={data.prefs}/>}</>}
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
