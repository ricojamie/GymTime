import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { db } from '../../db/database'
import { defaultPreferences } from '../../db/types'
import {
  IronMascot,
  renderGatorBadgeToCanvas,
  SwoleChimp,
  SwoleGator,
  useIsGatorMode,
  useMascotBrand,
  type GatorPose,
  type MascotPose,
} from './index'

beforeEach(async () => {
  await db.open()
  await db.preferences.put({ ...defaultPreferences })
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('SwoleGator mascot', () => {
  it('renders all 5 poses with WebP artwork and matching data-pose attributes', () => {
    const poses: GatorPose[] = ['hero', 'pr', 'stamp', 'badge', 'spotter']
    const { container } = render(
      <div>
        {poses.map((pose) => (
          <SwoleGator key={pose} pose={pose} size={64} />
        ))}
      </div>,
    )

    const imgs = container.querySelectorAll('img.gator-mascot__img')
    expect(imgs).toHaveLength(5)

    poses.forEach((pose, idx) => {
      const mascot = container.querySelector(`[data-pose="${pose}"]`)
      expect(mascot).not.toBeNull()
      const img = imgs[idx]
      expect(mascot?.contains(img)).toBe(true)
      expect(img).toHaveAttribute('src', `/mascot/gator-${pose}.webp`)
      expect(img).toHaveAttribute('alt', '')
      expect(img).toHaveAttribute('aria-hidden', 'true')
    })

    expect(container.querySelector('[data-pose="pr"] .gator-pr-aura')).not.toBeNull()
    expect(container.querySelector('[data-pose="hero"] .gator-pr-aura')).toBeNull()
  })

  it('triggers CHOMP & FLEX burst when interactive and clicked or activated via keyboard', async () => {
    const { container } = render(<SwoleGator pose="hero" interactive />)
    const button = screen.getByRole('button', { name: 'Swole Gator mascot flexing double biceps' })

    expect(container.querySelector('.gator-chomp-tag')).toBeNull()
    fireEvent.click(button)
    await waitFor(() => {
      expect(container.querySelector('.gator-chomp-tag')).toHaveTextContent('CHOMP!')
      expect(container.querySelectorAll('.gator-burst-ring')).toHaveLength(2)
      expect(container.querySelector('.gator-burst-ring--secondary')).not.toBeNull()
    })
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

  it('renders SwoleChimp in all 5 poses with chimp WebP artwork and data-mascot="chimp"', () => {
    const poses: MascotPose[] = ['hero', 'pr', 'stamp', 'badge', 'spotter']
    const { container } = render(
      <div>
        {poses.map((pose) => (
          <SwoleChimp key={pose} pose={pose} size={64} />
        ))}
      </div>,
    )

    const imgs = container.querySelectorAll('img.gator-mascot__img')
    expect(imgs).toHaveLength(5)

    poses.forEach((pose, idx) => {
      const mascot = container.querySelector(`[data-pose="${pose}"]`)
      expect(mascot).not.toBeNull()
      expect(mascot).toHaveAttribute('data-mascot', 'chimp')
      expect(mascot).toHaveClass('gator-mascot--brand-chimp')
      const img = imgs[idx]
      expect(mascot?.contains(img)).toBe(true)
      expect(img).toHaveAttribute('src', `/mascot/chimp-${pose}.webp`)
      expect(img).toHaveAttribute('alt', '')
      expect(img).toHaveAttribute('aria-hidden', 'true')
    })

    expect(container.querySelector('[data-pose="pr"] .gator-pr-aura')).not.toBeNull()
    expect(container.querySelector('[data-pose="hero"] .gator-pr-aura')).toBeNull()
  })

  it('defaults IronMascot to chimp in normal mode and reactively switches to gator', async () => {
    function Probe() {
      const brand = useMascotBrand()
      return (
        <div>
          <span data-testid="brand">{brand}</span>
          <IronMascot pose="hero" size={80} />
        </div>
      )
    }

    const { container } = render(<Probe />)
    expect(screen.getByTestId('brand')).toHaveTextContent('chimp')

    const getMascot = () => container.querySelector('[data-pose="hero"]')
    const getImg = () => container.querySelector('img.gator-mascot__img')

    expect(getMascot()).toHaveAttribute('data-mascot', 'chimp')
    expect(getMascot()).toHaveClass('gator-mascot--brand-chimp')
    expect(getImg()).toHaveAttribute('src', '/mascot/chimp-hero.webp')

    await db.preferences.update('default', { appIcon: 'swole_alligator' })

    await waitFor(() => {
      expect(screen.getByTestId('brand')).toHaveTextContent('gator')
      expect(getMascot()).toHaveAttribute('data-mascot', 'gator')
      expect(getMascot()).toHaveClass('gator-mascot--brand-gator')
      expect(getImg()).toHaveAttribute('src', '/mascot/gator-hero.webp')
    })
  })

  it('triggers BEAST! burst and invokes onActivate when interactive SwoleChimp is activated', async () => {
    const onActivate = vi.fn()
    const { container } = render(<SwoleChimp pose="hero" interactive onActivate={onActivate} />)
    const button = screen.getByRole('button', { name: 'Swole Chimp mascot flexing double biceps' })

    expect(button).toHaveAttribute('data-mascot', 'chimp')
    expect(container.querySelector('.gator-chomp-tag')).toBeNull()

    fireEvent.click(button)
    expect(onActivate).toHaveBeenCalledTimes(1)

    await waitFor(() => {
      expect(container.querySelector('.gator-chomp-tag')).toHaveTextContent('BEAST!')
      expect(container.querySelectorAll('.gator-burst-ring')).toHaveLength(2)
      expect(container.querySelector('.gator-burst-ring--secondary')).not.toBeNull()
    })

    fireEvent.keyDown(button, { key: 'Enter' })
    expect(onActivate).toHaveBeenCalledTimes(2)
  })
})
