import { db, defaultPreferences } from '../database'
import { normalizeMuscleName, WARMUP_MUSCLE_GROUP, type Preferences } from '../types'

export function updatePreferences(patch: Partial<Omit<Preferences,'id'>>) {
  return db.transaction('rw', db.preferences, async () => {
    const current = await db.preferences.get('default') ?? defaultPreferences
    const next = {...current, ...patch, id:'default' as const}
    if (patch.userName !== undefined) next.userName = patch.userName.trim() || 'Athlete'
    if (!Number.isInteger(next.restDaysPerWeek) || next.restDaysPerWeek < 0 || next.restDaysPerWeek > 7) throw new Error('Choose 0 to 7 rest days.')
    if (!Number.isFinite(next.barWeight) || next.barWeight < 0) throw new Error('Bar weight must be zero or greater.')
    if (!Number.isInteger(next.loadingSides) || next.loadingSides < 1 || next.loadingSides > 16) throw new Error('Choose 1 to 16 loading sides.')
    if (next.availablePlates.some(w => !Number.isFinite(w) || w <= 0)) throw new Error('Plate weights must be positive.')
    if (Object.values(next.plateInventory).some(n => !Number.isInteger(n) || n < 0 || n > 100)) throw new Error('Enter 0 to 100 plates.')
    await db.preferences.put(next)
  })
}

function mutableName(name: string) {
  const clean = normalizeMuscleName(name)
  if (clean === WARMUP_MUSCLE_GROUP) throw new Error('Warmups is a protected body part.')
  if (clean.length < 2) throw new Error('Use at least two characters for the body part name.')
  return clean
}
export function addMuscle(name: string) {
  return db.transaction('rw', db.muscle_groups, async () => {
    const clean = mutableName(name)
    if (await db.muscle_groups.where('name').equalsIgnoreCase(clean).first()) throw new Error('This body part already exists.')
    await db.muscle_groups.add({name:clean})
  })
}
export function renameMuscle(oldName: string, name: string) {
  return db.transaction('rw', db.muscle_groups, db.exercises, async () => {
    const old = mutableName(oldName), clean = mutableName(name)
    if (old === clean) return
    if (!await db.muscle_groups.get(old)) throw new Error('Body part no longer exists.')
    const collision = await db.muscle_groups.where('name').equalsIgnoreCase(clean).first()
    if (collision && collision.name !== old) throw new Error('This body part already exists.')
    await db.exercises.where('targetMuscle').equalsIgnoreCase(old).modify({targetMuscle:clean})
    await db.muscle_groups.delete(old)
    await db.muscle_groups.put({name:clean})
  })
}
export function deleteMuscle(name: string, replaceWith?: string) {
  return db.transaction('rw', db.muscle_groups, db.exercises, db.sets, async () => {
    const clean = mutableName(name)
    const exercises = await db.exercises.where('targetMuscle').equalsIgnoreCase(clean).toArray()
    if (exercises.length) {
      if (await db.sets.where('exerciseId').anyOf(exercises.map(e => e.id)).first()) {
        throw new Error('This body part has logged sets. Rename it to keep the training history.')
      }
      if (replaceWith === undefined) throw new Error('Choose a replacement for the exercises using this body part.')
      const target = mutableName(replaceWith || 'Uncategorized')
      if (target.toLowerCase() === clean.toLowerCase()) throw new Error('Choose a different replacement.')
      const existing = await db.muscle_groups.where('name').equalsIgnoreCase(target).first()
      if (!existing) await db.muscle_groups.add({name:target})
      await db.exercises.where('targetMuscle').equalsIgnoreCase(clean).modify({targetMuscle: existing?.name ?? target})
    }
    await db.muscle_groups.delete(clean)
  })
}
