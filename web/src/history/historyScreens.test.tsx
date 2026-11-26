import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { useState } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { defaultPreferences, type Exercise, type Workout, type WorkoutSet } from '../db/types'
import History from '../screens/History'
import Summary from '../screens/Summary'

const mocks = vi.hoisted(() => ({save:vi.fn(), updateWorkout:vi.fn()}))
vi.mock('../db/backup', () => ({ useSaveBackup: () => ({ready:true,save:mocks.save}) }))
vi.mock('../db/repositories', async importOriginal => ({...await importOriginal<typeof import('../db/repositories')>(), updateWorkout:mocks.updateWorkout}))
vi.mock('./sessionData', async importOriginal => ({...await importOriginal<typeof import('./sessionData')>(), useHistoryData:() => fixture }))

const exercise: Exercise = {id:12,name:'Deadlift',logType:'WEIGHT_REPS',targetMuscle:'Back',defaultDistanceUnit:'MILES',isCustom:false,notes:null,defaultRestSeconds:90,isStarred:false,repTarget:null}
const workout: Workout = {id:3,startTime:new Date(2026,0,22,11).getTime(),endTime:new Date(2026,0,22,12).getTime(),name:'Pull B',note:null,rating:null,ratingNote:null,routineId:2,routineDayId:5,routineNameSnapshot:'Winter training',routineDayNameSnapshot:'Wednesday',startedFromRoutine:true}
const set: WorkoutSet = {id:4,workoutId:3,exerciseId:12,weight:100,reps:5,rpe:7,calories:null,durationSeconds:null,distanceValue:null,distanceUnit:null,distanceMeters:null,isWarmup:false,isComplete:true,timestamp:workout.startTime+30000,note:'Smooth',supersetGroupId:null,supersetOrderIndex:0}
const fixture = {workouts:[workout],sets:[set,{...set,id:5,isWarmup:true,weight:30}],exercises:[exercise],prefs:defaultPreferences}
beforeEach(() => { mocks.updateWorkout.mockReset().mockResolvedValue(undefined); mocks.save.mockReset().mockResolvedValue('shared') })
afterEach(cleanup)

it('searches across month, routine and muscle, and keeps warmups out of history totals', () => {
  render(<MemoryRouter><History/></MemoryRouter>)
  const search = screen.getByRole('searchbox',{name:'Search history'})
  fireEvent.change(search,{target:{value:'winter January back'}})
  fireEvent.click(screen.getByRole('button',{name:/Pull B/}))
  const sheet = screen.getByRole('dialog',{name:'Pull B'})
  expect(sheet).toHaveTextContent('500 lb')
  expect(sheet).toHaveTextContent('1 warm-up set')
  expect(sheet).toHaveTextContent('RPE 7')
  expect(sheet).toHaveTextContent('Smooth')
})

it('saves the archive from Summary and persists edited feedback on Done', async () => {
  render(<MemoryRouter initialEntries={['/summary/3']}><Routes><Route path="/summary/:workoutId" element={<Summary/>}/><Route path="/" element={<h1>Home</h1>}/></Routes></MemoryRouter>)
  expect(screen.getByRole('region',{name:'Workout totals'})).not.toHaveAttribute('data-stamping')
  fireEvent.click(screen.getByRole('button',{name:/Save backup to Files/}))
  expect(await screen.findByText('Backup shared.')).toBeVisible()
  fireEvent.click(screen.getByRole('button',{name:'3 stars'}))
  fireEvent.click(screen.getByRole('button',{name:'3 stars'}))
  expect(screen.getByRole('button',{name:'3 stars'})).toHaveAttribute('aria-pressed','false')
  fireEvent.click(screen.getByRole('button',{name:'4 stars'}))
  fireEvent.change(screen.getByLabelText('Session note'),{target:{value:'Felt good'}})
  fireEvent.click(screen.getByRole('button',{name:'Done'}))
  await waitFor(() => expect(mocks.updateWorkout).toHaveBeenCalledWith(3,{rating:4,ratingNote:'Felt good'}))
  expect(await screen.findByRole('heading',{name:'Home'})).toBeVisible()
})

it('stamps the saved totals once on finish and accepts feedback and backup during the strike', async () => {
  function SummaryVisit() {
    const [open,setOpen] = useState(true)
    return <>{open && <Summary/>}<button onClick={() => setOpen(!open)}>Toggle summary</button></>
  }
  render(<MemoryRouter initialEntries={[{pathname:'/summary/3',state:{justFinished:3}}]}>
    <Routes><Route path="/summary/:workoutId" element={<SummaryVisit/>}/></Routes>
  </MemoryRouter>)
  const totals = screen.getByRole('region',{name:'Workout totals'})
  expect(totals).toHaveTextContent('500 lb')
  expect(totals).toHaveAttribute('data-stamping','true')
  expect(screen.getByRole('button',{name:'Done'})).toBeEnabled()
  fireEvent.click(screen.getByRole('button',{name:'3 stars'}))
  fireEvent.click(screen.getByRole('button',{name:/Save backup to Files/}))
  expect(await screen.findByText('Backup shared.')).toBeVisible()
  await waitFor(() => expect(totals).not.toHaveAttribute('data-stamping'),{timeout:2500})
  fireEvent.change(screen.getByLabelText('Session note'),{target:{value:'Still at rest'}})
  expect(totals).not.toHaveAttribute('data-stamping')
  fireEvent.click(screen.getByRole('button',{name:'Toggle summary'}))
  fireEvent.click(screen.getByRole('button',{name:'Toggle summary'}))
  expect(screen.getByRole('region',{name:'Workout totals'})).not.toHaveAttribute('data-stamping')
})
