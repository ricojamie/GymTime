import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { useSettings } from '../../theme'
import './gator-mascot.css'

export type GatorPose = 'hero' | 'pr' | 'stamp' | 'badge' | 'spotter'

export interface SwoleGatorProps {
  pose?: GatorPose
  size?: number | string
  interactive?: boolean
  className?: string
  label?: string
  celebrate?: boolean
}

const POSE_ASSETS: Record<GatorPose, string> = {
  hero: '/mascot/gator-hero.webp',
  pr: '/mascot/gator-pr.webp',
  stamp: '/mascot/gator-stamp.webp',
  badge: '/mascot/gator-badge.webp',
  spotter: '/mascot/gator-spotter.webp',
}

const DEFAULT_LABELS: Record<GatorPose, string> = {
  hero: 'Swole Gator mascot flexing double biceps',
  pr: 'Swole Gator celebrating a personal record',
  stamp: 'UF Swole Gator championship stamp',
  badge: 'UF Swole Gator badge',
  spotter: 'Swole Gator spotter with stopwatch',
}

/**
 * Reactive hook returning true when the user has selected the Swole Gator
 * app icon & UF Gators theme (`appIcon === 'swole_alligator'`).
 */
export function useIsGatorMode(): boolean {
  return useSettings().appIcon === 'swole_alligator'
}

export function SwoleGator({
  pose = 'hero',
  size = pose === 'badge' ? 40 : 140,
  interactive = false,
  className = '',
  label,
  celebrate = false,
}: SwoleGatorProps) {
  const [chomping, setChomping] = useState(false)
  const timerRef = useRef<number | null>(null)
  const rafRef = useRef<number | null>(null)

  useEffect(() => {
    return () => {
      if (timerRef.current !== null) window.clearTimeout(timerRef.current)
      if (rafRef.current !== null) window.cancelAnimationFrame(rafRef.current)
    }
  }, [])

  const triggerChomp = () => {
    if (!interactive) return
    if (timerRef.current !== null) window.clearTimeout(timerRef.current)
    if (rafRef.current !== null) window.cancelAnimationFrame(rafRef.current)
    setChomping(false)
    rafRef.current = window.requestAnimationFrame(() => {
      rafRef.current = null
      setChomping(true)
    })
    timerRef.current = window.setTimeout(() => {
      setChomping(false)
      timerRef.current = null
    }, 860)
  }

  const handleKeyDown = (e: KeyboardEvent<HTMLSpanElement>) => {
    if (!interactive) return
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault()
      triggerChomp()
    }
  }

  const resolvedSize = typeof size === 'number' ? `${size}px` : size
  const ariaLabel = label ?? DEFAULT_LABELS[pose]
  const isDecorative = label === ''

  const classes = [
    'gator-mascot',
    `gator-mascot--${pose}`,
    interactive ? 'gator-mascot--interactive' : '',
    celebrate ? 'gator-mascot--celebrate' : '',
    chomping ? 'gator-mascot--chomping' : '',
    className,
  ]
    .filter(Boolean)
    .join(' ')

  return (
    <span
      className={classes}
      style={{ width: resolvedSize, height: resolvedSize }}
      role={interactive ? 'button' : isDecorative ? undefined : 'img'}
      aria-label={isDecorative ? undefined : ariaLabel}
      aria-hidden={isDecorative && !interactive ? true : undefined}
      tabIndex={interactive ? 0 : undefined}
      onClick={interactive ? triggerChomp : undefined}
      onKeyDown={interactive ? handleKeyDown : undefined}
      data-pose={pose}
    >
      {pose === 'pr' && <span className="gator-pr-aura" aria-hidden="true" />}
      <span className="gator-mascot__art" aria-hidden="true">
        <img
          className="gator-mascot__img"
          src={POSE_ASSETS[pose]}
          alt=""
          aria-hidden="true"
          draggable={false}
          decoding="async"
        />
      </span>
      {interactive && chomping && (
        <>
          <span className="gator-burst-ring" aria-hidden="true" />
          <span className="gator-burst-ring gator-burst-ring--secondary" aria-hidden="true" />
          <span className="gator-chomp-tag" aria-hidden="true">
            CHOMP!
          </span>
        </>
      )}
    </span>
  )
}
