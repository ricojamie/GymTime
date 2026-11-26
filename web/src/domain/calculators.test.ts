import { describe, expect, it } from 'vitest'
import type { WorkoutSet } from '../db/types'
import { toLbs, fromLbs, formatWeight, distanceToMeters, isConvertibleToMeters } from './units'
import { calculateE1RM, calculateE10RM, roundToNearest2_5 } from './oneRepMax'
import { calculateStreak, calculateBestStreak } from './streakCalculator'
import { calculateWeeklyVolume } from './weeklyVolume'
import { emptyDraft, validateSet, normalizeDraft, afterLog } from './loggingValidation'
import { replayRecords, paretoBest } from './records'

const set = (s: Partial<WorkoutSet> = {}): WorkoutSet => ({...emptyDraft(), id: 1, exerciseId: 1, workoutId: 1,
  weight: 100, reps: 5, isComplete: true, distanceMeters: null, timestamp: 1, supersetGroupId: null, supersetOrderIndex: 0, ...s})
const day = (d: number) => new Date(2026, 2, d, 12).getTime()

describe('unit boundaries and Epley estimates', () => {
  it('rounds only display values and keeps metric equipment invertible', () => {
    for (const value of [1.25, 2.5, 5, 10, 15, 20, 25]) {
      expect(fromLbs(toLbs(value, 'kg'), 'kg')).toBeCloseTo(value, 8)
      expect(formatWeight(toLbs(value, 'kg'), 'kg')).toBe(String(value))
    }
    expect(toLbs(20, 'kg')).toBeCloseTo(44.0924)
    expect(formatWeight(135, 'lbs')).toBe('135')
  })
  it('keeps count units separate from physical distance', () => {
    expect(distanceToMeters(3.1, 'MILES')).toBeCloseTo(4988.9664)
    expect(distanceToMeters(1,'KILOMETERS')).toBe(1000)
    expect(distanceToMeters(1,'YARDS')).toBe(0.9144)
    expect(distanceToMeters(1,'FEET')).toBe(0.3048)
    expect(distanceToMeters(100,'STEPS')).toBeNull()
    expect(isConvertibleToMeters('FLOORS')).toBe(false)
  })
  it.each([[0,5],[NaN,5],[Infinity,5],[100,0],[100,16],[100,1.1],[Number.MAX_VALUE,15]])('rejects %s × %s', (weight,reps) => {
    expect(calculateE1RM(weight,reps)).toBeNull()
    expect(calculateE10RM(weight,reps)).toBeNull()
  })
  it('matches Android Epley and nearest load', () => {
    expect(calculateE1RM(100,15)).toBe(150)
    expect(calculateE10RM(100,10)).toBeCloseTo(100, 10)
    expect(roundToNearest2_5(103.75)).toBe(105)
  })
})

describe('Sunday-start Iron Streak', () => {
  const sessions = [5,6,7,8,8,10].map(d => set({timestamp: day(d)}))
  it('starts the clock on the first training day, deduplicates and ignores warmups', () => {
    expect(calculateStreak(sessions,2,day(5))).toMatchObject({streakDays:1,skipsRemaining:2,state:'ACTIVE'})
    expect(calculateStreak([...sessions,set({timestamp:day(9),isWarmup:true})],2,day(10))).toMatchObject({streakDays:5,skipsRemaining:1})
    expect(calculateStreak(sessions,2,day(8)).nextResetDate.getDate()).toBe(15)
  })
  it('breaks once the weekly allowance is exceeded', () => {
    expect(calculateStreak(sessions,2,day(11))).toMatchObject({state:'RESTING',streakDays:5,skipsRemaining:0})
    expect(calculateStreak(sessions,2,day(12))).toMatchObject({state:'BROKEN',streakDays:0,brokeToday:true})
    expect(calculateStreak([...sessions,set({timestamp:day(15)})],2,day(15))).toMatchObject({state:'ACTIVE',streakDays:1})
    expect(calculateBestStreak(sessions,2,day(15))).toBe(5)
  })
  it('reconciles best to daily peaks across gaps, daylight saving and all rest-day settings', () => {
    for (let allowance = 0; allowance <= 7; allowance++) {
      const days = [1,3,4,5,6,7,9,10,28,29,30].map(d => set({timestamp:day(d)}))
      let best = 0
      for (let d = 1; d <= 31; d++) best = Math.max(best, calculateStreak(days,allowance,day(d)).streakDays)
      expect(calculateBestStreak(days,allowance,day(31))).toBe(best)
    }
    expect(calculateStreak([], 2, day(1))).toMatchObject({state:'RESTING',streakDays:0,skipsRemaining:2})
  })
})

describe('weighted volume', () => {
  it('uses local Sunday boundaries, excludes incomplete/warmup/future sets and cardio', () => {
    const sets = [set({timestamp:day(7)}),set({timestamp:day(8),weight:125}),set({timestamp:day(9),isComplete:false}),
      set({timestamp:day(10),isWarmup:true}),set({timestamp:day(11),reps:null}),set({timestamp:day(13)})]
    expect(calculateWeeklyVolume(sets, day(12))).toEqual({currentWeek:625,lastWeek:500,progressPercent:1.25,hasOverflowed:true})
    expect(calculateWeeklyVolume([], day(12)).progressPercent).toBe(0)
  })
})

describe('set logging validation and retention', () => {
  it('allows zero load and rejects missing, fractional or negative required fields', () => {
    expect(validateSet('WEIGHT_REPS', set({weight:0}))).toBeNull()
    expect(validateSet('WEIGHT_REPS', set({weight:-1}))).toBeTruthy()
    expect(validateSet('REPS_ONLY', set({reps:3.5}))).toBeTruthy()
    expect(validateSet('DURATION', set({durationSeconds:0}))).toBeTruthy()
    expect(validateSet('CALORIES_TIME', set({durationSeconds:10,calories:0}))).toBeTruthy()
    expect(validateSet('WEIGHT_TIME', set({durationSeconds:10,rpe:11}))).toBeTruthy()
    expect(validateSet('DISTANCE_TIME', set({distanceValue:1,distanceUnit:'MILES',durationSeconds:90}))).toBeNull()
    expect(validateSet('WEIGHT_DISTANCE', set({distanceValue:2}))).toBeTruthy()
  })
  it('retains metrics, resets extras, and clears incompatible fields', () => {
    const draft = set({rpe:8,isWarmup:true,note:' effort ',calories:10})
    expect(afterLog(draft)).toMatchObject({weight:100,reps:5,rpe:null,note:null,isWarmup:false})
    expect(afterLog(draft,true).isWarmup).toBe(true)
    expect(normalizeDraft('REPS_ONLY',draft)).toMatchObject({weight:null,reps:5,calories:null,note:'effort'})
    expect(draft.rpe).toBe(8)
  })
})

describe('chronological record replay', () => {
  it('uses ID order, excludes warmups and respects high-rep dominance even without E1RM', () => {
    const sets = [set({id:2,reps:5,weight:95}),set({id:1,reps:20,weight:100}),set({id:3,reps:5,weight:110}),
      set({id:4,reps:1,weight:120,isWarmup:true})]
    const events = replayRecords(sets,'WEIGHT_REPS')
    expect(events.map(e => e.setId)).toEqual([1,3])
    expect(events[0].benchmark).toBe(true)
    expect(events[1].labels).toEqual(['Heaviest set','Best at 5 reps','Estimated 1RM'])
    expect(paretoBest(sets).map(s=>s.id)).toEqual([3,1])
  })
  it('awards REP, duration, and distance tiebreaks and refuses mixed step and distance measurements', () => {
    expect(replayRecords([set({id:1,durationSeconds:10}),set({id:2,durationSeconds:15})],'WEIGHT_TIME')).toHaveLength(2)
    expect(replayRecords([set({id:1,distanceValue:100,distanceUnit:'YARDS'}),set({id:2,distanceValue:200,distanceUnit:'YARDS'})],'WEIGHT_DISTANCE')).toHaveLength(2)
    const runs = [set({id:1,distanceValue:10,distanceUnit:'STEPS',distanceMeters:9999}),
      set({id:2,distanceValue:1,distanceUnit:'MILES'}),set({id:3,distanceValue:2,distanceUnit:'KILOMETERS'})]
    expect(replayRecords(runs,'DISTANCE_TIME').map(e=>e.setId)).toEqual([2,3])
    expect(replayRecords(runs,'DISTANCE_TIME','STEPS').map(e=>e.setId)).toEqual([1])
  })
})
