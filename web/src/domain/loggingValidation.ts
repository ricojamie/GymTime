import type { LogType, WorkoutSet } from '../db/types'

export type SetDraft = Pick<WorkoutSet, 'weight' | 'reps' | 'rpe' | 'durationSeconds' | 'distanceValue' |
  'distanceUnit' | 'calories' | 'isWarmup' | 'note'>
export type MetricField = 'weight' | 'reps' | 'durationSeconds' | 'distanceValue' | 'calories'

export const metricFields: Record<LogType, readonly MetricField[]> = {
  WEIGHT_REPS: ['weight', 'reps'], REPS_ONLY: ['reps'], DURATION: ['durationSeconds'],
  WEIGHT_DISTANCE: ['weight', 'distanceValue'], DISTANCE_TIME: ['distanceValue', 'durationSeconds'],
  WEIGHT_TIME: ['weight', 'durationSeconds'], CALORIES_TIME: ['calories', 'durationSeconds'],
}
const metricNames = { weight: 'Weight', reps: 'Reps', durationSeconds: 'Time', distanceValue: 'Distance', calories: 'Calories' }

export function emptyDraft(): SetDraft {
  return {weight: null, reps: null, rpe: null, durationSeconds: null, distanceValue: null,
    distanceUnit: null, calories: null, isWarmup: false, note: null}
}

export function validateSet(logType: LogType, draft: SetDraft): string | null {
  for (const field of metricFields[logType]) {
    const value = draft[field]
    if (value === null || !Number.isFinite(value)) return 'Enter ' + metricNames[field].toLowerCase() + '.'
    if (field === 'weight' && value < 0) return 'Weight must be zero or greater.'
    if (field !== 'weight' && value <= 0) return metricNames[field] + ' must be greater than zero.'
    if ((field === 'durationSeconds' || field === 'reps') && !Number.isInteger(value)) {
      return metricNames[field] + ' must be a whole number.'
    }
    if (field === 'distanceValue' && !draft.distanceUnit) return 'Choose a distance unit.'
  }
  if (draft.rpe !== null && (!Number.isFinite(draft.rpe) || draft.rpe < 0 || draft.rpe > 10)) return 'RPE must be between 0 and 10.'
  return null
}

export function normalizeDraft(logType: LogType, draft: SetDraft): SetDraft {
  const clean = emptyDraft()
  for (const field of metricFields[logType]) clean[field] = draft[field]
  if (metricFields[logType].includes('distanceValue')) clean.distanceUnit = draft.distanceUnit
  return {...clean, note: draft.note?.trim() || null, isWarmup: draft.isWarmup, rpe: draft.rpe}
}

export function afterLog(draft: SetDraft, forceWarmup = false): SetDraft {
  return {...draft, rpe: null, note: null, isWarmup: forceWarmup}
}
