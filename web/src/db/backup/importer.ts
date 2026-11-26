import { db, defaultPreferences } from '../database'
import { calculateBestStreak } from '../../domain/streakCalculator'
import { buildRestore } from './merge'
import { emptySnapshot, tableNames } from './format'
import type { ValidatedBackup } from './format'
import { readSnapshot } from './snapshot'

export type ImportReport = ReturnType<typeof buildRestore>['report']

export async function restoreBackup(
  backup: ValidatedBackup,
  mode: 'replace' | 'merge',
): Promise<ImportReport> {
  const replace = mode === 'replace'
  if (replace && !backup.canReplace) {
    throw new Error('Replace requires all eight v2 tables and their manifest counts. Choose Merge for this archive.')
  }
  return db.transaction('rw', db.tables, async () => {
    const local = replace ? emptySnapshot() : await readSnapshot()
    const { changes, report } = buildRestore(backup, local, replace)
    const current = (await db.preferences.get('default')) ?? defaultPreferences
    const prefs =
      replace && backup.preferences ? { ...current, ...backup.preferences } : { ...current }
    const best = calculateBestStreak(local.sets.concat(changes.sets), prefs.restDaysPerWeek)
    prefs.bestStreak = Math.max(
      best,
      backup.preferences?.bestStreak ?? 0,
      replace ? 0 : current.bestStreak,
    )
    if (replace) {
      for (const table of tableNames) await db.table(table).clear()
      // Imported history is already represented in the archive the user selected.
      prefs.lastBackupAtEpochMs = Date.now()
      prefs.workoutsSinceLastBackup = 0
    }
    for (const table of tableNames) {
      const rows = changes[table]
      for (let offset = 0; offset < rows.length; offset += 2000)
        await db.table(table).bulkPut(rows.slice(offset, offset + 2000))
    }
    await db.preferences.put(prefs)
    return report
  })
}
