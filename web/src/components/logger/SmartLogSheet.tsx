import { useMemo, useState } from 'react'
import { useLiveQuery } from 'dexie-react-hooks'
import { ArrowRight, Check, ChevronDown, ListOrdered, Trash2 } from 'lucide-react'
import { db, defaultPreferences } from '../../db/database'
import type { DistanceUnit, Exercise } from '../../db/types'
import { matchExercises, parseSmartLog } from '../../domain/smartLogParser'
import { validateSet } from '../../domain/loggingValidation'
import { distanceLabels, formatWeight, weightLabel } from '../../domain/units'
import { BottomSheet } from '../primitives'
import './tools.css'

export type SmartLogEntry = {
  exerciseId: number
  weight: number | null
  reps: number | null
  rpe: number | null
  distanceValue: number | null
  distanceUnit: DistanceUnit | null
  durationSeconds: number | null
  calories: number | null
  isWarmup: boolean
}

type Props = {
  initialExerciseId?: number
  onOpenQueue: (sets: SmartLogEntry[]) => void
  onClose: () => void
}

function duration(seconds: number) {
  return Math.floor(seconds / 60) + ':' + String(seconds % 60).padStart(2, '0')
}

export function SmartLogSheet({ initialExerciseId, onOpenQueue, onClose }: Props) {
  const exercises = useLiveQuery(() => db.exercises.orderBy('name').toArray(), [])
  const prefs = useLiveQuery(() => db.preferences.get('default'), []) ?? defaultPreferences
  const [text, setText] = useState('')
  const [review, setReview] = useState(false)
  const [selected, setSelected] = useState<number | undefined>()
  const [removed, setRemoved] = useState<number[]>([])
  const [error, setError] = useState('')
  const parsed = useMemo(() => parseSmartLog(text, prefs.preferredWeightUnit), [text, prefs.preferredWeightUnit])
  const matches = useMemo(() => {
    if (!review || !parsed || !exercises) return []
    if (!parsed.exerciseQuery.trim()) return exercises.filter(exercise => exercise.id === initialExerciseId)
    return matchExercises(parsed.exerciseQuery, exercises)
  }, [review, parsed, exercises, initialExerciseId])
  const exact = matches.find(item => item.name.toLowerCase() === parsed?.exerciseQuery.toLowerCase())
  const picked = exercises?.find(item => item.id === selected) ?? exact ?? (matches.length === 1 ? matches[0] : undefined)
  const rows = parsed?.sets.flatMap((draft, index) => removed.includes(index) ? [] : [{ draft, index }]) ?? []
  const unit = prefs.preferredWeightUnit
  const unitLabel = weightLabel(unit)
  const examples = ['bench 3x8 at 185 rpe 8', 'pullups 3 sets of 10', 'run 3.1 mi 24:30', 'db curl 35, 12, 10, 8']

  function reviewDraft() {
    if (!parsed?.sets.length) {
      setError('Enter a set with reps, weight, time, distance, or calories.')
      return
    }
    setError('')
    setReview(true)
    setRemoved([])
  }

  function selectExercise(id: number) {
    setSelected(id)
    setError('')
  }

  function openQueue(exercise: Exercise | undefined) {
    if (!exercise || !rows.length) return
    const entries = rows.map(({ draft }) => ({ ...draft, exerciseId: exercise.id, isWarmup: draft.isWarmup || exercise.targetMuscle === 'Warmups' }))
    for (const entry of entries) {
      const message = validateSet(exercise.logType, entry)
      if (message) {
        setError(message)
        return
      }
    }
    onOpenQueue(entries)
  }

  const action = review
    ? <button className="btn primary tool-action" disabled={!picked || !rows.length} onClick={() => openQueue(picked)}>
        {picked && <Check size={17} />} Open in logger <ArrowRight size={17} />
      </button>
    : <button className="btn primary tool-action" disabled={!text.trim()} onClick={reviewDraft}>
        <ListOrdered size={18} /> Review sets
      </button>

  return <BottomSheet title="Smart Log" onClose={onClose} footer={<>
    {error && <p className="tool-error" role="alert">{error}</p>}
    {action}
  </>}>
    <div className="logger-tool">
      <label className="tool-field">
        <span className="eyebrow">Set entry</span>
        <textarea className="smart-log-input" rows={3} value={text} maxLength={600} placeholder="bench 3x8 at 185 rpe 8"
          onChange={event => { setText(event.target.value); setReview(false); setSelected(undefined); setError('') }} />
      </label>
      {!review && <>
        <details className="tool-disclosure">
          <summary>Examples <ChevronDown size={16} /></summary>
          <div className="smart-examples">{examples.map(example => <button key={example} type="button" onClick={() => { setText(example); setError(''); setSelected(undefined) }}>{example}</button>)}</div>
        </details>
      </>}
      {review && <>
        <div>
          <label className="eyebrow" htmlFor="smart-exercise">{picked ? 'Exercise' : 'Choose exercise'}</label>
          <select id="smart-exercise" className="tool-select" value={picked?.id ?? ''} onChange={event => selectExercise(Number(event.target.value))}>
            <option value="" disabled>Choose exercise</option>
            {matches.length > 0 && <optgroup label="Matches">{matches.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}</optgroup>}
            <optgroup label="Library">{exercises?.filter(item => !matches.some(match => match.id === item.id)).map(item => <option key={item.id} value={item.id}>{item.name}</option>)}</optgroup>
          </select>
        </div>
        <section aria-label="Draft sets">
          <div className="tool-section-heading"><span className="eyebrow">Draft queue</span><span>{rows.length} {rows.length === 1 ? 'set' : 'sets'}</span></div>
          {rows.map(({ draft, index }, order) => {
            const core = [
              draft.weight != null ? formatWeight(draft.weight, unit) + ' ' + unitLabel : null,
              draft.reps != null ? draft.reps + ' reps' : null,
              draft.distanceValue != null && draft.distanceUnit != null ? draft.distanceValue + ' ' + distanceLabels[draft.distanceUnit] : null,
              draft.durationSeconds != null ? duration(draft.durationSeconds) : null,
              draft.calories != null ? draft.calories + ' cal' : null,
            ].filter(Boolean)
            return <div className="smart-draft-row stagger-in" key={index} style={{ '--stagger-i': Math.min(order, 12) } as React.CSSProperties}>
              <span className="tool-number">{String(order + 1).padStart(2, '0')}</span>
              <div><strong>{core.join(' · ')}</strong>{(draft.rpe != null || draft.isWarmup) && <small>{[draft.isWarmup ? 'Warm-up' : null, draft.rpe != null ? 'RPE ' + draft.rpe : null].filter(Boolean).join(' · ')}</small>}</div>
              <button type="button" className="icon-button" aria-label={'Remove draft set ' + (order + 1)} onClick={() => setRemoved(previous => [...previous, index])}><Trash2 size={16} /></button>
            </div>
          })}
        </section>
      </>}
    </div>
  </BottomSheet>
}

export default SmartLogSheet
