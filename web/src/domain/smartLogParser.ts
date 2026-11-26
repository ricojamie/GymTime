import type { DistanceUnit, Exercise, WeightUnit } from '../db/types'
import { emptyDraft, type SetDraft } from './loggingValidation'
import { toLbs } from './units'

export interface SmartLogResult {
  exerciseQuery: string
  sets: SetDraft[]
}

const weightUnitPattern = '(?:lbs?|pounds?|kgs?|kilos?|kilograms?)'
const numberPattern = '(\\d+(?:\\.\\d+)?)'
const weightWithUnit = new RegExp(numberPattern + '\\s*' + weightUnitPattern + '\\b', 'i')
const distancePattern = /(\d+(?:\.\d+)?)\s*(km|kilomet(?:er|re)s?|mi|miles?|m|met(?:er|re)s?|yds?|yards?|ft|feet|foot|steps?|floors?)\b/i
const aliases: Record<string, string> = {
  db: 'dumbbell', bb: 'barbell', ohp: 'overhead press', rdl: 'romanian deadlift',
  pullups: 'pull up', pullup: 'pull up', pushups: 'push up', pushup: 'push up',
}

function distanceUnit(token: string): DistanceUnit {
  if (/^k/.test(token)) return 'KILOMETERS'
  if (/^mi/.test(token)) return 'MILES'
  if (/^y/.test(token)) return 'YARDS'
  if (/^(ft|fe|foo)/.test(token)) return 'FEET'
  if (/^step/.test(token)) return 'STEPS'
  if (/^floor/.test(token)) return 'FLOORS'
  return 'METERS'
}

function duration(text: string) {
  const clock = /\b(\d+):(\d{1,2})(?::(\d{1,2}))?\b/.exec(text)
  if (clock) {
    const a = Number(clock[1]), b = Number(clock[2]), c = clock[3] === undefined ? null : Number(clock[3])
    if (b > 59 || (c !== null && c > 59)) return null
    return (c === null ? a * 60 + b : a * 3600 + b * 60 + c) || null
  }
  let total = 0
  for (const token of text.matchAll(/(\d+(?:\.\d+)?)\s*(h(?:rs?|ours?)?|mins?|minutes?|s(?:ec(?:ond)?s?)?|m(?=\s*\d+\s*s))(?=\b|\d)/g)) {
    const unit = token[2][0]
    total += Number(token[1]) * (unit === 'h' ? 3600 : unit === 'm' ? 60 : 1)
  }
  return Math.round(total) || null
}

export function parseSmartLog(text: string, preferredUnit: WeightUnit = 'lbs'): SmartLogResult | null {
  let command = text.trim().replace(/^(?:log|record|add)\b[\s,:-]*/i, '')
    .replace(/(\d),(?=\d{3}(?!\d))/g, '$1').replace(/(^|[^\d])\.(?=\d)/g, '$10.')
  const warmup = /^(?:warm[ -]?up|wu)\b[\s,:-]*/i
  const isLeadingWarmup = warmup.test(command)
  command = command.replace(warmup, '')
  const first = command.search(/\d/)
  if (first < 0) return null
  // Don't turn a typed negative metric into a positive one by treating its sign as punctuation.
  if (/(?:^|\s)-\s*\d/.test(command)) return null
  const exerciseQuery = command.slice(0, first).trim().replace(/[\s,:;-]+$/g, '')
    .replace(/(?:\b(?:log|record|add|sets?|for|at|with|of)[\s,:;-]*)+$/i, '').trim()
  let payload = command.slice(first).toLowerCase().replace(/×/g, 'x')
  const rpe = /\brpe\s*(?:of\s*)?(\d+(?:\.\d+)?)/i.exec(payload)
  const isWarmup = isLeadingWarmup || /\b(?:warm[ -]?up|wu)\b/.test(payload)
  const unit: WeightUnit = /(?:kg[s]?|kilos?|kilograms?)\b/.test(payload) ? 'kg' :
    /(?:lbs?|pounds?)\b/.test(payload) ? 'lbs' : preferredUnit
  payload = payload.replace(/\brpe\s*(?:of\s*)?\d+(?:\.\d+)?/g, '')
    .replace(/\b(?:warm[ -]?up|wu)\b/g, '').trim()
  const common = {...emptyDraft(), rpe: rpe ? Number(rpe[1]) : null, isWarmup}
  const canonical = (token: string) => toLbs(Number(token), unit)
  function repeat(count: number, reps: number, weight: number | null): SmartLogResult | null {
    if (count <= 0 || reps <= 0 || !Number.isSafeInteger(count) || !Number.isSafeInteger(reps)) return null
    return {exerciseQuery, sets: Array.from({length: Math.min(count, 20)}, () => ({...common, reps, weight}))}
  }
  const multi = new RegExp('^(\\d+)\\s*(x\\s*|sets?\\s*(?:of\\s*)?)(\\d+)\\s*(?:reps?\\s*)?(?:(?:at|@|with)\\s*' +
    numberPattern + '|' + numberPattern + '\\s*' + weightUnitPattern + '\\b)', 'i').exec(payload)
  if (multi) return repeat(+multi[1], +multi[3], canonical(multi[4] || multi[5]))
  const bodyweight = /^(\d+)\s*sets?\s*(?:of\s*)?(\d+)\s*(?:reps?)?\s*$/.exec(payload)
  if (bodyweight) return repeat(+bodyweight[1], +bodyweight[2], null)
  const dumbbell = new RegExp('^' + numberPattern + '(?:\\s*' + weightUnitPattern + '|s)?\\s*[,;]\\s*(\\d+(?:\\s*[,;]\\s*\\d+)+)\\s*$').exec(payload)
  if (dumbbell) return {exerciseQuery, sets: dumbbell[2].split(/[,;]/).slice(0, 20)
    .map(reps => ({...common, weight: canonical(dumbbell[1]), reps: Number(reps)}))}
  const repsFirst = new RegExp('^(\\d+)\\s*(?:reps?\\s*)?(?:by|x)\\s*' + numberPattern + '\\s*' + weightUnitPattern + '\\s*$').exec(payload)
  if (repsFirst) return repeat(1, +repsFirst[1], canonical(repsFirst[2]))
  const weightFirst = new RegExp('^' + numberPattern + '\\s*' + weightUnitPattern + '?\\s*(?:x|for)\\s*(\\d+)\\s*(?:reps?)?\\s*$').exec(payload)
  if (weightFirst) return repeat(1, +weightFirst[2], canonical(weightFirst[1]))
  const weight = weightWithUnit.exec(payload)
  const reps = /(\d+)\s*(?:reps?|repetitions?)\b/.exec(payload) ?? /^(\d+)$/.exec(payload)
  const durationPayload = payload.replace(/(\d+(?:\.\d+)?)\s*h(?:rs?|ours?)?\s*(\d+(?:\.\d+)?)\s*m(?=\b|\d)/g, '$1 hours $2 minutes').replace(/(\d+(?:\.\d+)?)\s*m(?=\s*\d+\s*s)/g, '$1 minutes ')
  const time = duration(durationPayload)
  const distance = distancePattern.exec(durationPayload)
  const calories = /(\d+(?:\.\d+)?)\s*cal(?:s|ories?)?\b/.exec(payload)
  if (!weight && !reps && !time && !distance && !calories) return null
  return {exerciseQuery, sets: [{...common, weight: weight ? canonical(weight[1]) : null, reps: reps ? +reps[1] : null,
    durationSeconds: time, distanceValue: distance ? +distance[1] : null, distanceUnit: distance ? distanceUnit(distance[2]) : null,
    calories: calories ? +calories[1] : null}]}
}

function normalizeName(name: string) {
  return name.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().replace(/&/g, ' and ')
    .replace(/[^a-z0-9]+/g, ' ').trim().split(/\s+/).flatMap(token => (aliases[token] ?? token).split(' '))
    .map(token => token.length > 3 && token.endsWith('s') && !token.endsWith('ss') ? token.slice(0, -1) : token).join(' ')
}

function editDistance(a: string, b: string): number {
  let previous = Array.from({length: b.length + 1}, (_, index) => index)
  let current = new Array<number>(b.length + 1)
  for (let i = 1; i <= a.length; i++) {
    current[0] = i
    for (let j = 1; j <= b.length; j++) current[j] = Math.min(previous[j] + 1, current[j - 1] + 1,
      previous[j - 1] + Number(a[i - 1] !== b[j - 1]))
    ;[current, previous] = [previous, current]
  }
  return previous[b.length]
}

export function matchExercises(query: string, exercises: readonly Exercise[]): Exercise[] {
  const normalized = normalizeName(query)
  if (!normalized) return []
  const words = new Set(normalized.split(' '))
  const scored = exercises.map(exercise => {
    const name = normalizeName(exercise.name)
    const tokens = new Set(name.split(' '))
    const overlap = [...words].filter(w => tokens.has(w)).length
    const similar = 1 - editDistance(normalized, name) / Math.max(normalized.length, name.length, 1)
    const score = Math.max(similar, 0.55 * overlap / Math.min(words.size, tokens.size)
      + 0.6 * overlap / (words.size + tokens.size) + 0.15 * similar)
    return {exercise, name, score, containsQuery: tokens.has(normalized)}
  }).sort((a,b) => b.score - a.score || a.name.localeCompare(b.name))
  const first = scored[0]
  if (!first || first.score < 0.35) return []
  const ambiguous = words.size === 1 && scored.filter(item => item.containsQuery).length > 1
  if (first.name === normalized || (!ambiguous && first.score >= 0.78 && first.score - (scored[1]?.score ?? 0) >= 0.10)) {
    return [first.exercise]
  }
  return scored.filter(item => item.score >= 0.35).slice(0,3).map(item => item.exercise)
}
