// @vitest-environment node
import 'fake-indexeddb/auto'
import { afterAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { strToU8, unzipSync, zipSync, strFromU8 } from 'fflate'
import { db } from '../database'
import { defaultPreferences } from '../types'
import { csvRecords, writeCsv } from './csv'
import { prepareBackup, saveBackup } from './exporter'
import { readBackup } from './reader'
import { restoreBackup } from './importer'
import { columns, decodePreferences } from './format'

const archive = (entries: Record<string, string>) =>
  zipSync(Object.fromEntries(Object.entries(entries).map(([k, v]) => [k, strToU8(v)])))
const old = () =>
  archive({
    'exercises.csv':
      'id,name,targetMuscle,defaultDistanceUnit,isStarred,notes\n5,Barbell Bench Press,Chest,MILES,TRUE,"Cue, with\na line and ""quotes"""\n6,Walk,Warm-up,MILES,0,\n',
    'routines.csv': 'id,name,isActive\n7,A,1',
    'routine_days.csv': 'id,routineId,name\n8,7,Upper',
    'routine_exercises.csv': 'id,routineDayId,exerciseId,orderIndex\n9,8,5,0',
    'workouts.csv':
      'id,startTime,endTime,routineDayId,name\n20,1000,2000,8,Morning\n21,3000,,,Open A\n22,4000,,,Open B',
    'sets.csv':
      'workoutId,exerciseId,timestamp,weight,reps,distanceValue,distanceUnit,distanceMeters,supersetGroupId\n20,5,1100,185,8,,,,g\n20,5,1100,185,8,,,,g\n20,6,1150,,,1,MILES,,g\n21,6,3100,,,,,1609.344,',
    'workout_exercise_instances.csv':
      'workoutId,exerciseId,routineExerciseId,supersetGroupId\n20,5,9,g\n20,6,,g',
    '__MACOSX/sets.csv': 'junk',
    '.DS_Store': 'junk',
  })
beforeEach(async () => {
  await db.delete()
  await db.open()
  vi.unstubAllGlobals()
})
afterAll(() => db.delete())

describe('Android CSV and ZIP compatibility', () => {
  it('handles BOM, commas, escaped quotes, CRLF, blank lines and trailing empty columns', () => {
    const rows = [
      ...csvRecords('\ufeffname,notes,tail\r\n\r\n"Two, names","embedded\r\n""quote""",\n'),
    ]
    expect(rows).toEqual([
      ['name', 'notes', 'tail'],
      ['Two, names', 'embedded\r\n"quote"', ''],
    ])
    expect([
      ...csvRecords(writeCsv(rows[0], [{ name: rows[1][0], notes: rows[1][1], tail: null }])),
    ]).toEqual(rows)
    for (const bad of ['"unfinished', 'bad"quote', '"closed"text'])
      expect(() => [...csvRecords(bad)]).toThrow()
  })
  it('normalizes portable colors, fonts and physical inventory using Android bounds', () => {
    const prefs = decodePreferences({
      themeFont: 'custom',
      themeColor: 'custom',
      customThemeColor: 'broken',
      barWeight: 20000,
      loadingSides: 5,
      availablePlates: [0.001, 55.1155, 55.12, 1001],
      plateInventory: { '55.1155': 8, '55.12': 4, '1001': 4 },
    })
    expect(prefs).toMatchObject({
      themeFont: 'bebas_neue',
      themeColor: 'lime',
      barWeight: 10000,
      loadingSides: 5,
      availablePlates: [55.12],
      plateInventory: { '55.12': 4 },
    })
  })
  it('backfills v1 cardio, snapshots, warm-ups, streak and extra open workouts atomically', async () => {
    const backup = await readBackup(old())
    expect(backup.version).toBe(1)
    const report = await restoreBackup(backup, 'replace')
    expect(report.warnings.some((w) => w.includes('Closed extra'))).toBe(true)
    const workouts = await db.workouts.toArray(),
      sets = await db.sets.toArray()
    expect(workouts.filter((w) => w.endTime === null).map((w) => w.id)).toEqual([22])
    expect(workouts.find((w) => w.id === 21)?.endTime).toBe(3100)
    expect(workouts[0]).toMatchObject({
      routineId: 7,
      routineDayId: 8,
      startedFromRoutine: true,
      routineNameSnapshot: 'A',
      routineDayNameSnapshot: 'Upper',
    })
    expect(sets[2]).toMatchObject({ distanceMeters: 1609.344, isWarmup: true })
    expect(sets[3]).toMatchObject({ distanceValue: 1, distanceUnit: 'MILES' })
    expect((await db.preferences.get('default'))?.bestStreak).toBeGreaterThanOrEqual(1)
  })
  it('preserves identical sets, scoped supersets, foreign keys and starter customizations on repeated merge', async () => {
    const backup = await readBackup(old())
    const bench = (await db.exercises.toArray()).find((e) => e.name === 'Barbell Bench Press')!
    await restoreBackup(backup, 'merge')
    const setGroup = (await db.sets.toArray())[0].supersetGroupId
    expect(setGroup).toBeTruthy()
    expect(setGroup).not.toBe('g')
    expect((await db.workout_exercise_instances.toArray())[0].supersetGroupId).toBe(setGroup)
    expect((await db.exercises.get(bench.id))?.notes).toContain('a line and "quotes"')
    expect((await db.exercises.get(bench.id))?.isStarred).toBe(true)
    const again = await restoreBackup(backup, 'merge')
    expect(again.inserted).toBe(0)
    expect(await db.sets.count()).toBe(4)
    expect(await db.workout_exercise_instances.count()).toBe(2)
    expect(await db.routine_exercises.count()).toBe(1)
  })
  it('exports exact v2 headers and only portable preferences, preserving local metadata on replace', async () => {
    await restoreBackup(await readBackup(old()), 'replace')
    await db.preferences.update('default', {
      appIcon: 'swole_alligator',
      preferredWeightUnit: 'kg',
      barWeight: 44.0924,
    })
    const file = await prepareBackup()
    const bytes = new Uint8Array(await file.arrayBuffer()),
      entries = unzipSync(bytes)
    for (const [name, header] of Object.entries(columns))
      expect(strFromU8(entries[name + '.csv']).split('\n')[0]).toBe(header.join(','))
    const prefs = JSON.parse(strFromU8(entries['preferences.json']))
    expect(prefs).toMatchObject({ preferredWeightUnit: 'kg', barWeight: 44.0924 })
    expect(prefs).not.toHaveProperty('appIcon')
    expect(JSON.parse(strFromU8(entries['manifest.json']))).toMatchObject({
      formatVersion: 2,
      dbSchemaVersion: 15,
      source: 'pwa',
      weightUnit: 'lbs',
      counts: { sets: 4 },
    })
    await restoreBackup(await readBackup(bytes), 'replace')
    expect(await db.preferences.get('default')).toMatchObject({
      appIcon: 'swole_alligator',
      barWeight: 44.0924,
      preferredWeightUnit: 'kg',
    })
    expect((await db.sets.toArray())[0].weight).toBe(185)
  })
  it('rejects corrupt data, unsupported metadata and broken references before replacing the live database', async () => {
    const initial = await db.exercises.toArray()
    const badFiles = [
      archive({ 'readme.txt': 'hi' }),
      archive({ 'exercises.csv': 'id,name\n1,"unterminated' }),
      archive({ 'sets.csv': 'id,exerciseId\n1,1' }),
      archive({ 'sets.csv': 'workoutId,exerciseId,timestamp\n2,1,111' }),
      archive({ 'exercises.csv': 'id,name\n1,A', 'nested/exercises.csv': 'id,name\n2,B' }),
      archive({ 'manifest.json': '{"formatVersion":3}', 'exercises.csv': 'id,name' }),
      archive({
        'manifest.json': '{"formatVersion":2,"weightUnit":"kg"}',
        'exercises.csv': 'id,name',
      }),
      archive({
        'manifest.json': '{"formatVersion":2,"counts":{"sets":2}}',
        'sets.csv': 'id,workoutId,exerciseId,timestamp',
      }),
      old().slice(0, -12),
      new Uint8Array([1, 2, 3]),
    ]
    for (const bytes of badFiles)
      await expect(readBackup(bytes).then((b) => restoreBackup(b, 'replace'))).rejects.toThrow()
    expect(await db.exercises.toArray()).toEqual(initial)
  })
  it('requires full v2 columns and only permits merging a partial v2 archive', async () => {
    const initial = await db.exercises.toArray()
    const manifest = JSON.stringify({formatVersion:2,weightUnit:'lbs'})
    await expect(readBackup(archive({'manifest.json':manifest,'exercises.csv':'id,name'}))).rejects.toThrow('Missing columns')
    const partial = await readBackup(archive({'manifest.json':manifest,'exercises.csv':columns.exercises.join(',')}))
    expect(partial.canReplace).toBe(false)
    const allHeaders = Object.fromEntries(Object.entries(columns).map(([name,keys])=>[name+'.csv',keys.join(',')]))
    const uncounted = await readBackup(archive({...allHeaders,'manifest.json':manifest}))
    expect(uncounted.canReplace).toBe(false)
    await expect(restoreBackup(partial,'replace')).rejects.toThrow('all eight')
    expect(await db.exercises.toArray()).toEqual(initial)
    await expect(restoreBackup(partial,'merge')).resolves.toMatchObject({inserted:0})
  })
  it('rolls back every table if writing restored rows fails', async () => {
    const backup = await readBackup(old()),
      initial = await db.exercises.toArray()
    const fail = () => {
      throw new Error('test write failure')
    }
    db.sets.hook('creating', fail)
    try {
      await expect(restoreBackup(backup, 'replace')).rejects.toThrow()
    } finally {
      db.sets.hook('creating').unsubscribe(fail)
    }
    expect(await db.exercises.toArray()).toEqual(initial)
    expect(await db.workouts.count()).toBe(0)
  })
  it('keeps backup reminders on cancellation and retains workouts completed while sharing', async () => {
    await db.preferences.put({ ...defaultPreferences, workoutsSinceLastBackup: 3 })
    const f = await prepareBackup()
    const share = vi
      .fn()
      .mockRejectedValueOnce(new DOMException('Cancelled', 'AbortError'))
      .mockResolvedValue(undefined)
    vi.stubGlobal('navigator', { canShare: () => true, share })
    expect(await saveBackup(f)).toBe('cancelled')
    expect((await db.preferences.get('default'))?.lastBackupAtEpochMs).toBeNull()
    await db.preferences.update('default', { workoutsSinceLastBackup: 4 })
    expect(await saveBackup(f)).toBe('shared')
    expect((await db.preferences.get('default'))?.workoutsSinceLastBackup).toBe(1)
  })
})
