import type { Exercise, LogType } from './types'

export const starterMuscles = ['Back', 'Biceps', 'Chest', 'Core', 'Legs', 'Shoulders', 'Triceps', 'Cardio', 'Warmups']
const starters: [string, string, number, LogType?][] = [
  ['Barbell Bench Press', 'Chest', 180], ['Incline Dumbbell Press', 'Chest', 120],
  ['Dumbbell Flyes', 'Chest', 90], ['Push-Ups', 'Chest', 60, 'REPS_ONLY'],
  ['Deadlift', 'Back', 240], ['Barbell Row', 'Back', 150], ['Pull-Ups', 'Back', 120, 'REPS_ONLY'],
  ['Lat Pulldown', 'Back', 90], ['Dumbbell Row', 'Back', 90], ['Barbell Squat', 'Legs', 240],
  ['Leg Press', 'Legs', 180], ['Romanian Deadlift', 'Legs', 150], ['Leg Curl', 'Legs', 90],
  ['Leg Extension', 'Legs', 90], ['Calf Raise', 'Legs', 60], ['Overhead Press', 'Shoulders', 180],
  ['Dumbbell Lateral Raise', 'Shoulders', 60], ['Face Pull', 'Shoulders', 60],
  ['Barbell Curl', 'Biceps', 90], ['Hammer Curl', 'Biceps', 60], ['Tricep Dips', 'Triceps', 90, 'REPS_ONLY'],
  ['Skull Crusher', 'Triceps', 90], ['Plank', 'Core', 60, 'DURATION'],
  ['Hanging Leg Raise', 'Core', 60, 'REPS_ONLY'], ['Cable Crunch', 'Core', 60],
]
export const starterExercises: Omit<Exercise, 'id'>[] = starters.map(([name, targetMuscle, defaultRestSeconds, logType = 'WEIGHT_REPS']) => ({
  name, targetMuscle, defaultRestSeconds, logType, defaultDistanceUnit: 'MILES',
  isCustom: false, isStarred: false, repTarget: null, notes: null,
}))
