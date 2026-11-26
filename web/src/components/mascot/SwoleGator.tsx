import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react'
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

const DEFAULT_LABELS: Record<GatorPose, string> = {
  hero: 'Swole Gator mascot flexing with barbell',
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
  const rawId = useId()
  const uid = rawId.replace(/[^a-zA-Z0-9_-]/g, '') || 'gator'
  const [chomping, setChomping] = useState(false)
  const timerRef = useRef<number | null>(null)

  useEffect(() => {
    return () => {
      if (timerRef.current !== null) window.clearTimeout(timerRef.current)
    }
  }, [])

  const triggerChomp = () => {
    if (!interactive) return
    if (timerRef.current !== null) window.clearTimeout(timerRef.current)
    setChomping(false)
    requestAnimationFrame(() => setChomping(true))
    timerRef.current = window.setTimeout(() => {
      setChomping(false)
      timerRef.current = null
    }, 820)
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
      {pose === 'badge' ? (
        <BadgeSvg uid={uid} />
      ) : (
        <FullSvg pose={pose} uid={uid} />
      )}
      {interactive && chomping && (
        <>
          <span className="gator-burst-ring" aria-hidden="true" />
          <span className="gator-chomp-tag" aria-hidden="true">
            CHOMP!
          </span>
        </>
      )}
    </span>
  )
}

function SharedDefs({ uid }: { uid: string }) {
  return (
    <defs>
      <linearGradient id={`${uid}-scale`} x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#2EE66B" />
        <stop offset="42%" stopColor="#1E8A44" />
        <stop offset="82%" stopColor="#105A2B" />
        <stop offset="100%" stopColor="#083619" />
      </linearGradient>
      <linearGradient id={`${uid}-scaleDark`} x1="0%" y1="0%" x2="0%" y2="100%">
        <stop offset="0%" stopColor="#177337" />
        <stop offset="100%" stopColor="#073117" />
      </linearGradient>
      <linearGradient id={`${uid}-belly`} x1="0%" y1="0%" x2="0%" y2="100%">
        <stop offset="0%" stopColor="#F9ECAF" />
        <stop offset="55%" stopColor="#E8D284" />
        <stop offset="100%" stopColor="#BA9B42" />
      </linearGradient>
      <linearGradient id={`${uid}-ufBlue`} x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#1D4ED8" />
        <stop offset="55%" stopColor="#0021A5" />
        <stop offset="100%" stopColor="#00125E" />
      </linearGradient>
      <linearGradient id={`${uid}-ufOrange`} x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#FF7244" />
        <stop offset="52%" stopColor="#FA4616" />
        <stop offset="100%" stopColor="#BE2D06" />
      </linearGradient>
      <linearGradient id={`${uid}-steel`} x1="0%" y1="0%" x2="0%" y2="100%">
        <stop offset="0%" stopColor="#94A3B8" />
        <stop offset="45%" stopColor="#475569" />
        <stop offset="100%" stopColor="#1E293B" />
      </linearGradient>
      <linearGradient id={`${uid}-plate`} x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#334155" />
        <stop offset="60%" stopColor="#182235" />
        <stop offset="100%" stopColor="#090F1D" />
      </linearGradient>
      <radialGradient id={`${uid}-aura`} cx="50%" cy="50%" r="50%">
        <stop offset="0%" stopColor="#FA4616" stopOpacity="0.28" />
        <stop offset="55%" stopColor="#0021A5" stopOpacity="0.24" />
        <stop offset="100%" stopColor="#040A1C" stopOpacity="0" />
      </radialGradient>
      <linearGradient id={`${uid}-glint`} x1="0%" y1="0%" x2="100%" y2="0%">
        <stop offset="0%" stopColor="#FFFFFF" stopOpacity="0" />
        <stop offset="50%" stopColor="#FFFFFF" stopOpacity="0.7" />
        <stop offset="100%" stopColor="#FFFFFF" stopOpacity="0" />
      </linearGradient>
      <linearGradient id={`${uid}-eye`} x1="0%" y1="0%" x2="0%" y2="100%">
        <stop offset="0%" stopColor="#FEF08A" />
        <stop offset="100%" stopColor="#F59E0B" />
      </linearGradient>
    </defs>
  )
}

/** Crisp 128x128 UF Swole Gator badge optimized for 28px–64px and PWA icons */
function BadgeSvg({ uid }: { uid: string }) {
  return (
    <svg className="gator-mascot__svg" viewBox="0 0 128 128" fill="none">
      <SharedDefs uid={uid} />
      {/* Varsity Navy Crest */}
      <circle cx="64" cy="64" r="61" fill="#040A1C" />
      <circle cx="64" cy="64" r="57" stroke={`url(#${uid}-ufBlue)`} strokeWidth="5" />
      <circle cx="64" cy="64" r="53.5" stroke={`url(#${uid}-ufOrange)`} strokeWidth="2.5" />

      {/* Broad Traps & Varsity Collar */}
      <path
        d="M22 108 C24 88 38 78 64 78 C90 78 104 88 106 108 Z"
        fill={`url(#${uid}-ufBlue)`}
        stroke="#040A1C"
        strokeWidth="2.5"
      />
      <path
        d="M36 86 Q64 98 92 86 L88 94 Q64 106 40 94 Z"
        fill={`url(#${uid}-ufOrange)`}
      />

      {/* Dorsal Ridge Spikes */}
      <path
        d="M32 45 L24 35 L38 38 L37 25 L48 33 L55 19 L64 30 L74 21 L78 34 Z"
        fill={`url(#${uid}-scaleDark)`}
        stroke="#062812"
        strokeWidth="2"
        strokeLinejoin="round"
      />

      {/* UF Varsity Headband */}
      <path
        d="M33 43 Q62 34 86 43 L84 52 Q60 44 31 52 Z"
        fill={`url(#${uid}-ufBlue)`}
        stroke="#040A1C"
        strokeWidth="2"
      />
      <path d="M32 48 Q60 40 85 48" stroke="#FA4616" strokeWidth="2.5" fill="none" />

      {/* Muscular Gator Head & Snout */}
      <path
        d="M28 74 C27 54 40 42 58 42 C72 42 82 47 88 53 L107 56 C113 57 115 64 109 68 L84 72 L68 74 L99 75 C103 77 101 85 93 88 C78 93 52 94 34 86 Z"
        fill={`url(#${uid}-scale)`}
        stroke="#041E0E"
        strokeWidth="2.5"
        strokeLinejoin="round"
      />

      {/* Gold Plated Lower Jaw */}
      <path
        d="M52 78 C66 78 82 77 97 76 C99 81 94 86 85 88 C71 91 55 90 43 85 Z"
        fill={`url(#${uid}-belly)`}
      />

      {/* Jawline & Cheek Muscle */}
      <path
        d="M42 64 C48 60 56 64 56 72"
        stroke="#0B4620"
        strokeWidth="2.5"
        strokeLinecap="round"
        fill="none"
      />

      {/* Razor Teeth */}
      <g fill="#FFFFFF" stroke="#040A1C" strokeWidth="1">
        <polygon points="64,72 68,79 72,72" />
        <polygon points="75,71 79,78 83,71" />
        <polygon points="86,70 90,77 94,70" />
        <polygon points="97,69 100,75 103,69" />
      </g>

      {/* Fierce Eye & Glint */}
      <path
        d="M60 52 L77 50 L79 58 L64 60 Z"
        fill="#040A1C"
      />
      <ellipse cx="70.5" cy="54.5" rx="4.5" ry="3.2" fill={`url(#${uid}-eye)`} />
      <rect x="69.6" y="51.6" width="1.8" height="5.8" rx="0.9" fill="#040A1C" />
      <circle cx="72.5" cy="53.2" r="1.4" fill="#FFFFFF" />

      {/* Nostril */}
      <ellipse cx="104" cy="59" rx="2.6" ry="1.6" fill="#041E0E" />

      {/* Crisp UF Chest Mark */}
      <text
        x="64"
        y="113"
        textAnchor="middle"
        fill="#FFFFFF"
        fontFamily="Impact, 'Bebas Neue', Arial Black, sans-serif"
        fontWeight="900"
        fontSize="16"
        letterSpacing="1.5"
      >
        UF
      </text>
    </svg>
  )
}

function FullSvg({ pose, uid }: { pose: Exclude<GatorPose, 'badge'>; uid: string }) {
  return (
    <svg className="gator-mascot__svg" viewBox="0 0 200 200" fill="none">
      <SharedDefs uid={uid} />
      {/* Ambient Swamp / Varsity Aura */}
      <circle cx="100" cy="100" r="92" fill={`url(#${uid}-aura)`} />

      {pose === 'pr' && <PrRays uid={uid} />}
      {pose === 'stamp' && <StampFrame uid={uid} />}

      <g className={pose === 'pr' ? 'gator-pr-hoist' : 'gator-breathe'}>
        {pose === 'pr' ? (
          <PrGatorBody uid={uid} />
        ) : pose === 'spotter' ? (
          <SpotterGatorBody uid={uid} />
        ) : (
          <HeroGatorBody uid={uid} isStamp={pose === 'stamp'} />
        )}
      </g>

      {/* Subtle Floating Chalk Particles */}
      {(pose === 'hero' || pose === 'pr') && (
        <g fill="#F8FAFC" opacity="0.8">
          <circle className="gator-chalk-1" cx="34" cy="76" r="2.2" />
          <circle className="gator-chalk-2" cx="166" cy="72" r="2.6" />
          <circle className="gator-chalk-3" cx="100" cy="24" r="1.8" />
        </g>
      )}
    </svg>
  )
}

/** Shared Gator Head with optional roaring open-jaw mode */
function GatorHead({
  uid,
  roar = false,
  x = 100,
  y = 54,
  scale = 1,
}: {
  uid: string
  roar?: boolean
  x?: number
  y?: number
  scale?: number
}) {
  return (
    <g transform={`translate(${x}, ${y}) scale(${scale})`}>
      {/* Dorsal Head Spikes */}
      <path
        d="M-24 -12 L-30 -24 L-16 -18 L-14 -31 L-3 -22 L5 -33 L12 -21 L22 -26 L20 -12 Z"
        fill={`url(#${uid}-scaleDark)`}
        stroke="#041E0E"
        strokeWidth="2"
        strokeLinejoin="round"
      />

      {/* UF Varsity Headband */}
      <path
        d="M-23 -11 Q0 -21 24 -11 L22 -3 Q0 -11 -24 -3 Z"
        fill={`url(#${uid}-ufBlue)`}
        stroke="#040A1C"
        strokeWidth="2"
      />
      <path d="M-23 -7 Q0 -15 23 -7" stroke="#FA4616" strokeWidth="2.5" fill="none" />

      {/* Lower Jaw (Animated on Chomp / Roar) */}
      <g className={roar ? 'gator-jaw-chomp' : 'gator-jaw-interactive'}>
        <path
          d={
            roar
              ? 'M-16 14 L26 22 C31 24 29 33 21 34 C4 36 -14 30 -21 20 Z'
              : 'M-14 12 L28 12 C32 14 30 21 22 23 C6 26 -12 24 -20 18 Z'
          }
          fill={`url(#${uid}-belly)`}
          stroke="#041E0E"
          strokeWidth="2.2"
          strokeLinejoin="round"
        />
        {roar && (
          <path
            d="M-6 16 Q10 18 22 23 L14 27 Q0 24 -10 20 Z"
            fill="#991B1B"
          />
        )}
        {/* Lower Fangs */}
        <g fill="#FFFFFF" stroke="#040A1C" strokeWidth="0.9">
          <polygon points="4,13 7,7 10,13" />
          <polygon points="14,14 17,8 20,14" />
          <polygon points="22,16 25,10 27,16" />
        </g>
      </g>

      {/* Upper Cranium & Snout */}
      <path
        d="M-26 12 C-27 -6 -16 -16 2 -16 C14 -16 22 -11 26 -5 L42 -1 C47 1 47 7 41 10 L18 13 L-10 15 Z"
        fill={`url(#${uid}-scale)`}
        stroke="#041E0E"
        strokeWidth="2.4"
        strokeLinejoin="round"
      />

      {/* Scale Cheek Detail */}
      <path
        d="M-15 4 C-10 0 -4 3 -4 10"
        stroke="#0A401E"
        strokeWidth="2.2"
        strokeLinecap="round"
      />

      {/* Upper Sharp Teeth */}
      <g fill="#FFFFFF" stroke="#040A1C" strokeWidth="0.9">
        <polygon points="2,13 5,20 9,13" />
        <polygon points="12,12 15,19 19,12" />
        <polygon points="22,11 25,18 29,11" />
        <polygon points="32,10 35,16 38,10" />
      </g>

      {/* Fierce Eye & Glint */}
      <path d="M2 -7 L17 -9 L19 -1 L5 1 Z" fill="#040A1C" />
      <ellipse cx="11" cy="-4.5" rx="4.2" ry="2.8" fill={`url(#${uid}-eye)`} />
      <rect x="10.2" y="-7.1" width="1.6" height="5.2" rx="0.8" fill="#040A1C" />
      <circle className="gator-eye-spark" cx="12.8" cy="-5.6" r="1.3" fill="#FFFFFF" />

      {/* Snout Nostril */}
      <ellipse cx="38" cy="2" rx="2.4" ry="1.5" fill="#041E0E" />
    </g>
  )
}

/** Hero & Stamp Gator Body: Double-flex / Front-rack UF Barbell Powerhouse */
function HeroGatorBody({ uid, isStamp }: { uid: string; isStamp: boolean }) {
  return (
    <g>
      {/* Massive Traps & Shoulders */}
      <path
        d="M44 104 C48 80 68 68 100 68 C132 68 152 80 156 104 L144 166 L56 166 Z"
        fill={`url(#${uid}-scale)`}
        stroke="#041E0E"
        strokeWidth="2.5"
      />

      {/* UF Blue Varsity Gym Tank */}
      <path
        d="M64 82 L78 82 L84 96 Q100 104 116 96 L122 82 L136 82 L144 158 L56 158 Z"
        fill={`url(#${uid}-ufBlue)`}
        stroke="#040A1C"
        strokeWidth="2.4"
        strokeLinejoin="round"
      />
      {/* UF Orange Tank Trim */}
      <path
        d="M78 82 L84 96 Q100 104 116 96 L122 82"
        stroke="#FA4616"
        strokeWidth="3.5"
        fill="none"
      />
      <path d="M58 148 L142 148" stroke="#FA4616" strokeWidth="4" />

      {/* Bold UF Chest Mark */}
      <text
        x="100"
        y="126"
        textAnchor="middle"
        fill="#FFFFFF"
        fontFamily="Impact, 'Bebas Neue', Arial Black, sans-serif"
        fontWeight="900"
        fontSize="24"
        letterSpacing="2"
      >
        UF
      </text>

      {/* Left Flexing Bicep & Forearm */}
      <g className="gator-flex-left">
        <path
          d="M58 92 C34 88 22 102 26 122 C29 134 42 138 52 132 L46 112 L58 106 Z"
          fill={`url(#${uid}-scale)`}
          stroke="#041E0E"
          strokeWidth="2.5"
          strokeLinejoin="round"
        />
        {/* Bicep Peak Highlight */}
        <path d="M35 102 Q44 96 52 104" stroke="#4ADE80" strokeWidth="2" strokeLinecap="round" opacity="0.65" />
        {/* UF Sweatband */}
        <rect x="33" y="106" width="16" height="9" rx="3" fill={`url(#${uid}-ufOrange)`} stroke="#040A1C" strokeWidth="1.8" transform="rotate(-18 41 110)" />
        {/* Fist gripping bar */}
        <circle cx="44" cy="100" r="10" fill={`url(#${uid}-scale)`} stroke="#041E0E" strokeWidth="2.4" />
      </g>

      {/* Right Flexing Bicep & Forearm */}
      <g className="gator-flex-right">
        <path
          d="M142 92 C166 88 178 102 174 122 C171 134 158 138 148 132 L154 112 L142 106 Z"
          fill={`url(#${uid}-scale)`}
          stroke="#041E0E"
          strokeWidth="2.5"
          strokeLinejoin="round"
        />
        <path d="M165 102 Q156 96 148 104" stroke="#4ADE80" strokeWidth="2" strokeLinecap="round" opacity="0.65" />
        <rect x="151" y="106" width="16" height="9" rx="3" fill={`url(#${uid}-ufOrange)`} stroke="#040A1C" strokeWidth="1.8" transform="rotate(18 159 110)" />
        <circle cx="156" cy="100" r="10" fill={`url(#${uid}-scale)`} stroke="#041E0E" strokeWidth="2.4" />
      </g>

      {/* Heavy Olympic Barbell */}
      <g className={isStamp ? '' : 'gator-barbell-lift'}>
        <rect x="18" y="96" width="164" height="7" rx="3.5" fill={`url(#${uid}-steel)`} stroke="#040A1C" strokeWidth="1.5" />
        {/* Left 45lb Plate */}
        <rect x="12" y="74" width="14" height="50" rx="4" fill={`url(#${uid}-plate)`} stroke="#040A1C" strokeWidth="2" />
        <rect x="14" y="80" width="10" height="4" fill="#FA4616" />
        <rect x="26" y="82" width="6" height="34" rx="2" fill={`url(#${uid}-steel)`} stroke="#040A1C" strokeWidth="1.5" />
        {/* Right 45lb Plate */}
        <rect x="174" y="74" width="14" height="50" rx="4" fill={`url(#${uid}-plate)`} stroke="#040A1C" strokeWidth="2" />
        <rect x="176" y="80" width="10" height="4" fill="#0021A5" />
        <rect x="168" y="82" width="6" height="34" rx="2" fill={`url(#${uid}-steel)`} stroke="#040A1C" strokeWidth="1.5" />

        {/* Metallic Glint Sweep */}
        {!isStamp && (
          <rect
            className="gator-glint"
            x="64"
            y="95"
            width="28"
            height="9"
            rx="4"
            fill={`url(#${uid}-glint)`}
          />
        )}
      </g>

      {/* Gator Head */}
      <GatorHead uid={uid} x={100} y={58} scale={0.96} />
    </g>
  )
}

/** PR Celebration Pose: Roaring Overhead PR Barbell Hoist */
function PrGatorBody({ uid }: { uid: string }) {
  return (
    <g>
      {/* Torso & UF Tank */}
      <path
        d="M54 98 C58 76 74 68 100 68 C126 68 142 76 146 98 L138 172 L62 172 Z"
        fill={`url(#${uid}-ufBlue)`}
        stroke="#040A1C"
        strokeWidth="2.5"
      />
      <path d="M62 158 L138 158" stroke="#FA4616" strokeWidth="5" />
      <text
        x="100"
        y="138"
        textAnchor="middle"
        fill="#FFFFFF"
        fontFamily="Impact, 'Bebas Neue', Arial Black, sans-serif"
        fontWeight="900"
        fontSize="24"
        letterSpacing="2"
      >
        UF
      </text>

      {/* Raised Power Arms Overhead */}
      <g className="gator-flex-left">
        <path
          d="M64 96 C44 88 38 66 46 42 L60 46 C56 64 60 76 74 84 Z"
          fill={`url(#${uid}-scale)`}
          stroke="#041E0E"
          strokeWidth="2.4"
        />
        <rect x="44" y="45" width="16" height="8" rx="3" fill={`url(#${uid}-ufOrange)`} stroke="#040A1C" strokeWidth="1.6" />
        <circle cx="52" cy="38" r="9.5" fill={`url(#${uid}-scale)`} stroke="#041E0E" strokeWidth="2.2" />
      </g>
      <g className="gator-flex-right">
        <path
          d="M136 96 C156 88 162 66 154 42 L140 46 C144 64 140 76 126 84 Z"
          fill={`url(#${uid}-scale)`}
          stroke="#041E0E"
          strokeWidth="2.4"
        />
        <rect x="140" y="45" width="16" height="8" rx="3" fill={`url(#${uid}-ufOrange)`} stroke="#040A1C" strokeWidth="1.6" />
        <circle cx="148" cy="38" r="9.5" fill={`url(#${uid}-scale)`} stroke="#041E0E" strokeWidth="2.2" />
      </g>

      {/* Bending PR Barbell Overhead */}
      <path
        d="M14 44 Q100 32 186 44"
        stroke={`url(#${uid}-steel)`}
        strokeWidth="7"
        strokeLinecap="round"
        fill="none"
      />
      {/* Stacked PR Plates Left */}
      <rect x="10" y="20" width="12" height="48" rx="3.5" fill={`url(#${uid}-plate)`} stroke="#040A1C" strokeWidth="2" />
      <rect x="12" y="26" width="8" height="4" fill="#FA4616" />
      <rect x="23" y="25" width="10" height="38" rx="3" fill={`url(#${uid}-ufBlue)`} stroke="#040A1C" strokeWidth="1.8" />
      {/* Stacked PR Plates Right */}
      <rect x="178" y="20" width="12" height="48" rx="3.5" fill={`url(#${uid}-plate)`} stroke="#040A1C" strokeWidth="2" />
      <rect x="180" y="26" width="8" height="4" fill="#FA4616" />
      <rect x="167" y="25" width="10" height="38" rx="3" fill={`url(#${uid}-ufBlue)`} stroke="#040A1C" strokeWidth="1.8" />

      {/* Roaring Gator Head */}
      <GatorHead uid={uid} roar x={100} y={76} scale={0.95} />
    </g>
  )
}

/** PR Varsity Burst Rays */
function PrRays({ uid }: { uid: string }) {
  return (
    <g className="gator-pr-rays" opacity="0.55">
      {[0, 30, 60, 90, 120, 150, 180, 210, 240, 270, 300, 330].map((deg, idx) => (
        <polygon
          key={deg}
          points="100,100 94,10 106,10"
          fill={idx % 2 === 0 ? `url(#${uid}-ufOrange)` : `url(#${uid}-ufBlue)`}
          transform={`rotate(${deg} 100 100)`}
        />
      ))}
    </g>
  )
}

/** Championship UF Varsity Octagon/Shield Stamp Frame */
function StampFrame({ uid }: { uid: string }) {
  return (
    <g>
      <polygon
        points="64,10 136,10 186,60 186,140 136,190 64,190 14,140 14,60"
        fill="#040A1C"
        stroke={`url(#${uid}-ufOrange)`}
        strokeWidth="5"
      />
      <polygon
        points="68,18 132,18 178,64 178,136 132,182 68,182 22,136 22,64"
        fill="none"
        stroke={`url(#${uid}-ufBlue)`}
        strokeWidth="4"
      />
      <text
        x="100"
        y="34"
        textAnchor="middle"
        fill="#FA4616"
        fontFamily="Impact, 'Bebas Neue', Arial Black, sans-serif"
        fontWeight="900"
        fontSize="13"
        letterSpacing="2"
      >
        UF · IRONLOG
      </text>
      <rect x="40" y="158" width="120" height="20" rx="6" fill={`url(#${uid}-ufOrange)`} stroke="#040A1C" strokeWidth="2" />
      <text
        x="100"
        y="172"
        textAnchor="middle"
        fill="#FFFFFF"
        fontFamily="Impact, 'Bebas Neue', Arial Black, sans-serif"
        fontWeight="900"
        fontSize="12"
        letterSpacing="1.8"
      >
        SWOLE GATOR
      </text>
    </g>
  )
}

/** Spotter Pose: Friendly Hyped Gator with Stopwatch & Fist Pump */
function SpotterGatorBody({ uid }: { uid: string }) {
  return (
    <g>
      {/* Torso & UF Tank */}
      <path
        d="M52 104 C56 82 72 72 100 72 C128 72 144 82 148 104 L142 174 L58 174 Z"
        fill={`url(#${uid}-ufBlue)`}
        stroke="#040A1C"
        strokeWidth="2.5"
      />
      <path d="M58 160 L142 160" stroke="#FA4616" strokeWidth="5" />
      <text
        x="100"
        y="142"
        textAnchor="middle"
        fill="#FFFFFF"
        fontFamily="Impact, 'Bebas Neue', Arial Black, sans-serif"
        fontWeight="900"
        fontSize="24"
        letterSpacing="2"
      >
        UF
      </text>

      {/* Left Arm Holding UF Orange Stopwatch */}
      <path
        d="M60 100 C40 104 30 122 36 140 L50 138 C48 126 54 116 64 114 Z"
        fill={`url(#${uid}-scale)`}
        stroke="#041E0E"
        strokeWidth="2.4"
      />
      <circle cx="42" cy="144" r="9" fill={`url(#${uid}-scale)`} stroke="#041E0E" strokeWidth="2.2" />
      {/* Stopwatch */}
      <circle cx="32" cy="156" r="15" fill={`url(#${uid}-ufOrange)`} stroke="#040A1C" strokeWidth="2.2" />
      <circle cx="32" cy="156" r="10.5" fill="#040A1C" />
      <path d="M32 156 L32 149 M32 156 L38 156" stroke="#FFFFFF" strokeWidth="2" strokeLinecap="round" />
      <rect x="29" y="138" width="6" height="4" rx="1" fill="#94A3B8" />

      {/* Right Arm Fist Pump */}
      <g className="gator-spotter-wave">
        <path
          d="M140 100 C162 96 172 80 166 60 L152 64 C154 78 148 88 136 90 Z"
          fill={`url(#${uid}-scale)`}
          stroke="#041E0E"
          strokeWidth="2.4"
        />
        <rect x="150" y="65" width="16" height="8" rx="3" fill={`url(#${uid}-ufOrange)`} stroke="#040A1C" strokeWidth="1.6" />
        <circle cx="159" cy="56" r="10" fill={`url(#${uid}-scale)`} stroke="#041E0E" strokeWidth="2.2" />
      </g>

      {/* Gator Head */}
      <GatorHead uid={uid} x={100} y={62} scale={0.96} />
    </g>
  )
}
