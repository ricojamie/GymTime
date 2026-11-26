import { afterEach, beforeEach, expect, it } from 'vitest'
import { db } from '../db/database'
import { finishWorkout, logSet, startWorkout } from '../db/repositories'
import { addWorkoutToRoutine } from './sessionData'
import { describeWorkout, setText } from './presentation'

beforeEach(async () => { await db.open() })
afterEach(async () => { await db.delete() })

it('converts a finished session into a day, keeps the original history, and preserves the name on replacement', async () => {
  const exercise = (await db.exercises.toArray())[0]
  const workoutId = await startWorkout({})
  await logSet({ exerciseId:exercise.id, workoutId, weight:40, reps:10, isWarmup:true })
  const setId = await logSet({ exerciseId:exercise.id, workoutId, weight:88.1848, reps:6 })
  await finishWorkout(workoutId)
  const routineId = await db.routines.add({ name:'A/B', isActive:false, nextDayOrderIndex:0 })
  const dayId = await addWorkoutToRoutine(workoutId, routineId, null, 'Upper')
  const plan = await db.routine_exercises.where('routineDayId').equals(dayId).toArray()
  expect(plan).toHaveLength(1)
  expect(plan[0]).toMatchObject({exerciseId:exercise.id, targetSets:1, targetRepsMin:6, targetRepsMax:6})
  await addWorkoutToRoutine(workoutId, routineId, dayId, 'Never overwrites day name')
  expect((await db.routine_days.get(dayId))?.name).toBe('Upper')
  expect(await db.routine_exercises.where('routineDayId').equals(dayId).count()).toBe(1)
  const workout = await db.workouts.get(workoutId)
  const sets = await db.sets.where('workoutId').equals(workoutId).toArray()
  expect(workout?.endTime).toEqual(expect.any(Number))
  expect(sets).toHaveLength(2)
  expect(describeWorkout(workout!, sets, new Map([[exercise.id, exercise]]))).toMatchObject({
    sets:1, exercises:1, muscles:[exercise.targetMuscle], volume:88.1848 * 6,
  })
  expect(setText(sets.find(s => s.id === setId)!, 'kg')).toBe('40 kg · 6 reps')
})

it('preserves the planned day when an empty session is offered as its replacement', async () => {
  const routineId = await db.routines.add({name:'Weekly',isActive:false,nextDayOrderIndex:0})
  const dayId = await db.routine_days.add({routineId,name:'Lower',orderIndex:0})
  const exercise = (await db.exercises.toArray())[0]
  const planId = await db.routine_exercises.add({routineDayId:dayId,exerciseId:exercise.id,orderIndex:0,targetSets:4,targetRepsMin:3,targetRepsMax:5,targetRestSeconds:null,notes:null,supersetGroupId:null,supersetOrderIndex:0})
  const workoutId = await startWorkout({})
  await expect(addWorkoutToRoutine(workoutId,routineId,dayId,'Replacement')).rejects.toThrow('no exercises')
  expect((await db.routine_exercises.get(planId))?.targetSets).toBe(4)
})
