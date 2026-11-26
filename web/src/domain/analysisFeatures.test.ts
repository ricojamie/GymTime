import { describe, expect, it } from 'vitest'
import type { Exercise, Workout, WorkoutSet } from '../db/types'
import { calculateTrainingInsights, sampleChart } from './trainingInsights'
import { calculateStrengthMomentum } from './strengthMomentum'
import { calculateRoutineStats } from './routineStats'
import { calculateMonthlyReport } from './monthlyReport'
import { buildWorkoutShare, formatShareSet, formatWorkoutShare } from './workoutShare'
import { DAY } from './analyticsCore'

const now = +new Date(2026, 10, 26, 12)
const lift: Exercise = {id:1,name:'Bench',targetMuscle:'Chest',logType:'WEIGHT_REPS',defaultDistanceUnit:'MILES',
  isCustom:false,notes:null,defaultRestSeconds:90,isStarred:true,repTarget:null}
function exercise(overrides: Partial<Exercise>): Exercise { return {...lift, ...overrides} }
function workout(id:number,start:number, overrides:Partial<Workout> = {}): Workout {
  return {id,startTime:start,endTime:start + 60_000,name:'Push',note:null,rating:null,ratingNote:null,
    routineId:1,routineDayId:null,routineDayNameSnapshot:null,routineNameSnapshot:null,startedFromRoutine:true,...overrides}
}
function set(id:number,w:Workout,overrides:Partial<WorkoutSet>={}): WorkoutSet {
  return {id,workoutId:w.id,exerciseId:1,weight:100,reps:5,isWarmup:false,isComplete:true,distanceUnit:null,
    distanceMeters:null,distanceValue:null,note:null,supersetGroupId:null,supersetOrderIndex:0,timestamp:w.startTime,
    calories:null,durationSeconds:null,rpe:null,...overrides}
}
const series = (weights:number[],offset=0) => {
  const workouts = weights.map((_,i) => workout(i+1,now-(weights.length-i+offset)*DAY))
  return {exercises:[lift],workouts,sets:weights.map((weight,i) => set(i+1,workouts[i],{weight}))}
}

describe('training insights',() => {
  it('compares adjacent half-open windows, excludes ongoing, future, invalid and warm-up sets',() => {
    const workouts = [workout(1,now - 28*DAY),workout(2,now - 56*DAY),workout(3,now-DAY,{endTime:null}),workout(4,now + DAY)]
    const sets = workouts.map((w,i) => set(i+1,w))
    sets.push(set(5,workouts[0],{exerciseId:2}),set(6,workouts[0],{weight:NaN}),set(7,workouts[0],{isWarmup:true}),set(8,workouts[0],{isComplete:false}))
    const d = calculateTrainingInsights({workouts,sets,exercises:[lift,exercise({id:2,targetMuscle:'Warm-up'})],muscleNames:['Grip']},28,now)
    expect(d.cadence).toMatchObject({currentWorkouts:1,previousWorkouts:1,currentWorkingSets:1,previousWorkingSets:1})
    expect(d.muscles.find(m => m.name === 'Grip')?.currentWorkingSets).toBe(0)
    expect(d.cadence.weeks.reduce((a,w) => a + w.workouts,0)).toBe(1)
    expect(d.cadence.weeks[0].isPartialWeek).toBe(true)
  })
  it('samples only plotted points and keeps complete history',() => {
    const workouts = Array.from({length:100},(_,i) => workout(i+1,now-(100-i)*120000))
    const d = calculateTrainingInsights({workouts,sets:workouts.map((w,i) => set(i+1,w,{weight:100+i})),exercises:[lift]},28,now)
    const e = d.exercises[0]
    expect(e.history).toHaveLength(100)
    expect(e.progress.chartSessionCount).toBe(100)
    expect(e.progress.chartPoints).toHaveLength(80)
    expect(e.progress.chartPoints.map(p => p.workoutId)).toEqual(sampleChart(workouts.map(w => w.id)))
    expect(e.progress.chartPoints[0].workoutId).toBe(1)
    expect(e.progress.chartPoints[79].workoutId).toBe(100)
    expect(d.recentRecords).toHaveLength(6)
    expect(new Set(d.recentRecords.map(e => e.set.id)).size).toBe(6)
    expect(d.observations).toHaveLength(2)
  })
  it('keeps floors and steps independent from meters and unifies Core/Abs even across exercises',() => {
    const workouts = [workout(1,now-2*DAY),workout(2,now-DAY)]
    const sets = [set(1,workouts[0],{distanceValue:2,distanceUnit:'MILES',durationSeconds:150}),
      set(2,workouts[1],{distanceValue:301,distanceUnit:'STEPS',durationSeconds:100}),
      set(3,workouts[1],{exerciseId:2})]
    const exercises = [exercise({targetMuscle:'Core',logType:'DISTANCE_TIME'}),exercise({id:2,targetMuscle:'Abs'})]
    const d = calculateTrainingInsights({workouts,sets,exercises},28,now)
    const progress = d.exercises.find(e => e.id === 1)!.progress
    expect(progress.metric?.unit).toBe('steps')
    expect(progress.chartPoints.map(p => p.value)).toEqual([301])
    expect(progress.comparison.status).toBe('NO_COMPARABLE_DATA')
    expect(d.muscles.find(m => m.name === 'Abs')!.currentWorkingSets).toBe(3)
  })
  it('shows load for high-rep weighted sets and reps when there is no load',() => {
    const a = series([150])
    a.sets[0].reps = 20
    expect(calculateTrainingInsights(a,28,now).exercises[0].progress.metric?.kind).toBe('WEIGHT')
    a.sets[0].weight = 0
    expect(calculateTrainingInsights(a,28,now).exercises[0].progress.metric?.kind).toBe('REPS')
  })
})
describe('strength and routine trends',() => {
  it('uses matched session medians, caps muscle changes and requires four sessions',() => {
    const result = calculateStrengthMomentum(series([100,100,100,200,200,200]),now)
    const chest = result.muscles.find(m => m.muscle === 'Chest')!
    expect(chest).toMatchObject({percentChange:20,direction:'STRONG_UP',status:'READY'})
    expect(chest.contributingExercises[0]).toMatchObject({percentChange:100,recentSessionCount:3,baselineSessionCount:3})
    expect(calculateStrengthMomentum(series([100,105,110]),now).muscles[0].status).toBe('BUILDING_BASELINE')
    expect(calculateStrengthMomentum(series([100,105,110,112],43),now).muscles[0].status).toBe('STALE')
  })
  it('requires a 21-day routine baseline, excludes anomalous durations, averages recent frequency over four weeks',() => {
    const s = series([100,110,120])
    s.workouts[0].startTime -= 30*DAY; s.workouts[0].endTime = s.workouts[0].startTime+400*60000
    s.sets[0].timestamp=s.workouts[0].startTime
    const r=calculateRoutineStats(s,1,now)
    expect(r).toMatchObject({timesCompleted:3,avgDurationMinutes:1,workoutsPerWeekRecent:0.5,totalVolume:1650})
    expect(r.exerciseTrends[0].e1rmDelta).toBeCloseTo(20*(1+5/30))
    expect(calculateRoutineStats(series([100,110]),1,now).exerciseTrends[0].e1rmDelta).toBeNull()
  })
})
describe('monthly report and Android share text',() => {
  it('uses the last complete calendar month, previous month, historical PR baselines and rated sessions',() => {
    const workouts = [workout(1,+new Date(2026,8,30,12)),workout(2,+new Date(2026,9,1,10),{rating:4}),workout(3,+new Date(2026,10,1))]
    const sets = workouts.map((w,i) => set(i+1,w,{weight:100+i*10}))
    const r=calculateMonthlyReport({workouts,sets,exercises:[lift]},now)
    expect(r).toMatchObject({workoutCount:1,previousWorkoutCount:1,totalVolume:550,previousTotalVolume:500,volumeChangePercent:10,averageRating:4})
    expect(r.newPRs.map(p => p.weight)).toEqual([110])
    expect(r.topMuscles).toEqual([{muscle:'Chest',setCount:1}])
  })
  it('exactly matches Android punctuation, legacy brand, Pareto suppression and warmup omission',() => {
    const w=workout(1,+new Date(2026,10,5,12))
    const sets=[set(1,w,{weight:60,reps:5}),set(2,w,{weight:65,reps:6}),set(3,w,{weight:45,reps:20,isWarmup:true})]
    const share=buildWorkoutShare({workouts:[w],sets,exercises:[lift]},1)!
    expect(formatWorkoutShare(share,'en-US')).toBe('GymTime — 5 Nov 2026\nDuration: 1m  |  Volume: 690 lbs  |  2 sets across 1 exercises\n\nBench\n  60 lbs × 5\n  65 lbs × 6  🏆 PR\n\nShared from GymTime')
    expect(share.highlight).toContain('PR on Bench')
    expect(buildWorkoutShare({workouts:[w],sets:[sets[2]],exercises:[lift]},1)).toBeNull()
  })
  it('uses Android formatting order for cardio and timed loads',() => {
    const w=workout(3,now-DAY)
    const s=set(1,w,{reps:null,weight:null,calories:120,durationSeconds:95})
    expect(formatShareSet(s)).toBe('120 cal · 1m 35s')
    expect(formatShareSet({...s,distanceMeters:720})).toBe('720 m · 1m 35s')
    expect(formatShareSet({...s,weight:15})).toBe('15 lbs · 1m 35s')
  })
})
