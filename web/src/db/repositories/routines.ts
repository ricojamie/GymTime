import { db } from '../database'

export function setActiveRoutine(id: number | null) {
  return db.transaction('rw', db.routines, async () => {
    if (id !== null && !await db.routines.get(id)) throw new Error('Routine no longer exists.')
    await db.routines.filter(r => r.isActive && r.id !== id).modify({isActive:false})
    if (id !== null) await db.routines.update(id,{isActive:true})
  })
}
export function deleteRoutine(id: number) {
  return db.transaction('rw', db.routines, db.routine_days, db.routine_exercises, async () => {
    const days = await db.routine_days.where('routineId').equals(id).primaryKeys()
    await db.routine_exercises.where('routineDayId').anyOf(days).delete()
    await db.routine_days.bulkDelete(days)
    await db.routines.delete(id)
  })
}
