import { unzipSync } from 'fflate'
import { DISTANCE_UNITS, LOG_TYPES, normalizeMuscleName } from '../types'
import { distanceToMeters, metersToDistance } from '../../domain/units'
import { csvRecords } from './csv'
import { columns, countsOf, countKeys, decodePreferences, emptySnapshot, isRecord, tableNames } from './format'
import type { Snapshot, TableName, ValidatedBackup } from './format'

const required: Record<TableName, readonly string[]> = {
  muscle_groups: ['name'],
  exercises: ['id', 'name'],
  routines: ['id', 'name'],
  routine_days: ['id', 'routineId', 'name'],
  workouts: ['id', 'startTime'],
  routine_exercises: ['routineDayId', 'exerciseId'],
  sets: ['workoutId', 'exerciseId', 'timestamp'],
  workout_exercise_instances: ['workoutId', 'exerciseId'],
}
const utf8 = new TextDecoder('utf-8', { fatal: true })
const maxEntry = 50 * 1024 * 1024
const maxTotal = 100 * 1024 * 1024
const entries = new Set(['manifest.json', 'preferences.json', ...tableNames.map((t) => t + '.csv')])
const basename = (path: string) => path.replace(/\\/g, '/').split('/').pop()!.trim()

// fflate decodes ZIP contents but leaves integrity checks to the caller.
const crcTable = Uint32Array.from({ length: 256 }, (_, i) => {
  let n = i
  for (let bit = 0; bit < 8; bit++) n = (n >>> 1) ^ (n & 1 ? 0xedb88320 : 0)
  return n >>> 0
})
function crc32(bytes: Uint8Array) {
  let crc = 0xffffffff
  for (const byte of bytes) crc = (crc >>> 8) ^ crcTable[(crc ^ byte) & 0xff]
  return (crc ^ 0xffffffff) >>> 0
}
function unpack(bytes: Uint8Array) {
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength)
  if (bytes.length < 22 || view.getUint32(0, true) !== 0x04034b50)
    throw new Error('Choose an IronLog ZIP backup')
  let end = bytes.length - 22
  while (
    end >= Math.max(0, bytes.length - 65557) &&
    (view.getUint32(end, true) !== 0x06054b50 ||
      end + 22 + view.getUint16(end + 20, true) !== bytes.length)
  )
    end--
  if (end < Math.max(0, bytes.length - 65557)) throw new Error('Incomplete ZIP directory')
  const checked = new Map<string, { crc: number; length: number; path: string }>()
  const count = view.getUint16(end + 10, true)
  let cursor = view.getUint32(end + 16, true)
  let total = 0
  for (let i = 0; i < count; i++) {
    if (cursor + 46 > end || view.getUint32(cursor, true) !== 0x02014b50)
      throw new Error('Invalid ZIP directory')
    const nameLength = view.getUint16(cursor + 28, true)
    const next =
      cursor + 46 + nameLength + view.getUint16(cursor + 30, true) + view.getUint16(cursor + 32, true)
    if (next > end) throw new Error('Invalid ZIP entry')
    const path = utf8.decode(bytes.subarray(cursor + 46, cursor + 46 + nameLength))
    const name = basename(path)
    if (entries.has(name) && !path.replace(/\\/g, '/').split('/').includes('__MACOSX')) {
      if (checked.has(name)) throw new Error('Duplicate ZIP entry ' + name)
      const length = view.getUint32(cursor + 24, true)
      total += length
      if (length > maxEntry || total > maxTotal)
        throw new Error('Backup exceeds the supported uncompressed size')
      checked.set(name, { length, crc: view.getUint32(cursor + 16, true), path })
    }
    cursor = next
  }
  const raw = unzipSync(bytes, { filter: ({ name }) => checked.get(basename(name))?.path === name })
  const files = new Map<string, string>()
  for (const [name, entry] of checked) {
    const buffer = raw[entry.path]
    if (!buffer || buffer.length !== entry.length || crc32(buffer) !== entry.crc)
      throw new Error('Damaged ZIP entry ' + name)
    files.set(name, utf8.decode(buffer))
  }
  return files
}

function parseTable(table: TableName, text: string, data: Snapshot, legacy: boolean) {
  const records = csvRecords(text)
  const header: string[] | undefined = records.next().value
  if (!header) throw new Error('Missing CSV header')
  const headers = header.map((s) => s.trim())
  if (new Set(headers).size !== headers.length) throw new Error('Duplicate column name')
  const missing = (legacy ? required[table] : columns[table]).filter((c) => !headers.includes(c))
  if (missing.length) throw new Error('Missing columns ' + missing.join(', '))
  let rowNumber = 1
  let autoId = -1
  const ids = new Set<number>()
  for (const fields of records) {
    rowNumber++
    try {
      if (rowNumber > 500001) throw new Error('Table exceeds 500,000 rows')
      if (fields.length !== headers.length) throw new Error('Wrong number of fields')
      const row: Record<string, string> = Object.fromEntries(
        headers.map((key, i) => [key, fields[i]]),
      )
      const str = (k: string) => row[k] || null
      const name = () => {
        const n = row.name?.trim()
        if (!n) throw new Error('Missing name')
        return n
      }
      const number = (k: string, fallback: number | null = null) => {
        const s = row[k]?.trim()
        if (!s) return fallback
        if (!/^-?(?:\d+(?:\.\d*)?|\.\d+)(?:e[+-]?\d+)?$/i.test(s) || !Number.isFinite(Number(s)))
          throw new Error('Invalid ' + k)
        return Number(s)
      }
      const integer = (k: string, fallback: number | null = null) => {
        const n = number(k, fallback)
        if (n !== null && !Number.isSafeInteger(n)) throw new Error('Invalid integer ' + k)
        return n
      }
      const key = (k: string) => {
        const n = integer(k)
        if (n === null || n <= 0) throw new Error('Invalid ' + k)
        return n
      }
      const time = (k: string) => {
        const t = integer(k)
        if (t === null || Math.abs(t) > 8640000000000000) throw new Error('Invalid ' + k)
        return t
      }
      const bool = (k: string, d = false) => {
        const s = row[k]?.trim().toLowerCase()
        if (!s) return d
        if (s === 'true' || s === '1') return true
        if (s === 'false' || s === '0') return false
        throw new Error('Invalid boolean ' + k)
      }
      if (table === 'muscle_groups') {
        data.muscle_groups.push({ name: normalizeMuscleName(name()) })
        continue
      }
      const needsId = Boolean(str('id')) || required[table].includes('id')
      const id = needsId ? key('id') : autoId--
      if (ids.has(id)) throw new Error('Duplicate id ' + id)
      ids.add(id)
      const order = () => ({ orderIndex: integer('orderIndex') ?? 0 })
      const group = () => ({
        supersetGroupId: str('supersetGroupId'),
        supersetOrderIndex: integer('supersetOrderIndex') ?? 0,
      })
      const unit = (k: string) => {
        const value = DISTANCE_UNITS.find((v) => v === row[k])
        if (!value && str(k)) throw new Error('Unknown ' + k)
        return value ?? null
      }
      switch (table) {
        case 'exercises': {
          const logType =
            LOG_TYPES.find((type) => type === row.logType) ?? (!str('logType') ? 'WEIGHT_REPS' : null)
          if (!logType) throw new Error('Unknown logType')
          data.exercises.push({
            id,
            name: name(),
            targetMuscle: normalizeMuscleName(row.targetMuscle || 'Other'),
            logType,
            defaultDistanceUnit: unit('defaultDistanceUnit') ?? 'MILES',
            isCustom: bool('isCustom', true),
            notes: str('notes'),
            defaultRestSeconds: integer('defaultRestSeconds') ?? 90,
            isStarred: bool('isStarred'),
            repTarget: integer('repTarget'),
          })
          break
        }
        case 'routines':
          data.routines.push({
            id,
            name: name(),
            isActive: bool('isActive'),
            nextDayOrderIndex: integer('nextDayOrderIndex') ?? 0,
          })
          break
        case 'routine_days':
          data.routine_days.push({ id, routineId: key('routineId'), name: name(), ...order() })
          break
        case 'routine_exercises':
          data.routine_exercises.push({
            id,
            routineDayId: key('routineDayId'),
            exerciseId: key('exerciseId'),
            ...order(),
            targetSets: integer('targetSets') ?? 3,
            targetRepsMin: integer('targetRepsMin'),
            targetRepsMax: integer('targetRepsMax'),
            targetRestSeconds: integer('targetRestSeconds'),
            notes: str('notes'),
            ...group(),
          })
          break
        case 'workouts': {
          const routineDayId = integer('routineDayId'),
            routineId = integer('routineId')
          const rating = integer('rating')
          if (rating !== null && (rating < 1 || rating > 5)) throw new Error('Rating outside 1–5')
          data.workouts.push({
            id,
            startTime: time('startTime'),
            endTime: str('endTime') ? time('endTime') : null,
            name: str('name'),
            note: str('note'),
            rating,
            ratingNote: str('ratingNote'),
            routineDayId,
            routineId,
            routineNameSnapshot: str('routineNameSnapshot'),
            routineDayNameSnapshot: str('routineDayNameSnapshot'),
            startedFromRoutine: bool('startedFromRoutine', legacy && !!(routineDayId || routineId)),
          })
          break
        }
        case 'sets': {
          const rpe = number('rpe')
          if (rpe !== null && (rpe < 0 || rpe > 10)) throw new Error('RPE outside 0–10')
          data.sets.push({
            id,
            workoutId: key('workoutId'),
            exerciseId: key('exerciseId'),
            weight: number('weight'),
            calories: number('calories'),
            reps: integer('reps'),
            rpe,
            durationSeconds: integer('durationSeconds'),
            distanceValue: number('distanceValue'),
            distanceUnit: unit('distanceUnit'),
            distanceMeters: number('distanceMeters'),
            isWarmup: bool('isWarmup'),
            isComplete: bool('isComplete', true),
            timestamp: time('timestamp'),
            note: str('note'),
            ...group(),
          })
          break
        }
        case 'workout_exercise_instances':
          data.workout_exercise_instances.push({
            id,
            workoutId: key('workoutId'),
            exerciseId: key('exerciseId'),
            routineExerciseId: integer('routineExerciseId'),
            ...order(),
            plannedSets: integer('plannedSets'),
            repMin: integer('repMin'),
            repMax: integer('repMax'),
            restSeconds: integer('restSeconds'),
            notes: str('notes'),
            ...group(),
            isSkipped: bool('isSkipped'),
            addedDuringWorkout: bool('addedDuringWorkout'),
          })
          break
      }
    } catch (err) {
      throw new Error('Row ' + rowNumber + ': ' + (err as Error).message)
    }
  }
  // Legacy tables sometimes omitted their primary keys.
  if (table !== 'muscle_groups') {
    let nextId = 0
    for (const row of data[table]) nextId = Math.max(nextId, row.id)
    for (const row of data[table]) if (row.id < 0) row.id = ++nextId
  }
}

function validateRelationships(data: Snapshot, legacy: boolean) {
  const map = <T extends { id: number }>(items: T[]) => new Map(items.map((item) => [item.id, item]))
  const exercises = map(data.exercises),
    routines = map(data.routines),
    days = map(data.routine_days),
    workouts = map(data.workouts)
  const routineExercises = map(data.routine_exercises)
  const check = (exists: boolean, table: string, id: number) => {
    if (!exists) throw new Error(table + '.csv: missing parent for row id ' + id)
  }
  for (const d of days.values()) check(routines.has(d.routineId), 'routine_days', d.id)
  for (const r of routineExercises.values())
    check(days.has(r.routineDayId) && exercises.has(r.exerciseId), 'routine_exercises', r.id)
  for (const w of workouts.values()) {
    const day = days.get(w.routineDayId ?? 0)
    const routine = routines.get(w.routineId ?? day?.routineId ?? 0)
    w.routineId = routine?.id ?? null
    w.routineDayId = day?.id ?? null
    if (legacy) {
      w.routineNameSnapshot ??= routine?.name ?? null
      w.routineDayNameSnapshot ??= day?.name ?? null
    }
  }
  for (const s of data.sets) {
    const exercise = exercises.get(s.exerciseId)
    check(!!exercise && workouts.has(s.workoutId), 'sets', s.id)
    if (exercise!.targetMuscle === 'Warmups') s.isWarmup = true
    if (s.distanceValue !== null) {
      s.distanceUnit ??= exercise!.defaultDistanceUnit
      s.distanceMeters ??= distanceToMeters(s.distanceValue, s.distanceUnit)
    } else if (s.distanceMeters !== null && s.distanceMeters > 0) {
      s.distanceUnit ??= 'MILES'
      s.distanceValue = metersToDistance(s.distanceMeters, s.distanceUnit)
    }
    if (s.distanceValue === null) s.distanceUnit = null
  }
  for (const inst of data.workout_exercise_instances) {
    check(
      exercises.has(inst.exerciseId) && workouts.has(inst.workoutId),
      'workout_exercise_instances',
      inst.id,
    )
    if (inst.routineExerciseId !== null && !routineExercises.has(inst.routineExerciseId))
      inst.routineExerciseId = null
  }
}

export async function readBackup(file: Blob | Uint8Array): Promise<ValidatedBackup> {
  if ((file instanceof Uint8Array ? file.length : file.size) > maxTotal + 1024 * 1024)
    throw new Error('Backup exceeds the supported archive size')
  const files = unpack(file instanceof Uint8Array ? file : new Uint8Array(await file.arrayBuffer()))
  const manifest = files.has('manifest.json')
    ? (JSON.parse(files.get('manifest.json')!) as unknown)
    : undefined
  if (manifest !== undefined && !isRecord(manifest))
    throw new Error('manifest.json must contain a JSON object')
  const version = manifest === undefined ? 1 : manifest.formatVersion
  if (version !== 1 && version !== 2)
    throw new Error('Unsupported backup format version ' + String(version))
  if (
    String(manifest?.weightUnit ?? 'lbs')
      .trim()
      .toLowerCase() !== 'lbs'
  )
    throw new Error('Unsupported backup storage weight unit. IronLog requires lbs.')
  const source = typeof manifest?.source === 'string' ? manifest.source : 'legacy'
  const data = emptySnapshot()
  const present = tableNames.filter((t) => files.has(t + '.csv'))
  if (!present.length) throw new Error('No IronLog CSV tables found')
  for (const table of present) {
    try {
      parseTable(table, files.get(table + '.csv')!, data, version === 1)
    } catch (err) {
      throw new Error(table + '.csv: ' + (err as Error).message)
    }
  }
  validateRelationships(data, version === 1)
  const counts = countsOf(data)
  if (isRecord(manifest?.counts))
    for (const table of tableNames) {
      const key = countKeys[table]
      if (manifest.counts[key] !== undefined && manifest.counts[key] !== counts[key])
        throw new Error('Manifest row count differs from ' + table + '.csv')
    }
  const preferences = files.has('preferences.json')
    ? decodePreferences(JSON.parse(files.get('preferences.json')!))
    : null
  const warnings = tableNames
    .filter((t) => !present.includes(t))
    .map((t) => 'Archive does not include ' + t + '.csv')
  const declared = manifest?.counts
  const completeCounts = isRecord(declared) && tableNames.every((table) => declared[countKeys[table]] === counts[countKeys[table]])
  const canReplace = version === 1 || (present.length === tableNames.length && completeCounts)
  if (!canReplace) warnings.push('Use Merge for this partial v2 archive.')
  return { version, source, data, counts, preferences, warnings, canReplace }
}
