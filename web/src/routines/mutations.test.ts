import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import 'fake-indexeddb/auto'
import { db } from '../db/database'
import { createRoutine, duplicateDay, duplicateRoutine, moveDay, newPlanRow, planBlocks, removeDay, saveDay, setNextDay } from './mutations'

beforeEach(async () => { await db.delete(); await db.open() })
afterEach(async () => { await db.delete() })

describe('routine plans', () => {
  it('keeps the current day intact after invalid edits', async () => {
    const r = await createRoutine('Upper / lower')
    const day = await saveDay(r,null,'Upper',[newPlanRow(1)])
    await expect(saveDay(r,day,'Bad',[newPlanRow(12345)])).rejects.toThrow('deleted')
    await expect(saveDay(r,day,'Bad',[{...newPlanRow(2),targetRepsMin:10,targetRepsMax:5}])).rejects.toThrow('Minimum')
    expect((await db.routine_days.get(day))?.name).toBe('Upper')
    expect((await db.routine_exercises.toArray()).map(row => row.exerciseId)).toEqual([1])
  })
  it('keeps the next day stable across reordering and advances when removed',async () => {
    const r = await createRoutine('Three days')
    const a = await saveDay(r,null,'A',[newPlanRow(1)])
    const b = await saveDay(r,null,'B',[newPlanRow(2)])
    const c = await saveDay(r,null,'C',[newPlanRow(3)])
    await setNextDay(r,b)
    await moveDay(r,b,-1)
    expect((await db.routines.get(r))?.nextDayOrderIndex).toBe((await db.routine_days.get(b))?.orderIndex)
    await removeDay(r,b)
    expect((await db.routines.get(r))?.nextDayOrderIndex).toBe((await db.routine_days.get(a))?.orderIndex)
    expect(await db.routine_exercises.where('routineDayId').equals(b).count()).toBe(0)
    expect(await db.routine_days.get(c)).toBeDefined()
  })
  it('copies prescriptions with independent superset IDs and ordered blocks',async () => {
    const r = await createRoutine('PPL')
    const pair = [1,2].map((id,index) => ({...newPlanRow(id),supersetGroupId:'pair',supersetOrderIndex:index,targetRestSeconds:45}))
    const d = await saveDay(r,null,'Push',[...pair,newPlanRow(3)])
    const duplicate = await duplicateRoutine(r)
    const day = (await db.routine_days.where('routineId').equals(duplicate).first())!
    const rows = await db.routine_exercises.where('routineDayId').equals(day.id).sortBy('orderIndex')
    expect(planBlocks(rows).map(block => block.map(row => row.exerciseId))).toEqual([[1,2],[3]])
    expect(rows[0].supersetGroupId).not.toBe('pair')
    expect(rows[1].targetRestSeconds).toBe(45)
    const duplicatedDay = await duplicateDay(r,d)
    const copyRows = await db.routine_exercises.where('routineDayId').equals(duplicatedDay).sortBy('orderIndex')
    expect(copyRows[0].supersetGroupId).not.toBe(rows[0].supersetGroupId)
  })
  it('enforces routine and day caps inside write transactions', async () => {
    const results = await Promise.allSettled(Array.from({length:11},(_,i) => createRoutine('R' + i)))
    expect(results.filter(row => row.status === 'fulfilled')).toHaveLength(10)
    const [r] = await db.routines.toArray()
    const days = await Promise.allSettled(Array.from({length:11},(_,i) => saveDay(r.id,null,'D'+i,[newPlanRow(1)])))
    expect(days.filter(row => row.status === 'fulfilled')).toHaveLength(10)
  })
})
