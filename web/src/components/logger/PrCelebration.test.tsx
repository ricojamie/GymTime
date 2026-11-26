import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { db } from '../../db/database'
import { defaultPreferences } from '../../db/types'
import PrCelebration from './PrCelebration'

beforeEach(async () => {
  await db.open()
  await db.preferences.put({ ...defaultPreferences })
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('PrCelebration', () => {
  it('renders classic PR burst with 3 shockwaves, 40 particles, and dismiss callback', () => {
    const onDismiss = vi.fn()
    const { container } = render(
      <PrCelebration
        id={1}
        labels={['Heaviest weight', 'Best E1RM']}
        exerciseName="Barbell Bench Press"
        onDismiss={onDismiss}
      />,
    )

    expect(container.querySelector('.pr-overlay')).not.toHaveClass('is-gator')
    expect(container.querySelector('.pr-edge-flash')).toBeInTheDocument()
    expect(container.querySelectorAll('.pr-shockwave')).toHaveLength(3)
    expect(container.querySelectorAll('.pr-particle')).toHaveLength(40)
    expect(screen.getByText('Barbell Bench Press')).toBeInTheDocument()
    expect(screen.getByText('Heaviest weight')).toBeInTheDocument()
    expect(screen.getByText('Best E1RM')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /dismiss/i }))
    expect(onDismiss).toHaveBeenCalledTimes(1)
  })

  it('renders UF Swole Gator PR hoist when isGator is enabled', () => {
    const { container } = render(
      <PrCelebration
        id={2}
        labels={['New rep max']}
        exerciseName="Back Squat"
        isGator
        onDismiss={() => {}}
      />,
    )

    expect(container.querySelector('.pr-overlay')).toHaveClass('is-gator')
    const gatorSvg = container.querySelector('[data-pose="pr"]')
    expect(gatorSvg).toBeInTheDocument()
    expect(screen.getByText('Back Squat')).toBeInTheDocument()
    expect(screen.getByText('New rep max')).toBeInTheDocument()
  })

  it('triggers Android haptic pulse only when timerVibrateEnabled is true', async () => {
    const vibrate = vi.fn(() => true)
    Object.defineProperty(navigator, 'vibrate', {
      configurable: true,
      writable: true,
      value: vibrate,
    })

    await db.preferences.put({ ...defaultPreferences, timerVibrateEnabled: true })
    const { unmount } = render(
      <PrCelebration id={3} labels={['Best E1RM']} exerciseName="Deadlift" onDismiss={() => {}} />,
    )

    await waitFor(() => expect(vibrate).toHaveBeenCalledWith([45, 55, 110]))
    unmount()

    vibrate.mockClear()
    await db.preferences.put({ ...defaultPreferences, timerVibrateEnabled: false })
    render(
      <PrCelebration id={4} labels={['Best E1RM']} exerciseName="Deadlift" onDismiss={() => {}} />,
    )

    await waitFor(async () => {
      expect((await db.preferences.get('default'))?.timerVibrateEnabled).toBe(false)
    })
    expect(vibrate).not.toHaveBeenCalled()
  })
})
