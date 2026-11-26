import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import 'fake-indexeddb/auto'
import { db, defaultPreferences } from '../../db/database'
import { toLbs } from '../../domain/units'
import PlateCalculatorSheet from './PlateCalculatorSheet'
import SmartLogSheet from './SmartLogSheet'
import ProgressSheet from './ProgressSheet'

beforeEach(async () => {
  await db.open()
  await db.preferences.put({...defaultPreferences})
})
afterEach(cleanup)

describe('logger tools', () => {
  it('loads exact metric-equivalent plates in both modes and applies inventory per side', async () => {
    const plate = toLbs(25, 'kg')
    const bar = toLbs(20, 'kg')
    await db.preferences.put({...defaultPreferences, preferredWeightUnit:'kg', barWeight:bar,
      availablePlates:[plate], usePlateInventory:true, plateInventory:{[String(plate)]:3}})
    const onUse = vi.fn()
    render(<PlateCalculatorSheet initialWeightLb={bar + plate * 2} onUse={onUse} onClose={vi.fn()} />)
    expect(await screen.findByText('× 1')).toBeTruthy()
    fireEvent.click(screen.getByRole('button',{name:'Build weight'}))
    const add = screen.getByRole('button',{name:'Add 25 kg per side'}) as HTMLButtonElement
    expect(add.disabled).toBe(true)
    fireEvent.click(screen.getByRole('button',{name:'Use 70 kg'}))
    expect(onUse.mock.calls[0][0]).toBeCloseTo(toLbs(70,'kg'), 1)
  })

  it('reviews Smart Log sets and passes only retained drafts to the logger', async () => {
    const onOpenQueue = vi.fn()
    const exercise = await db.exercises.where('name').equals('Barbell Bench Press').first()
    render(<SmartLogSheet initialExerciseId={exercise!.id} onOpenQueue={onOpenQueue} onClose={vi.fn()} />)
    fireEvent.change(screen.getByRole('textbox'), {target:{value:'barbell bench press 3x8 at 185 rpe 8'}})
    fireEvent.click(screen.getByRole('button',{name:'Review sets'}))
    fireEvent.click(await screen.findByRole('button',{name:'Remove draft set 2'}))
    const select = screen.getByRole('combobox')
    await screen.findByRole('option',{name:'Barbell Bench Press'})
    fireEvent.change(select,{target:{value:String(exercise!.id)}})
    fireEvent.click(screen.getByRole('button',{name:'Open in logger'}))
    expect(onOpenQueue.mock.calls[0][0]).toHaveLength(2)
    expect(onOpenQueue.mock.calls[0][0][0]).toMatchObject({exerciseId:exercise!.id, reps:8, weight:185, rpe:8})
    expect(await db.sets.count()).toBe(0)
  })

  it('shows historical warm-ups while excluding them from strength evidence', async () => {
    const exercise = await db.exercises.where('name').equals('Barbell Bench Press').first()
    const now = Date.now()
    const workoutId = await db.workouts.add({name:'Session A', startTime:now - 60000, endTime:now,
      note:null,rating:null,ratingNote:null,routineId:null,routineDayId:null,routineNameSnapshot:null,routineDayNameSnapshot:null,startedFromRoutine:false})
    const base = {workoutId,exerciseId:exercise!.id,weight:100,reps:5,rpe:null,durationSeconds:null,calories:null,
      distanceValue:null,distanceUnit:null,distanceMeters:null,isWarmup:false,isComplete:true,timestamp:now-2000,
      supersetGroupId:null,supersetOrderIndex:0,note:null}
    try {
      await db.sets.bulkAdd([base,{...base,timestamp:now-1000,isWarmup:true,weight:300}])
      render(<ProgressSheet exerciseId={exercise!.id} initialTab="records" onClose={vi.fn()}/>)
      expect(await screen.findByText('All-time best · Heaviest set')).toBeTruthy()
      expect(screen.queryByText('300 lb · 5 reps')).toBeNull()
      fireEvent.click(screen.getByRole('button',{name:'History'}))
      const history = screen.getByRole('region',{name:'Workout history'})
      expect(within(history).getByText('300 lb · 5 reps')).toBeTruthy()
      expect(within(history).getByText(/Warm-up/)).toBeTruthy()
    } finally {
      cleanup()
      await db.sets.where('workoutId').equals(workoutId).delete()
      await db.workouts.delete(workoutId)
    }
  })
})
