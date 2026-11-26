import type { DBCore, Middleware } from 'dexie'
import { normalizeMuscleName, WARMUP_MUSCLE_GROUP, type Exercise, type WorkoutSet } from './types'

// SQLite's category triggers also apply to bulk restores and direct table updates.
// Apply them below Dexie's entity API so a new caller cannot bypass the same rules.
export const warmupRules: Middleware<DBCore> = {
  stack: 'dbcore', name: 'warmup-rules', level: 10,
  create(core) {
    return {
      transaction(stores, mode, options) {
        let scope = stores
        if (mode === 'readwrite' && (stores.includes('sets') || stores.includes('exercises'))) {
          scope = [...new Set([...stores, 'sets', 'exercises'])]
        }
        return core.transaction(scope, mode, options)
      },
      table(name) {
        const table = core.table(name)
        if (!['sets','exercises','muscle_groups'].includes(name)) return table
        return {
          ...table,
          async mutate(request) {
            if (request.type !== 'add' && request.type !== 'put') return table.mutate(request)
            const trans = request.trans
            if (name === 'muscle_groups') {
              return table.mutate({...request, values: request.values.map(g => ({...g, name:normalizeMuscleName(g.name)}))})
            }
            if (name === 'sets') {
              const rows = request.values as readonly WorkoutSet[]
              const ids = [...new Set(rows.filter(s => !s.isWarmup).map(s => s.exerciseId))]
              if (!ids.length) return table.mutate(request)
              const exercises: (Exercise | undefined)[] = await core.table('exercises').getMany({trans, keys:ids})
              const warmups = new Set(exercises.flatMap(e => e?.targetMuscle === WARMUP_MUSCLE_GROUP ? [e.id] : []))
              if (!warmups.size) return table.mutate(request)
              return table.mutate({...request, values:rows.map(s => warmups.has(s.exerciseId) ? {...s, isWarmup:true} : s)})
            }
            const values = (request.values as readonly Exercise[]).map(e => ({...e,targetMuscle:normalizeMuscleName(e.targetMuscle)}))
            const result = await table.mutate({...request,values})
            if (request.type === 'add') return result
            const sets = core.table('sets')
            const index = sets.schema.getIndexByKeyPath('exerciseId')!
            for (const [i, exercise] of values.entries()) {
              if (result.failures[i] || exercise.targetMuscle !== WARMUP_MUSCLE_GROUP || !exercise.id) continue
              const {result: rows} = await sets.query({trans, values:true,
                query:{index,range:{type:1,lower:exercise.id,upper:exercise.id}}})
              const formerlyWorking = (rows as WorkoutSet[]).filter(s => !s.isWarmup)
              if (!formerlyWorking.length) continue
              const repaired = await sets.mutate({trans,type:'put',values:formerlyWorking.map(s => ({...s,isWarmup:true}))})
              if (repaired.numFailures) throw Object.values(repaired.failures)[0]
            }
            return result
          },
        }
      },
    }
  },
}
