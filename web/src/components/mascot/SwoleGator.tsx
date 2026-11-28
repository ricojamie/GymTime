import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { useSettings } from '../../theme'
import './gator-mascot.css'

export type GatorPose = 'hero' | 'pr' | 'stamp' | 'badge' | 'spotter'
export type MascotPose = GatorPose
export type MascotBrand = 'chimp' | 'gator'

export interface IronMascotProps {
  pose?: MascotPose
  brand?: 'auto' | MascotBrand
  size?: number | string
  interactive?: boolean
  className?: string
  label?: string
  celebrate?: boolean
  onActivate?: () => void
}

export type SwoleGatorProps = Omit<IronMascotProps, 'brand'> & {
  brand?: 'auto' | MascotBrand
}

const POSE_ASSETS: Record<MascotBrand, Record<MascotPose, string>> = {
  gator: {
    hero: '/mascot/gator-hero.webp',
    pr: '/mascot/gator-pr.webp',
    stamp: '/mascot/gator-stamp.webp',
    badge: '/mascot/gator-badge.webp',
    spotter: '/mascot/gator-spotter.webp',
  },
  chimp: {
    hero: '/mascot/chimp-hero.webp',
    pr: '/mascot/chimp-pr.webp',
    stamp: '/mascot/chimp-stamp.webp',
    badge: '/mascot/chimp-badge.webp',
    spotter: '/mascot/chimp-spotter.webp',
  },
}

const DEFAULT_LABELS: Record<MascotBrand, Record<MascotPose, string>> = {
  gator: {
    hero: 'Swole Gator mascot flexing double biceps',
    pr: 'Swole Gator celebrating a personal record',
    stamp: 'UF Swole Gator championship stamp',
    badge: 'UF Swole Gator badge',
    spotter: 'Swole Gator spotter with stopwatch',
  },
  chimp: {
    hero: 'Swole Chimp mascot flexing double biceps',
    pr: 'Swole Chimp celebrating a personal record',
    stamp: 'IronLog Swole Chimp championship stamp',
    badge: 'IronLog Swole Chimp badge',
    spotter: 'Swole Chimp spotter with stopwatch',
  },
}

const ROAR_TAG: Record<MascotBrand, string> = {
  gator: 'CHOMP!',
  chimp: 'BEAST!',
}

/**
 * Reactive hook returning true when the user has selected the Swole Gator
 * app icon & UF Gators theme (`appIcon === 'swole_alligator'`).
 */
export function useIsGatorMode(): boolean {
  return useSettings().appIcon === 'swole_alligator'
}

export function useMascotBrand(): MascotBrand {
  return useIsGatorMode() ? 'gator' : 'chimp'
}

export function IronMascot({
  pose = 'hero',
  brand = 'auto',
  size = pose === 'badge' ? 40 : 140,
  interactive = false,
  className = '',
  label,
  celebrate = false,
  onActivate,
}: IronMascotProps) {
  const autoBrand = useMascotBrand()
  const resolvedBrand: MascotBrand = brand === 'auto' ? autoBrand : brand
  const [chomping, setChomping] = useState(false)
  const timerRef = useRef<number | null>(null)
  const rafRef = useRef<number | null>(null)

  useEffect(() => {
    return () => {
      if (timerRef.current !== null) window.clearTimeout(timerRef.current)
      if (rafRef.current !== null) window.cancelAnimationFrame(rafRef.current)
    }
  }, [])

  const triggerBurst = () => {
    if (!interactive) return
    onActivate?.()
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
      triggerBurst()
    }
  }

  const resolvedSize = typeof size === 'number' ? `${size}px` : size
  const ariaLabel = label ?? DEFAULT_LABELS[resolvedBrand][pose]
  const isDecorative = label === ''

  const classes = [
    'gator-mascot',
    `gator-mascot--${pose}`,
    `gator-mascot--brand-${resolvedBrand}`,
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
      onClick={interactive ? triggerBurst : undefined}
      onKeyDown={interactive ? handleKeyDown : undefined}
      data-pose={pose}
      data-mascot={resolvedBrand}
    >
      {pose === 'pr' && <span className="gator-pr-aura" aria-hidden="true" />}
      <span className="gator-mascot__art" aria-hidden="true">
        <img
          className="gator-mascot__img"
          src={POSE_ASSETS[resolvedBrand][pose]}
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
            {ROAR_TAG[resolvedBrand]}
          </span>
        </>
      )}
    </span>
  )
}

export function SwoleGator(props: SwoleGatorProps) {
  return <IronMascot {...props} brand={props.brand ?? 'gator'} />
}

export function SwoleChimp(props: SwoleGatorProps) {
  return <IronMascot {...props} brand={props.brand ?? 'chimp'} />
}
