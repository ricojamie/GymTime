import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import type { Exercise, Workout, WorkoutSet } from '../db/types'
import Insights from './Insights'
import MonthlyReport from './MonthlyReport'
import { useInsightsSource, useReportClock } from './insightsData'

vi.mock('./insightsData', async load => ({
  ...await load<typeof import('./insightsData')>(),
  useInsightsSource: vi.fn(),
  useReportClock: vi.fn(),
}))
const clock = new Date(2026,10,1,12).getTime()
const session = new Date(2026,9,1,12).getTime()
const exercise: Exercise = {id:1,name:'Test press',targetMuscle:'Chest',logType:'WEIGHT_REPS',defaultDistanceUnit:'MILES',isCustom:false,notes:null,defaultRestSeconds:60,isStarred:true,repTarget:null}
const workout: Workout = {id:1,startTime:session,endTime:session+1000,name:'Press',note:null,rating:4,ratingNote:null,routineDayId:null,routineId:null,routineNameSnapshot:null,routineDayNameSnapshot:null,startedFromRoutine:false}
const set: WorkoutSet = {id:1,workoutId:1,exerciseId:1,weight:22.0462,reps:3,rpe:null,calories:null,durationSeconds:null,distanceValue:null,distanceMeters:null,distanceUnit:null,isWarmup:false,isComplete:true,timestamp:session,note:null,supersetGroupId:null,supersetOrderIndex:0}
const source = {workouts:[workout], exercises:[exercise],sets:[set],muscleNames:['Chest','Biceps'],unit:'kg' as const}
afterEach(cleanup)

describe('local training reports', () => {
  it('updates the comparison window and shows the exercise evidence in kilograms', () => {
    vi.mocked(useInsightsSource).mockReturnValue(source)
    vi.mocked(useReportClock).mockReturnValue(clock)
    render(<MemoryRouter><Insights /></MemoryRouter>)
    expect(screen.getByText('No comparable sessions in this period.')).toBeDefined()
    fireEvent.click(screen.getByRole('button',{name:'12 weeks'}))
    expect(screen.getByRole('slider').getAttribute('aria-valuetext')).toContain('11 kg')
    fireEvent.click(screen.getByRole('button',{name:/^Chest 1 set/}))
    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByRole('button',{name:/Test press/})).toBeDefined()
  })
  it('reports the previous complete calendar month using canonical weights and local ratings', () => {
    vi.mocked(useInsightsSource).mockReturnValue(source)
    vi.mocked(useReportClock).mockReturnValue(clock)
    render(<MemoryRouter><MonthlyReport/></MemoryRouter>)
    expect(screen.getByRole('heading',{name:new Date(session).toLocaleDateString(undefined,{month:'long',year:'numeric'})})).toBeDefined()
    expect(screen.getByText('30 kg')).toBeDefined()
    expect(screen.getByText('4 / 5')).toBeDefined()
    expect(screen.getByText('Biceps')).toBeDefined()
  })
})
