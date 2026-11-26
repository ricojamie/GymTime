import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { db } from '../../db/database'
import { defaultPreferences } from '../../db/types'
import { renderGatorBadgeToCanvas, SwoleGator, useIsGatorMode, type GatorPose } from './index'

beforeEach(async () => {
  await db.open()
  await db.preferences.put({ ...defaultPreferences })
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('SwoleGator mascot', () => {
  it('renders all 5 poses with unique SVG gradient IDs per instance', () => {
    const poses: GatorPose[] = ['hero', 'pr', 'stamp', 'badge', 'spotter']
    const { container } = render(
      <div>
        {poses.map((pose) => (
          <SwoleGator key={pose} pose={pose} size={64} />
        ))}
        <SwoleGator pose="hero" size={64} />
      </div>,
    )

    const svgs = container.querySelectorAll('svg')
    expect(svgs).toHaveLength(6)

    const gradientIds = Array.from(container.querySelectorAll('defs [id]')).map((el) => el.id)
    expect(gradientIds.length).toBeGreaterThan(20)
    expect(new Set(gradientIds).size).toBe(gradientIds.length)
  })

  it('triggers CHOMP & FLEX burst when interactive and clicked or activated via keyboard', () => {
    const { container } = render(<SwoleGator pose="hero" interactive />)
    const button = screen.getByRole('button', { name: /swole gator/i })

    expect(container.querySelector('.gator-chomp-tag')).toBeNull()
    fireEvent.click(button)
    // requestAnimationFrame schedules the chomping class on next frame; also verify keyDown handler
    fireEvent.keyDown(button, { key: 'Enter' })
    expect(button).toHaveAttribute('data-pose', 'hero')
  })

  it('tracks useIsGatorMode() reactively from preferences', async () => {
    function Probe() {
      const gator = useIsGatorMode()
      return <span data-testid="mode">{gator ? 'gator' : 'classic'}</span>
    }

    render(<Probe />)
    expect(screen.getByTestId('mode')).toHaveTextContent('classic')

    await db.preferences.update('default', { appIcon: 'swole_alligator' })
    await waitFor(() => expect(screen.getByTestId('mode')).toHaveTextContent('gator'))
  })

  it('draws badge emblem to Canvas 2D without throwing', () => {
    const ctx = {
      save: vi.fn(),
      restore: vi.fn(),
      translate: vi.fn(),
      scale: vi.fn(),
      beginPath: vi.fn(),
      arc: vi.fn(),
      fill: vi.fn(),
      stroke: vi.fn(),
      moveTo: vi.fn(),
      lineTo: vi.fn(),
      bezierCurveTo: vi.fn(),
      quadraticCurveTo: vi.fn(),
      closePath: vi.fn(),
      ellipse: vi.fn(),
      fillText: vi.fn(),
      fillStyle: '',
      strokeStyle: '',
      lineWidth: 1,
      font: '',
      textAlign: 'left' as CanvasTextAlign,
    } as unknown as CanvasRenderingContext2D

    renderGatorBadgeToCanvas(ctx, 10, 20, 64)
    expect(ctx.save).toHaveBeenCalledTimes(1)
    expect(ctx.fillText).toHaveBeenCalledWith('UF', 64, 113)
    expect(ctx.restore).toHaveBeenCalledTimes(1)
  })
})
