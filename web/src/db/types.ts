export const LOG_TYPES = [
  'WEIGHT_REPS', 'REPS_ONLY', 'DURATION', 'WEIGHT_DISTANCE',
  'DISTANCE_TIME', 'WEIGHT_TIME', 'CALORIES_TIME',
] as const
export type LogType = typeof LOG_TYPES[number]
export const DISTANCE_UNITS = ['METERS', 'KILOMETERS', 'YARDS', 'FEET', 'MILES', 'STEPS', 'FLOORS'] as const
export type DistanceUnit = typeof DISTANCE_UNITS[number]
export type WeightUnit = 'lbs' | 'kg'
export const WARMUP_MUSCLE_GROUP = 'Warmups'
export function normalizeMuscleName(name: string) {
  return /^warmups?$/i.test(name.replace(/[\s-]+/g, '')) ? WARMUP_MUSCLE_GROUP : name.trim()
}

export interface MuscleGroup { name: string }
export interface Exercise {
  id: number
  name: string
  targetMuscle: string
  logType: LogType
  defaultDistanceUnit: DistanceUnit
  isCustom: boolean
  notes: string | null
  defaultRestSeconds: number
  isStarred: boolean
  repTarget: number | null
}
export interface Routine {
  id: number
  name: string
  isActive: boolean
  nextDayOrderIndex: number
}
export interface RoutineDay {
  id: number
  routineId: number
  name: string
  orderIndex: number
}
export interface RoutineExercise {
  id: number
  routineDayId: number
  exerciseId: number
  orderIndex: number
  targetSets: number
  targetRepsMin: number | null
  targetRepsMax: number | null
  targetRestSeconds: number | null
  notes: string | null
  supersetGroupId: string | null
  supersetOrderIndex: number
}
export interface Workout {
  id: number
  startTime: number
  endTime: number | null
  name: string | null
  note: string | null
  rating: number | null
  ratingNote: string | null
  routineDayId: number | null
  routineId: number | null
  routineNameSnapshot: string | null
  routineDayNameSnapshot: string | null
  startedFromRoutine: boolean
}
export interface WorkoutSet {
  id: number
  workoutId: number
  exerciseId: number
  weight: number | null
  calories: number | null
  reps: number | null
  rpe: number | null
  durationSeconds: number | null
  distanceValue: number | null
  distanceUnit: DistanceUnit | null
  distanceMeters: number | null
  isWarmup: boolean
  isComplete: boolean
  timestamp: number
  note: string | null
  supersetGroupId: string | null
  supersetOrderIndex: number
}
export interface WorkoutExerciseInstance {
  id: number
  workoutId: number
  exerciseId: number
  routineExerciseId: number | null
  orderIndex: number
  plannedSets: number | null
  repMin: number | null
  repMax: number | null
  restSeconds: number | null
  notes: string | null
  supersetGroupId: string | null
  supersetOrderIndex: number
  isSkipped: boolean
  addedDuringWorkout: boolean
}
export interface PortablePreferences {
  userName: string
  themeColor: string
  customThemeColor: string | null
  themeFont: string
  darkMode: boolean
  timerAutoStart: boolean
  timerAudioEnabled: boolean
  timerVibrateEnabled: boolean
  keepScreenOn: boolean
  restDaysPerWeek: number
  bestStreak: number
  monthlyReportEnabled: boolean
  barWeight: number
  loadingSides: number
  availablePlates: number[]
  usePlateInventory: boolean
  plateInventory: Record<string, number>
  preferredWeightUnit: WeightUnit
}
export interface Preferences extends PortablePreferences {
  id: 'default'
  lastBackupAtEpochMs: number | null
  workoutsSinceLastBackup: number
  customFontBlob: ArrayBuffer | null
  customFontName: string | null
  appIcon: 'classic' | 'swole_alligator'
}
export const defaultPreferences: Preferences = {
  id: 'default', userName: 'Athlete', themeColor: 'lime', customThemeColor: null,
  themeFont: 'bebas_neue', darkMode: true, timerAutoStart: true, timerAudioEnabled: true,
  timerVibrateEnabled: true, keepScreenOn: false, restDaysPerWeek: 2, bestStreak: 0,
  monthlyReportEnabled: true, barWeight: 45, loadingSides: 2,
  availablePlates: [45, 35, 25, 15, 10, 5, 2.5], usePlateInventory: false,
  plateInventory: {}, preferredWeightUnit: 'lbs', lastBackupAtEpochMs: null,
  workoutsSinceLastBackup: 0, customFontBlob: null, customFontName: null, appIcon: 'classic',
}
