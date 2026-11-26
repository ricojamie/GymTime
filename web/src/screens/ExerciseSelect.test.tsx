import { cleanup, configure, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { db } from '../db/database'
import ExerciseForm from './ExerciseForm'
import ExerciseSelect from './ExerciseSelect'

const routes = [
  { path: '/exercises/select', element: <ExerciseSelect/> },
  { path: '/exercises/new', element: <ExerciseForm/> },
  { path: '/workout/:workoutId/exercise/:exerciseId', element: <h1>Logging</h1> },
  { path: '/workout/builder', element: <h1>Builder</h1> },
  { path: '/library', element: <h1>Library</h1> },
]
function visit(entry: string) {
  const router = createMemoryRouter(routes, { initialEntries: [entry] })
  render(<RouterProvider router={router}/>)
  return router
}
beforeAll(() => {
  configure({ asyncUtilTimeout: 5000 })
  // jsdom's AbortSignal and Node's Request are different implementations.
  // These memory routes have no network loaders, so navigation needs no fetch signal.
  const NativeRequest = Request
  vi.stubGlobal('Request', class extends NativeRequest {
    constructor(input: RequestInfo | URL, init?: RequestInit) { super(input, { ...init, signal: undefined }) }
  })
})
afterAll(() => { vi.unstubAllGlobals(); configure({ asyncUtilTimeout: 1000 }) })
beforeEach(async () => {
  // jsdom lacks the native dialog open/close methods used by the iOS sheet.
  if (!HTMLDialogElement.prototype.showModal) {
    HTMLDialogElement.prototype.showModal = function () { this.open = true }
    HTMLDialogElement.prototype.close = function () { this.open = false }
  }
  sessionStorage.clear()
  await db.delete()
  await db.open()
})
afterEach(() => { cleanup(); db.close() })

describe('exercise selection', () => {
  it('searches across name and muscle, then starts with the selected exercise', async () => {
    visit('/exercises/select?workoutMode=true')
    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'press chest barbell' } })
    const match = await screen.findByRole('button', { name: /^Barbell Bench Press.*Chest/ })
    expect(screen.queryByText('Leg Press')).toBeNull()
    fireEvent.click(match)
    await screen.findByRole('heading', { name: 'Logging' })
    const workout = (await db.workouts.toArray())[0]
    const bench = await db.exercises.where('name').equals('Barbell Bench Press').first()
    const slots = await db.workout_exercise_instances.where('workoutId').equals(workout.id).toArray()
    expect(slots.map(item => item.exerciseId)).toEqual([bench?.id])
  })
  it('builds an ordered superset and enforces its minimum selection', async () => {
    visit('/exercises/select?mode=build_superset')
    const bench = await screen.findByRole('button', { name: /Barbell Bench Press.*Chest/ })
    fireEvent.click(bench)
    expect((screen.getByRole('button', { name: /Start superset/ }) as HTMLButtonElement).disabled).toBe(true)
    fireEvent.click(screen.getByRole('button', { name: /Barbell Curl.*Biceps/ }))
    fireEvent.click(screen.getByRole('button', { name: /Start superset/ }))
    await screen.findByRole('heading', { name: 'Logging' })
    const slots = await db.workout_exercise_instances.toArray()
    expect(slots).toHaveLength(2)
    expect(slots[0].supersetGroupId).toBeTruthy()
    expect(slots[0].supersetGroupId).toBe(slots[1].supersetGroupId)
    expect(slots.map(item => item.supersetOrderIndex)).toEqual([0, 1])
  })
  it('keeps the form draft on cancelled navigation and returns a created ID to the builder', async () => {
    const router = visit('/exercises/new?returnTo=%2Fworkout%2Fbuilder')
    const name = await screen.findByRole('textbox', { name: 'Exercise name' })
    fireEvent.change(name, { target: { value: 'Weighted Carry Test' } })
    fireEvent.click(screen.getByRole('button', { name: 'Legs' }))
    fireEvent.click(screen.getByRole('button', { name: 'Back to builder' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Keep editing' }))
    expect((name as HTMLInputElement).value).toBe('Weighted Carry Test')
    fireEvent.click(screen.getByRole('button', { name: 'More log types' }))
    fireEvent.click(screen.getByRole('button', { name: 'Weight + distance' }))
    fireEvent.click(screen.getByRole('button', { name: 'm' }))
    fireEvent.click(screen.getByRole('button', { name: 'Save exercise' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/workout/builder'))
    const created = await db.exercises.get(Number(new URLSearchParams(router.state.location.search).get('createdExerciseId')))
    expect(created).toMatchObject({ name: 'Weighted Carry Test', logType: 'WEIGHT_DISTANCE', defaultDistanceUnit: 'METERS', isCustom: true })
  })
})
