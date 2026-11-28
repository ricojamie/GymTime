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

  it('renders the interactive Swole Chimp kinetic hero in Normal mode', async () => {
    const { container } = showHome()
    expect(await screen.findByRole('button', { name: /swole chimp/i }, { timeout: 12_000 })).toBeInTheDocument()
    expect(container.querySelector('.apex-mascot-layer.home-hero-mascot-wrap [data-mascot="chimp"][data-pose="hero"]')).toBeInTheDocument()
    expect(container.querySelector('.apex-canvas-back')).toBeInTheDocument()
    expect(container.querySelector('.apex-canvas-front')).toBeInTheDocument()
    expect(container.querySelector('.home-brand-sub')).toHaveTextContent('SWOLE CHIMP')
    expect(container.querySelector('.home-brand [data-mascot="chimp"][data-pose="badge"]')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /unleash apex surge/i }))
  })

  it('renders the interactive UF Swole Gator hero in Gator mode', async () => {
    await updatePreferences({ appIcon: 'swole_alligator' })
    const { container } = showHome()
    expect(await screen.findByRole('button', { name: /swole gator/i }, { timeout: 12_000 })).toBeInTheDocument()
    expect(container.querySelector('.apex-mascot-layer.home-hero-mascot-wrap [data-mascot="gator"][data-pose="hero"]')).toBeInTheDocument()
    expect(container.querySelector('.home-brand-sub')).toHaveTextContent('THE SWAMP')
    expect(container.querySelector('.home-brand [data-mascot="gator"][data-pose="badge"]')).toBeInTheDocument()
  })

  it('clamps Feb 29 leap-day prior-year volume to Feb 28 of the prior year', async () => {
    const leapNow = new Date(2024, 1, 29, 12, 0, 0).getTime()
    const origNow = Date.now
    Date.now = () => leapNow
    try {
      const exercise = await db.exercises.toCollection().first()
      const seedCompleted = async (name: string, ts: number, reps: number) => {
        const id = await startWorkout({ name })
        const setId = await logSet({ workoutId: id, exerciseId: exercise!.id, weight: 100, reps, isComplete: true })
        await finishWorkout(id)
        await db.workouts.update(id, { startTime: ts - 1800_000, endTime: ts })
        await db.sets.update(setId, { timestamp: ts - 600_000 })
      }
      // 2024 YTD workout on Feb 29: 100 * 10 = 1,000 lb
      await seedCompleted('Leap Day', leapNow - 1800_000, 10)
      // 2023 workout on Feb 28 (included in prior YTD): 100 * 5 = 500 lb
      await seedCompleted('Prior Feb 28', new Date(2023, 1, 28, 10, 0, 0).getTime(), 5)
      // 2023 workout on Mar 1 (MUST be excluded from Feb 29 clamped prior YTD): 100 * 50 = 5,000 lb
      await seedCompleted('Prior Mar 1', new Date(2023, 2, 1, 10, 0, 0).getTime(), 50)

      showHome()
      const streakBtn = await screen.findByRole('button', { name: /iron streak/i }, { timeout: 12_000 })
      fireEvent.click(streakBtn)
      const dialog = await screen.findByRole('dialog', { name: /iron streak/i })
      // 1,000 lb vs 500 lb (Feb 28, 2023) — NOT 5,500 lb (which would happen if Mar 1 leaked in)
      expect(within(dialog).getByText('Same period last year').parentElement).toHaveTextContent(/500 lb/)
    } finally {
      Date.now = origNow
    }
  })
})

