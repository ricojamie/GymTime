import { expect, it, describe } from 'vitest'
import type { Exercise } from '../db/types'
import { parseSmartLog, matchExercises } from './smartLogParser'
import { toLbs } from './units'

describe('offline Smart Log grammar', () => {
  it('expands counted sets and captures units, RPE and warm-up', () => {
    const result = parseSmartLog('log wu bench 3x8 at 185 lbs rpe 8','kg')
    expect(result?.exerciseQuery).toBe('bench')
    expect(result?.sets).toHaveLength(3)
    expect(result?.sets[1]).toMatchObject({weight:185,reps:8,isWarmup:true,rpe:8})
    expect(parseSmartLog('bench 3 × 10 60kg')?.sets[2].weight).toBeCloseTo(toLbs(60,'kg'))
    expect(parseSmartLog('Bench press at 185 x 8','kg')?.sets[0].weight).toBeCloseTo(toLbs(185,'kg'))
  })
  it('recognizes reps only, dumbbell lists, and explicit reps-first notation', () => {
    expect(parseSmartLog('pullups 3 sets of 10')?.sets.map(s=>s.reps)).toEqual([10,10,10])
    expect(parseSmartLog('db curl 35, 12, 10, 8')?.sets.map(s=>[s.weight,s.reps])).toEqual([[35,12],[35,10],[35,8]])
    expect(parseSmartLog('Incline DB press 32s, 10, 9, 8')?.sets).toHaveLength(3)
    expect(parseSmartLog('Incline press, 10 by 65 pounds')?.sets[0]).toMatchObject({reps:10,weight:65})
    expect(parseSmartLog('record Pull-ups 12')?.sets[0].reps).toBe(12)
  })
  it('keeps time, distance and calories distinct from reps and load', () => {
    expect(parseSmartLog('run 3.1 mi 24:30')?.sets[0]).toMatchObject({distanceValue:3.1,distanceUnit:'MILES',durationSeconds:1470})
    expect(parseSmartLog('row 1,000 meters, 8 min, 140 calories')?.sets[0]).toMatchObject({distanceValue:1000,distanceUnit:'METERS',durationSeconds:480,calories:140})
    expect(parseSmartLog('Sled push 180 lb for 40 yards')?.sets[0]).toMatchObject({weight:180,reps:null,distanceUnit:'YARDS'})
    expect(parseSmartLog('Farmer walk 70 lb for 45s')?.sets[0]).toMatchObject({weight:70,reps:null,durationSeconds:45})
    expect(parseSmartLog('plank 1h 2m 3s')?.sets[0]).toMatchObject({durationSeconds:3723,distanceValue:null})
    expect(parseSmartLog('plank 1h 2m')?.sets[0]).toMatchObject({durationSeconds:3720,distanceValue:null})
    expect(parseSmartLog('run .5 mi 5:00')?.sets[0].distanceValue).toBe(.5)
  })
  it('caps long queues, rejects zero sets, negatives and invalid clocks', () => {
    expect(parseSmartLog('bench 1000x3 at 100')?.sets).toHaveLength(20)
    expect(parseSmartLog('bench 0x8 at 100')).toBeNull()
    expect(parseSmartLog('bench -5 lb x 8')).toBeNull()
    expect(parseSmartLog('plank 1:90')).toBeNull()
    expect(parseSmartLog('plank 0:00')).toBeNull()
    expect(parseSmartLog('bench')).toBeNull()
  })
  it('resolves abbreviations and retains ambiguity when one name matches multiple exercises', () => {
    const exercises = ['Barbell Curl','Dumbbell Curl','Pull-Ups','Barbell Squat'].map((name,id)=>({id,name}) as Exercise)
    expect(matchExercises('pullups',exercises).map(e=>e.name)).toEqual(['Pull-Ups'])
    expect(matchExercises('db curl',exercises).map(e=>e.name)).toEqual(['Dumbbell Curl'])
    expect(matchExercises('curl',exercises).map(e=>e.name)).toEqual(expect.arrayContaining(['Barbell Curl','Dumbbell Curl']))
    expect(matchExercises('',exercises)).toEqual([])
  })
})
