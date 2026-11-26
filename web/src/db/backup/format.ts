import type {
  MuscleGroup,
  Exercise,
  Routine,
  RoutineDay,
  RoutineExercise,
  Workout,
  WorkoutSet,
  WorkoutExerciseInstance,
  Preferences,
} from '../types'
import { defaultPreferences } from '../types'

export interface Snapshot {
  muscle_groups: MuscleGroup[]
  exercises: Exercise[]
  routines: Routine[]
  routine_days: RoutineDay[]
  workouts: Workout[]
  routine_exercises: RoutineExercise[]
  sets: WorkoutSet[]
  workout_exercise_instances: WorkoutExerciseInstance[]
}
export const columns = {
  muscle_groups: ['name'],
  exercises: [
    'id',
    'name',
    'targetMuscle',
    'logType',
    'defaultDistanceUnit',
    'isCustom',
    'notes',
    'defaultRestSeconds',
    'isStarred',
    'repTarget',
  ],
  routines: ['id', 'name', 'isActive', 'nextDayOrderIndex'],
  routine_days: ['id', 'routineId', 'name', 'orderIndex'],
  workouts: [
    'id',
    'startTime',
    'endTime',
    'name',
    'note',
    'rating',
    'ratingNote',
    'routineDayId',
    'routineId',
    'routineNameSnapshot',
    'routineDayNameSnapshot',
    'startedFromRoutine',
  ],
  routine_exercises: [
    'id',
    'routineDayId',
    'exerciseId',
    'orderIndex',
    'targetSets',
    'targetRepsMin',
    'targetRepsMax',
    'targetRestSeconds',
    'notes',
    'supersetGroupId',
    'supersetOrderIndex',
  ],
  sets: [
    'id',
    'workoutId',
    'exerciseId',
    'weight',
    'calories',
    'reps',
    'rpe',
    'durationSeconds',
    'distanceValue',
    'distanceUnit',
    'distanceMeters',
    'isWarmup',
    'isComplete',
    'timestamp',
    'note',
    'supersetGroupId',
    'supersetOrderIndex',
  ],
  workout_exercise_instances: [
    'id',
    'workoutId',
    'exerciseId',
    'routineExerciseId',
    'orderIndex',
    'plannedSets',
    'repMin',
    'repMax',
    'restSeconds',
    'notes',
    'supersetGroupId',
    'supersetOrderIndex',
    'isSkipped',
    'addedDuringWorkout',
  ],
} as const satisfies { [K in keyof Snapshot]: readonly (keyof Snapshot[K][number])[] }
export type TableName = keyof Snapshot
export const tableNames = Object.keys(columns) as TableName[]
export const countKeys = {
  muscle_groups: 'muscleGroups',
  exercises: 'exercises',
  routines: 'routines',
  routine_days: 'routineDays',
  workouts: 'workouts',
  routine_exercises: 'routineExercises',
  sets: 'sets',
  workout_exercise_instances: 'workoutExerciseInstances',
} as const
export const countsOf = (data: Snapshot) =>
  Object.fromEntries(tableNames.map((key) => [countKeys[key], data[key].length])) as Record<
    (typeof countKeys)[TableName],
    number
  >
export const emptySnapshot = (): Snapshot => ({
  muscle_groups: [],
  exercises: [],
  routines: [],
  routine_days: [],
  routine_exercises: [],
  workouts: [],
  sets: [],
  workout_exercise_instances: [],
})
export const portableKeys = [
  'userName',
  'themeColor',
  'customThemeColor',
  'themeFont',
  'darkMode',
  'timerAutoStart',
  'timerAudioEnabled',
  'timerVibrateEnabled',
  'keepScreenOn',
  'restDaysPerWeek',
  'bestStreak',
  'monthlyReportEnabled',
  'barWeight',
  'loadingSides',
  'availablePlates',
  'usePlateInventory',
  'plateInventory',
  'preferredWeightUnit',
] as const
export type PortablePreferences = Pick<Preferences, (typeof portableKeys)[number]>
const pickPortable = (prefs: Preferences) =>
  Object.fromEntries(portableKeys.map((k) => [k, prefs[k]])) as PortablePreferences
export const isRecord = (obj: unknown): obj is Record<string, unknown> =>
  !!obj && typeof obj === 'object' && !Array.isArray(obj)
export const manifestFor = (data: Snapshot, now: number) => ({
  formatVersion: 2,
  dbSchemaVersion: 15,
  appVersion: '1.0.0-pwa',
  source: 'pwa',
  exportedAtEpochMs: now,
  weightUnit: 'lbs',
  counts: countsOf(data),
})
export type Counts = ReturnType<typeof countsOf>
export interface ValidatedBackup {
  data: Snapshot
  preferences: PortablePreferences | null
  version: number
  source: string
  counts: Counts
  canReplace: boolean
  warnings: string[]
}

export function decodePreferences(raw: unknown): PortablePreferences {
  if (!isRecord(raw)) throw new Error('preferences.json must contain a JSON object')
  const prefs = pickPortable(defaultPreferences)
  for (const key of ['userName', 'themeColor', 'themeFont'] as const) {
    const value = raw[key]
    if (typeof value === 'string' && value.trim()) prefs[key] = value.trim()
  }
  if (
    typeof raw.customThemeColor === 'string' &&
    /^#(?:[\da-f]{6}|[\da-f]{8})$/i.test(raw.customThemeColor)
  )
    prefs.customThemeColor = raw.customThemeColor
  for (const key of [
    'darkMode',
    'timerAutoStart',
    'timerAudioEnabled',
    'timerVibrateEnabled',
    'keepScreenOn',
    'monthlyReportEnabled',
    'usePlateInventory',
  ] as const) {
    if (typeof raw[key] === 'boolean') prefs[key] = raw[key]
  }
  for (const [key, min, max] of [
    ['restDaysPerWeek', 0, 7],
    ['bestStreak', 0, 2147483647],
    ['loadingSides', 1, 16],
  ] as const) {
    const value = raw[key]
    if (typeof value === 'number' && Number.isFinite(value))
      prefs[key] = Math.min(max, Math.max(min, Math.trunc(value)))
  }
  if (typeof raw.barWeight === 'number' && Number.isFinite(raw.barWeight) && raw.barWeight >= 0)
    prefs.barWeight = raw.barWeight
  const unit = String(raw.preferredWeightUnit ?? '')
    .trim()
    .toLowerCase()
  if (unit === 'kg' || unit === 'lbs') prefs.preferredWeightUnit = unit
  if (Array.isArray(raw.availablePlates))
    prefs.availablePlates = [
      ...new Set(
        raw.availablePlates.filter(
          (w): w is number => typeof w === 'number' && Number.isFinite(w) && w > 0,
        ),
      ),
    ].sort((a, b) => b - a)
  if (isRecord(raw.plateInventory)) {
    prefs.plateInventory = {}
    for (const [key, count] of Object.entries(raw.plateInventory)) {
      if (Number.isFinite(+key) && +key > 0 && typeof count === 'number' && Number.isFinite(count))
        prefs.plateInventory[String(+key)] = Math.min(100, Math.max(0, Math.trunc(count)))
    }
  }
  if (prefs.themeFont.toLowerCase() === 'custom') prefs.themeFont = 'bebas_neue'
  if (prefs.themeColor === 'custom' && !prefs.customThemeColor) prefs.themeColor = 'lime'
  prefs.barWeight = Math.min(10000, Math.max(0, prefs.barWeight))
  const plateWeight = (w: number) => (w >= 0.01 && w <= 1000 ? Math.round(w * 100) / 100 : null)
  const denominations = prefs.availablePlates.map(plateWeight).filter((w): w is number => w !== null)
  prefs.availablePlates = [...new Set(denominations)].sort((a, b) => b - a)
  const inventory: Record<string, number> = {}
  for (const [weight, count] of Object.entries(prefs.plateInventory)) {
    const w = plateWeight(Number(weight))
    if (w !== null) inventory[w] = Math.min(inventory[w] ?? count, count)
  }
  prefs.plateInventory = inventory
  return prefs
}

export const portableOnly = (prefs: Preferences) => decodePreferences(prefs)
