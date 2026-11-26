import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { db } from '../db/database'
import { getActiveWorkout, logSet, startWorkout } from '../db/repositories'
import Builder from './Builder'
import Workout from './Workout'

function Destination() {
  const location = useLocation()
  return <output aria-label="Destination" data-just-finished={location.state?.justFinished}>{location.pathname}</output>
}

beforeEach(async () => {
  // jsdom versions without modal dialogs still expose the underlying element.
  if (!HTMLDialogElement.prototype.showModal) {
    HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', '') }
    HTMLDialogElement.prototype.close = function () { this.removeAttribute('open') }
  }
  await db.delete()
  await db.open()
  sessionStorage.clear()
})
afterEach(() => { cleanup(); db.close() })

describe('One-time workout', () => {
  it('starts on the first exercise in the reordered plan', async () => {
    const bench = await db.exercises.where('name').equals('Barbell Bench Press').first()
    const squat = await db.exercises.where('name').equals('Barbell Squat').first()
    render(<MemoryRouter initialEntries={['/workout/builder']}><Routes>
      <Route path="/workout/builder" element={<Builder />} />
      <Route path="/workout/:id/exercise/:exerciseId" element={<Destination />} />
    </Routes></MemoryRouter>)
    fireEvent.click(await screen.findByRole('button', { name: /Barbell Bench Press/ }, { timeout: 6000 }))
    fireEvent.click(screen.getByRole('button', { name: /Barbell Squat/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Move Barbell Squat earlier' }))
    fireEvent.click(screen.getByRole('button', { name: /Start workout · 2 exercises/ }))
    const destination = await screen.findByLabelText('Destination', {}, { timeout: 5000, onTimeout: err => new Error(screen.queryByRole('alert')?.textContent || err.message) })
    const active = await getActiveWorkout()
    expect(destination).toHaveTextContent('/workout/' + active!.id + '/exercise/' + squat!.id)
    const plan = await db.workout_exercise_instances.where('workoutId').equals(active!.id).sortBy('orderIndex')
    expect(plan.map(item => item.exerciseId)).toEqual([squat!.id, bench!.id])
  }, 15000)

  it('preserves selections when an exercise form returns to the builder', async () => {
    const bench = await db.exercises.where('name').equals('Barbell Bench Press').first()
    const squat = await db.exercises.where('name').equals('Barbell Squat').first()
    sessionStorage.setItem('ironlog.workout-builder', JSON.stringify({ ids: [bench!.id], query: 'bar', muscle: 'Chest' }))
    render(<MemoryRouter initialEntries={['/workout/builder?createdExerciseId=' + squat!.id]}><Builder /></MemoryRouter>)
    await screen.findByRole('button', { name: /Start workout · 2 exercises/ }, { timeout: 6000 })
    const selection = screen.getByRole('region', { name: 'Workout order' })
    expect(within(selection).getAllByRole('listitem').map(item => item.querySelector('strong')?.textContent))
      .toEqual(['Barbell Bench Press', 'Barbell Squat'])
  }, 15000)

  it('finishes on confirmation and retains imported set-only sessions', async () => {
    const bench = await db.exercises.where('name').equals('Barbell Bench Press').first()
    const id = await startWorkout()
    await logSet({ workoutId: id, exerciseId: bench!.id, reps: 8, weight: 100 })
    await db.workout_exercise_instances.where('workoutId').equals(id).delete()
    render(<MemoryRouter initialEntries={['/workout/active']}><Routes>
      <Route path="/workout/active" element={<Workout />} />
      <Route path="/summary/:id" element={<Destination />} />
    </Routes></MemoryRouter>)
    await screen.findByRole('button', { name: /Continue Barbell Bench Press/ })
    fireEvent.click(screen.getByRole('button', { name: 'Finish' }))
    const confirm = await screen.findByRole('button', { name: 'Finish workout' })
    expect((await db.workouts.get(id))?.endTime).toBeNull()
    fireEvent.click(confirm)
    const destination = await screen.findByLabelText('Destination', {}, { timeout: 5000 })
    expect(destination).toHaveTextContent('/summary/' + id)
    expect(destination).toHaveAttribute('data-just-finished', String(id))
    expect((await db.workouts.get(id))?.endTime).toBeTypeOf('number')
  }, 15000)
})
