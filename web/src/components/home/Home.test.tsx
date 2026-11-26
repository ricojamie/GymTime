import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import Home from '../../screens/Home'
import { db } from '../../db/database'
import { finishWorkout, logSet, startWorkout, updatePreferences } from '../../db/repositories'

beforeEach(async () => {
  localStorage.clear()
  await db.delete()
  await db.open()
})
afterEach(() => { cleanup(); db.close() })

const showHome = () => render(<MemoryRouter><Home/></MemoryRouter>)

describe('Home dashboard', { timeout: 30_000 }, () => {
  it('uses real empty-state metrics and opens the selected muscle evidence', async () => {
    showHome()
    expect(screen.getByRole('link', {name: 'Start workout'})).toHaveAttribute('href', '/exercises/select?workoutMode=true')
    expect(await screen.findByText('Fresh start', {}, {timeout: 12_000})).toBeInTheDocument()
    expect(screen.queryByText('Backup due')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', {name: 'Chest strength trend, building baseline'}))
    const dialog = await screen.findByRole('dialog', {name: 'Strength trends'})
    expect(within(dialog).getByRole('button', {name: 'Chest'})).toHaveAttribute('aria-pressed','true')
    expect(within(dialog).getByText('No comparable sessions logged for this muscle.')).toBeInTheDocument()
  })

  it('resumes an existing session', async () => {
    await startWorkout({name: 'Tuesday training'})
    showHome()
    expect(await screen.findByText('Resume workout', {}, {timeout: 12_000})).toHaveAttribute('href', '/workout/active')
    expect(screen.getByRole('heading', {name: 'Tuesday training'})).toBeInTheDocument()
    expect(screen.queryByRole('link', {name:'Start workout'})).not.toBeInTheDocument()
  })

  it('shows a dismissible due reminder for completed training', async () => {
    const workoutId = await startWorkout()
    const exercise = await db.exercises.toCollection().first()
    await logSet({workoutId, exerciseId: exercise!.id, weight: 100, reps: 8, isComplete: true})
    await finishWorkout(workoutId)
    await updatePreferences({workoutsSinceLastBackup: 3})
    showHome()
    expect(await screen.findByText('Backup due', {}, {timeout: 12_000})).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', {name: 'Dismiss backup reminder'}))
    expect(screen.queryByText('Backup due')).not.toBeInTheDocument()
    expect((await db.preferences.get('default'))?.workoutsSinceLastBackup).toBe(3)
  })
})
