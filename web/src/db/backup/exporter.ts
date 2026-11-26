import { strToU8, zip } from 'fflate'
import { db, defaultPreferences } from '../database'
import { readSnapshot } from './snapshot'
import { columns, manifestFor, portableOnly, tableNames } from './format'
import { writeCsv } from './csv'

type BackupResult = 'shared' | 'downloaded' | 'cancelled'
interface Capture {
  at: number
  workouts: number
  previousBackupAt: number | null
  finished: Map<number, number | null>
}
const captured = new WeakMap<File, Capture>()

export async function prepareBackup(): Promise<File> {
  const { data, preferences, now } = await db.transaction('r', db.tables, async () => ({
    data: await readSnapshot(),
    preferences: (await db.preferences.get('default')) ?? defaultPreferences,
    now: Date.now(),
  }))
  const entries: Record<string, Uint8Array> = {
    'manifest.json': strToU8(JSON.stringify(manifestFor(data, now), null, 2) + '\n'),
    'preferences.json': strToU8(JSON.stringify(portableOnly(preferences), null, 2) + '\n'),
  }
  for (const table of tableNames)
    entries[table + '.csv'] = strToU8(writeCsv(columns[table], data[table]))
  const bytes = await new Promise<Uint8Array>((resolve, reject) =>
    zip(entries, { level: 5 }, (error, result) => (error ? reject(error) : resolve(result))),
  )
  const date = new Date(now)
  const day = [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, '0'),
    String(date.getDate()).padStart(2, '0'),
  ].join('-')
  const file = new File([new Uint8Array(bytes).buffer], 'ironlog_backup_' + day + '.zip', {
    type: 'application/zip',
    lastModified: now,
  })
  captured.set(file, {
    at: now,
    workouts: preferences.workoutsSinceLastBackup,
    previousBackupAt: preferences.lastBackupAtEpochMs,
    finished: new Map(data.workouts.map((w) => [w.id, w.endTime])),
  })
  return file
}

/** Pass a prebuilt File on iOS so no asynchronous step occurs before the share call. */
export async function saveBackup(prepared?: File): Promise<BackupResult> {
  const file = prepared ?? (await prepareBackup())
  let result: BackupResult
  if (typeof navigator.share === 'function' && navigator.canShare?.({ files: [file] })) {
    try {
      await navigator.share({ files: [file], title: 'IronLog Backup' })
      result = 'shared'
    } catch (error) {
      if ((error as Error).name === 'AbortError') return 'cancelled'
      throw error
    }
  } else {
    const href = URL.createObjectURL(file)
    const link = document.createElement('a')
    link.href = href
    link.download = file.name
    document.body.append(link)
    link.click()
    link.remove()
    setTimeout(() => URL.revokeObjectURL(href), 60000)
    result = 'downloaded'
  }
  const snapshot = captured.get(file)
  if (snapshot)
    await db.transaction('rw', db.preferences, db.workouts, async () => {
      const pref = await db.preferences.get('default')
      if (!pref || (pref.lastBackupAtEpochMs ?? 0) > snapshot.at) return
      let remaining = Math.max(0, pref.workoutsSinceLastBackup - snapshot.workouts)
      if (pref.lastBackupAtEpochMs !== snapshot.previousBackupAt) {
        // A second share may finish while this sheet is open. Its reset has changed
        // the counter baseline, so compare the saved workout revision directly.
        const finished = await db.workouts.where('endTime').aboveOrEqual(snapshot.at).toArray()
        remaining = Math.min(
          pref.workoutsSinceLastBackup,
          finished.filter((w) => snapshot.finished.get(w.id) !== w.endTime).length,
        )
      }
      await db.preferences.update('default', {
        lastBackupAtEpochMs: snapshot.at,
        workoutsSinceLastBackup: remaining,
      })
    })
  return result
}
