import type { WeightUnit, WorkoutSet } from '../db/types'
import { canonicalMuscle, DAY, groupBy, positive, volumeOf } from './analyticsCore'
import type { TrainingSource } from './analyticsCore'
import { paretoBest } from './records'
import { formatWeight, weightLabel } from './units'

export type ShareableSet = WorkoutSet & {isPersonalRecord: boolean}
export interface ShareableWorkout {
  date:number; title:string; durationMinutes:number|null; totalVolume:number; totalWorkingSets:number
  exercises:{id:number;name:string;targetMuscle:string;sets:ShareableSet[]}[]
  recap:string|null; highlight:string; muscles:string[]; prCount:number
}

export function buildWorkoutShare(source: TrainingSource, workoutId: number, recap?: string): ShareableWorkout | null {
  const workout = source.workouts.find(w => w.id === workoutId)
  if (!workout) return null
  const metadata = new Map(source.exercises.map(e => [e.id,e]))
  const finished = new Set(source.workouts.filter(w => w.endTime !== null).map(w => w.id))
  const all = source.sets.filter(s => s.isComplete && metadata.has(s.exerciseId) && canonicalMuscle(metadata.get(s.exerciseId)!.targetMuscle) !== 'Warmups')
  const current = all.filter(s => s.workoutId === workoutId).sort((a,b) => a.timestamp - b.timestamp || a.id - b.id)
  const working = current.filter(s => !s.isWarmup)
  if (!working.length) return null
  const historical = all.filter(s => !s.isWarmup && s.workoutId !== workoutId && finished.has(s.workoutId) && s.timestamp < workout.startTime)
  const priorByExercise = groupBy(historical,s => s.exerciseId)
  const exercises = [...groupBy(current,s => s.exerciseId)].flatMap(([id,sets]) => {
    if (sets.every(s => s.isWarmup)) return []
    const bestAtReps = new Map<number,number>()
    for (const s of priorByExercise.get(id) ?? []) {
      if (positive(s.reps) && positive(s.weight)) bestAtReps.set(s.reps,Math.max(s.weight,bestAtReps.get(s.reps) ?? 0))
    }
    const candidates = sets.filter(s => {
      if (s.isWarmup || !positive(s.weight) || !positive(s.reps) || s.weight <= (bestAtReps.get(s.reps) ?? 0)) return false
      bestAtReps.set(s.reps,s.weight)
      return true
    })
    const prs = new Set(paretoBest(candidates).map(s => s.id))
    const exercise = metadata.get(id)!
    return [{id,name:exercise.name,targetMuscle:exercise.targetMuscle,sets:sets.map(s => ({...s,isPersonalRecord:prs.has(s.id)}))}]
  })
  const totalVolume = working.reduce((n,s) => n + volumeOf(s),0)
  const muscles = [...new Set(exercises.map(e => e.targetMuscle))].sort()
  const byMuscle = groupBy(working,s => metadata.get(s.exerciseId)!.targetMuscle)
  const [leader, leadingSets] = [...byMuscle].sort((a,b) => b[1].length - a[1].length || b[0].localeCompare(a[0]))[0]
  const prExercises = exercises.filter(e => e.sets.some(s => s.isPersonalRecord))
  const priorComparable = historical.filter(s => metadata.get(s.exerciseId)!.targetMuscle === leader && s.timestamp >= workout.startTime - 42 * DAY)
  const totals = [...groupBy(priorComparable,s => s.workoutId).values()].map(group => group.reduce((n,s) => n + volumeOf(s),0)).filter(n => n > 0)
  const leadVolume = leadingSets.reduce((n,s) => n + volumeOf(s),0)
  const highest = totals.length > 0 && totals.every(n => leadVolume > n)
  const records = prExercises.slice(0,2).map(e => e.name).join(' and ')
  let highlight = 'Logged ' + working.length + (working.length === 1 ? ' working set' : ' working sets') + ' across ' +
    exercises.length + (exercises.length === 1 ? ' exercise.' : ' exercises.')
  if (records && highest) highlight = 'PR on ' + records + ', plus your highest-volume ' + leader + ' session in six weeks.'
  else if (records) highlight = 'PR on ' + records + ' during a ' + working.length + '-set session.'
  else if (highest) highlight = 'Your highest-volume ' + leader + ' session in six weeks.'
  const title = workout.name?.trim() || workout.routineDayNameSnapshot || 'Workout'
  return {date:workout.startTime,title,durationMinutes:workout.endTime === null ? null : Math.max(0,Math.floor((workout.endTime - workout.startTime)/60_000)),
    totalVolume,totalWorkingSets:working.length,exercises,recap:recap?.trim() || null,highlight,muscles,prCount:prExercises.length}
}

function formatSeconds(s: number) {
  return s >= 60 ? Math.floor(s / 60) + 'm ' + s % 60 + 's' : s + 's'
}

// Mirror Java's shortest round-tripping float string for canonical weights converted from kg.
function androidDecimal(value: number): string {
  const float = Math.fround(value)
  if (Number.isInteger(float)) return String(float)
  for (let digits = 1; digits <= 9; digits++) {
    const rounded = Number(float.toPrecision(digits))
    if (Math.fround(rounded) === float) return String(rounded)
  }
  return String(float)
}

export function formatShareSet(s: WorkoutSet) {
  const w = s.weight !== null && Number.isFinite(s.weight) ? s.weight : null
  const d = s.durationSeconds !== null && s.durationSeconds >= 0 ? s.durationSeconds : null
  const m = s.distanceMeters !== null && Number.isFinite(s.distanceMeters) ? s.distanceMeters : null
  const c = s.calories !== null && Number.isFinite(s.calories) ? s.calories : null
  if (w !== null) {
    if (s.reps !== null) return androidDecimal(w) + ' lbs × ' + s.reps
    if (d !== null) return androidDecimal(w) + ' lbs · ' + formatSeconds(d)
    if (m !== null) return androidDecimal(w) + ' lbs · ' + androidDecimal(m) + ' m'
    return androidDecimal(w) + ' lbs'
  }
  if (s.reps !== null) return s.reps + ' reps'
  if (m !== null && d !== null) return androidDecimal(m) + ' m · ' + formatSeconds(d)
  if (c !== null && d !== null) return androidDecimal(c) + ' cal · ' + formatSeconds(d)
  if (d !== null) return formatSeconds(d)
  if (m !== null) return androidDecimal(m) + ' m'
  return c === null ? '—' : androidDecimal(c) + ' cal'
}

export function formatWorkoutShare(w: ShareableWorkout, locale?: string) {
  const d = new Date(w.date)
  const date = d.getDate() + ' ' + d.toLocaleDateString(locale, {month:'short'}) + ' ' + d.getFullYear()
  const parts: string[] = []
  if (w.durationMinutes !== null) {
    const minutes = Math.max(0,w.durationMinutes)
    parts.push('Duration: ' + (minutes >= 60 ? Math.floor(minutes/60) + 'h ' : '') + minutes % 60 + 'm')
  }
  parts.push('Volume: ' + Math.trunc(w.totalVolume).toLocaleString(locale) + ' lbs',
    w.totalWorkingSets + ' sets across ' + w.exercises.filter(e => e.sets.some(s => !s.isWarmup)).length + ' exercises')
  // These legacy GymTime literals are part of Android's portable plain-text formatter.
  let body = 'GymTime — ' + date + '\n' + parts.join('  |  ') + '\n\n'
  if (w.recap?.trim()) body += w.recap.trim() + '\n\n'
  for (const exercise of w.exercises) {
    const working = exercise.sets.filter(s => !s.isWarmup)
    if (working.length) body += exercise.name + '\n'
    for (const set of working) body += '  ' + formatShareSet(set) + (set.isPersonalRecord ? '  🏆 PR' : '') + '\n'
  }
  return body + '\nShared from GymTime'
}

export async function renderWorkoutPng(w: ShareableWorkout, options: {accent?:string;unit?:WeightUnit;iconUrl?:string} = {}): Promise<Blob> {
  const canvas = document.createElement('canvas')
  const width = 1080, height = 1260
  canvas.width = width
  canvas.height = height
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('Image export is unavailable in this browser.')
  const accent = /^#[0-9a-f]{6}$/i.test(options.accent ?? '') ? options.accent! : '#A3E635'
  const unit = options.unit ?? 'lbs'
  ctx.fillStyle = '#090A0D'
  ctx.fillRect(0,0,width,height)
  ctx.fillStyle = accent
  ctx.fillRect(0,0,width,12)
  function label(text: string, x: number, y: number, px: number, color = '#F4F6FA', weight = 600, maxWidth = 950) {
    ctx!.fillStyle = color
    ctx!.font = weight + ' ' + px + 'px system-ui, sans-serif'
    ctx!.fillText(text,x,y,maxWidth)
  }
  label('IRONLOG',64,130,66,accent,800)
  if (options.iconUrl) {
    const img = new Image()
    img.src = options.iconUrl
    try { await img.decode(); ctx.drawImage(img,914,48,96,96) } catch { /* The wordmark is always available offline. */ }
  }
  label(new Date(w.date).toLocaleDateString(undefined,{weekday:'long',month:'long',day:'numeric',year:'numeric'}),64,235,30,'#A3A9B6',500)
  label(w.title,64,345,85)
  label('TOTAL VOLUME',64,475,26,'#A3A9B6')
  label(formatWeight(w.totalVolume,unit) + ' ' + weightLabel(unit),64,605,112)
  label(w.totalWorkingSets + ' working sets',64,690,43)
  label(w.durationMinutes === null ? 'In progress' : w.durationMinutes + ' min',645,690,43)
  ctx.strokeStyle = '#3B3E47'
  ctx.beginPath(); ctx.moveTo(64,742); ctx.lineTo(1016,742); ctx.stroke()
  const shown = w.exercises.slice(0,5)
  for (const [i,exercise] of shown.entries()) {
    const y = 813 + i * 73
    const workingSets = exercise.sets.filter(s => !s.isWarmup)
    label(exercise.name,64,y,37,'#F4F6FA',600,700)
    label(workingSets.length + (workingSets.length === 1 ? ' set' : ' sets'),805,y,31,'#A3A9B6')
  }
  if (w.exercises.length > shown.length) label('+' + (w.exercises.length - shown.length) + ' more exercises',64,1180,29,'#A3A9B6')
  return new Promise((resolve,reject) => canvas.toBlob(blob => blob ? resolve(blob) : reject(new Error('Image export failed.')), 'image/png'))
}
