import { useCallback, useEffect, useRef, useState, type PointerEvent as ReactPointerEvent } from 'react'
import { IronMascot } from '../mascot'
import './apex-stage.css'

export interface ApexKineticStageProps {
  active?: boolean
  gatorMode?: boolean
  onSurge?: () => void
}

interface RGB {
  r: number
  g: number
  b: number
}

interface StagePalette {
  primary: RGB
  secondary: RGB
  tertiary: RGB
  core: RGB
  floorLine: RGB
  isLight: boolean
  isGator: boolean
}

interface Vec3 {
  x: number
  y: number
  z: number
}

interface ProjPoint {
  x: number
  y: number
  z: number
  scale: number
}

interface GyroRing {
  radiusScale: number
  tiltX: number
  tiltY: number
  tiltZ: number
  spin: number
  baseSpeed: number
  wobbleAmp: number
  wobbleFreq: number
  wobblePhase: number
  style: 'primary' | 'secondary' | 'bracket'
  nodeCount: number
  arcCount: number
  hasTicks: boolean
}

interface Shockwave3D {
  age: number
  duration: number
  delay: number
  startR: number
  maxR: number
  tiltX: number
  tiltZ: number
  yOffset: number
  colorRole: 'primary' | 'secondary' | 'tertiary'
}

interface LightningBolt {
  age: number
  duration: number
  points: Vec3[]
  branches: Vec3[][]
  colorRole: 'primary' | 'secondary'
  flickerPhase: number
}

interface Particle3D {
  kind: 'ambient' | 'ember' | 'shard'
  x: number
  y: number
  z: number
  px: number
  py: number
  pz: number
  vx: number
  vy: number
  vz: number
  size: number
  life: number
  maxLife: number
  colorRole: 'primary' | 'secondary' | 'tertiary' | 'core'
  orbitAngle: number
  orbitRadius: number
  orbitSpeed: number
  orbitY: number
  orbitTilt: number
  magnetBoost: number
}

const DEFAULT_ACCENT: RGB = { r: 163, g: 230, b: 53 }
const SURGE_PARTICLE_COUNT = 68
const MAX_BURST_PARTICLES = 170
const AMBIENT_EMBER_COUNT = 26
const RING_SEGMENTS = 92

function clamp(value: number, min: number, max: number): number {
  return value < min ? min : value > max ? max : value
}

function rgba(c: RGB, alpha: number): string {
  const a = clamp(alpha, 0, 1)
  return `rgba(${c.r},${c.g},${c.b},${a.toFixed(3)})`
}

function mixRgb(a: RGB, b: RGB, ratio: number): RGB {
  const t = clamp(ratio, 0, 1)
  return {
    r: Math.round(a.r + (b.r - a.r) * t),
    g: Math.round(a.g + (b.g - a.g) * t),
    b: Math.round(a.b + (b.b - a.b) * t),
  }
}

function parseColor(input: string, fallback: RGB): RGB {
  const raw = input.trim()
  if (!raw) return fallback
  if (raw.startsWith('#')) {
    const hex = raw.slice(1)
    if (hex.length === 3) {
      const r = parseInt(hex[0] + hex[0], 16)
      const g = parseInt(hex[1] + hex[1], 16)
      const b = parseInt(hex[2] + hex[2], 16)
      if (!Number.isNaN(r) && !Number.isNaN(g) && !Number.isNaN(b)) return { r, g, b }
    } else if (hex.length >= 6) {
      const r = parseInt(hex.slice(0, 2), 16)
      const g = parseInt(hex.slice(2, 4), 16)
      const b = parseInt(hex.slice(4, 6), 16)
      if (!Number.isNaN(r) && !Number.isNaN(g) && !Number.isNaN(b)) return { r, g, b }
    }
  }
  const match = raw.match(/rgba?\(\s*([\d.]+)\s*[,\s]\s*([\d.]+)\s*[,\s]\s*([\d.]+)/i)
  if (match) {
    return {
      r: clamp(Math.round(Number(match[1])), 0, 255),
      g: clamp(Math.round(Number(match[2])), 0, 255),
      b: clamp(Math.round(Number(match[3])), 0, 255),
    }
  }
  return fallback
}

function isReducedMotionActive(): boolean {
  if (typeof document === 'undefined') return false
  return (
    document.documentElement.dataset.motion === 'reduced' ||
    Boolean(window.matchMedia?.('(prefers-reduced-motion: reduce)').matches)
  )
}

function readStagePalette(gatorProp: boolean): StagePalette {
  const root = document.documentElement
  const computed = getComputedStyle(root)
  const isLight = root.dataset.theme === 'light'
  const isGator = gatorProp || root.dataset.brand === 'gator'

  if (isGator) {
    if (isLight) {
      return {
        primary: { r: 234, g: 62, b: 16 },     // UF Vibrant Orange (crisp on light)
        secondary: { r: 0, g: 33, b: 165 },    // UF Gator Blue
        tertiary: { r: 37, g: 99, b: 235 },    // UF Royal Blue highlight
        core: { r: 255, g: 247, b: 237 },      // White-hot amber core
        floorLine: { r: 0, g: 33, b: 165 },
        isLight: true,
        isGator: true,
      }
    }
    return {
      primary: { r: 250, g: 70, b: 22 },       // UF Vibrant Orange #FA4616
      secondary: { r: 37, g: 99, b: 235 },     // UF Royal Blue #2563EB
      tertiary: { r: 96, g: 165, b: 250 },     // Electric Stadium Blue
      core: { r: 255, g: 248, b: 236 },        // White-hot stadium core
      floorLine: { r: 59, g: 130, b: 246 },
      isLight: false,
      isGator: true,
    }
  }

  const accent = parseColor(computed.getPropertyValue('--accent'), DEFAULT_ACCENT)
  const accentInk = parseColor(computed.getPropertyValue('--accent-ink'), accent)

  if (isLight) {
    // High-contrast emerald/accent + machined gunmetal so bright themes stay punchy on white cards
    const crispPrimary = mixRgb(accentInk, accent, 0.28)
    const gunmetal: RGB = { r: 44, g: 56, b: 74 }
    return {
      primary: crispPrimary,
      secondary: gunmetal,
      tertiary: mixRgb(crispPrimary, { r: 15, g: 23, b: 42 }, 0.18),
      core: { r: 255, g: 255, b: 255 },
      floorLine: gunmetal,
      isLight: true,
      isGator: false,
    }
  }

  // Swole Chimp Dark: electric --accent + molten steel-silver + white-hot lightning cores
  const moltenSteel = mixRgb({ r: 208, g: 220, b: 234 }, accent, 0.16)
  const plasmaBright = mixRgb(accent, { r: 244, g: 255, b: 226 }, 0.42)
  return {
    primary: accent,
    secondary: moltenSteel,
    tertiary: plasmaBright,
    core: { r: 255, g: 255, b: 255 },
    floorLine: mixRgb(accent, { r: 160, g: 178, b: 204 }, 0.45),
    isLight: false,
    isGator: false,
  }
}

function getRoleColor(
  palette: StagePalette,
  role: 'primary' | 'secondary' | 'tertiary' | 'core',
): RGB {
  if (role === 'primary') return palette.primary
  if (role === 'secondary') return palette.secondary
  if (role === 'tertiary') return palette.tertiary
  return palette.core
}

function rotate3D(
  x: number,
  y: number,
  z: number,
  rx: number,
  ry: number,
  rz: number,
): Vec3 {
  // Rotate X
  const cx = Math.cos(rx)
  const sx = Math.sin(rx)
  const y1 = y * cx - z * sx
  const z1 = y * sx + z * cx
  // Rotate Y
  const cy = Math.cos(ry)
  const sy = Math.sin(ry)
  const x2 = x * cy + z1 * sy
  const z2 = -x * sy + z1 * cy
  // Rotate Z
  const cz = Math.cos(rz)
  const sz = Math.sin(rz)
  return {
    x: x2 * cz - y1 * sz,
    y: x2 * sz + y1 * cz,
    z: z2,
  }
}

function project3D(p: Vec3, cx: number, cy: number, fov: number): ProjPoint {
  // z > 0 is closer to camera (front canvas, larger scale); z < 0 is behind mascot (back canvas)
  const denom = Math.max(fov * 0.32, fov - p.z)
  const scale = fov / denom
  return {
    x: cx + p.x * scale,
    y: cy + p.y * scale,
    z: p.z,
    scale,
  }
}

function createInitialRings(): GyroRing[] {
  return [
    {
      radiusScale: 1.09,
      tiltX: 0.41,
      tiltY: 0.14,
      tiltZ: -0.24,
      spin: 0.35,
      baseSpeed: 0.62,
      wobbleAmp: 0.08,
      wobbleFreq: 0.72,
      wobblePhase: 0.0,
      style: 'primary',
      nodeCount: 3,
      arcCount: 2,
      hasTicks: true,
    },
    {
      radiusScale: 0.96,
      tiltX: 1.02,
      tiltY: -0.28,
      tiltZ: 0.56,
      spin: 1.9,
      baseSpeed: -0.84,
      wobbleAmp: 0.11,
      wobbleFreq: 0.58,
      wobblePhase: 1.7,
      style: 'secondary',
      nodeCount: 2,
      arcCount: 2,
      hasTicks: true,
    },
    {
      radiusScale: 1.21,
      tiltX: -0.86,
      tiltY: 0.34,
      tiltZ: -0.66,
      spin: 3.4,
      baseSpeed: 0.42,
      wobbleAmp: 0.07,
      wobbleFreq: 0.45,
      wobblePhase: 3.1,
      style: 'bracket',
      nodeCount: 2,
      arcCount: 1,
      hasTicks: false,
    },
  ]
}

function createAmbientEmbers(baseR: number): Particle3D[] {
  const embers: Particle3D[] = []
  const roles: Array<Particle3D['colorRole']> = ['primary', 'secondary', 'tertiary', 'primary']
  for (let i = 0; i < AMBIENT_EMBER_COUNT; i++) {
    const angle = (i / AMBIENT_EMBER_COUNT) * Math.PI * 2 + (i % 3) * 0.27
    const radius = baseR * (0.76 + ((i * 37) % 100) / 145)
    const orbitY = (((i * 53) % 100) / 100 - 0.5) * baseR * 1.18
    const x = Math.cos(angle) * radius
    const z = Math.sin(angle) * radius
    embers.push({
      kind: 'ambient',
      x,
      y: orbitY,
      z,
      px: x,
      py: orbitY,
      pz: z,
      vx: 0,
      vy: 0,
      vz: 0,
      size: 1.45 + ((i * 19) % 10) * 0.18,
      life: 1,
      maxLife: 1,
      colorRole: roles[i % roles.length],
      orbitAngle: angle,
      orbitRadius: radius,
      orbitSpeed: (i % 2 === 0 ? 1 : -1) * (0.34 + ((i * 29) % 10) * 0.045),
      orbitY,
      orbitTilt: (((i * 41) % 100) / 100 - 0.5) * 0.55,
      magnetBoost: 0,
    })
  }
  return embers
}

function midpointDisplace3D(a: Vec3, b: Vec3, depth: number, jitter: number): Vec3[] {
  if (depth <= 0) return [a, b]
  const mid: Vec3 = {
    x: (a.x + b.x) * 0.5 + (Math.random() - 0.5) * jitter,
    y: (a.y + b.y) * 0.5 + (Math.random() - 0.5) * jitter,
    z: (a.z + b.z) * 0.5 + (Math.random() - 0.5) * jitter * 0.85,
  }
  const left = midpointDisplace3D(a, mid, depth - 1, jitter * 0.56)
  const right = midpointDisplace3D(mid, b, depth - 1, jitter * 0.56)
  return [...left.slice(0, -1), ...right]
}

function createLightningBolt(
  baseR: number,
  colorRole: 'primary' | 'secondary',
  duration = 0.28,
): LightningBolt {
  const a1 = Math.random() * Math.PI * 2
  const a2 = a1 + (Math.random() > 0.5 ? 1 : -1) * (1.1 + Math.random() * 1.45)
  const r1 = baseR * (0.86 + Math.random() * 0.34)
  const r2 = baseR * (0.86 + Math.random() * 0.34)
  const start: Vec3 = {
    x: Math.cos(a1) * r1,
    y: (Math.random() - 0.5) * baseR * 0.96,
    z: Math.sin(a1) * r1,
  }
  const end: Vec3 = {
    x: Math.cos(a2) * r2,
    y: (Math.random() - 0.5) * baseR * 0.96,
    z: Math.sin(a2) * r2,
  }
  const points = midpointDisplace3D(start, end, 4, baseR * 0.38)
  const branches: Vec3[][] = []
  const branchCount = 1 + (Math.random() > 0.45 ? 1 : 0)
  for (let b = 0; b < branchCount; b++) {
    const idx = Math.floor(points.length * (0.28 + b * 0.3 + Math.random() * 0.14))
    const origin = points[clamp(idx, 1, points.length - 2)]
    const dirAngle = Math.random() * Math.PI * 2
    const len = baseR * (0.34 + Math.random() * 0.28)
    const tip: Vec3 = {
      x: origin.x + Math.cos(dirAngle) * len,
      y: origin.y + (Math.random() - 0.5) * len * 0.9,
      z: origin.z + Math.sin(dirAngle) * len * 0.8,
    }
    branches.push(midpointDisplace3D(origin, tip, 3, baseR * 0.18))
  }
  return {
    age: 0,
    duration,
    points,
    branches,
    colorRole,
    flickerPhase: Math.random() * Math.PI * 2,
  }
}

export function ApexKineticStage({
  active = false,
  gatorMode = false,
  onSurge,
}: ApexKineticStageProps) {
  const stageRef = useRef<HTMLDivElement>(null)
  const backCanvasRef = useRef<HTMLCanvasElement>(null)
  const frontCanvasRef = useRef<HTMLCanvasElement>(null)
  const mascotLayerRef = useRef<HTMLDivElement>(null)

  const [surging, setSurging] = useState(false)
  const surgeUiTimerRef = useRef<number | undefined>(undefined)
  const surgeUiRafRef = useRef<number | undefined>(undefined)

  const activeRef = useRef(active)
  const gatorRef = useRef(gatorMode)
  const onSurgeRef = useRef(onSurge)

  useEffect(() => {
    activeRef.current = active
  }, [active])
  useEffect(() => {
    gatorRef.current = gatorMode
  }, [gatorMode])
  useEffect(() => {
    onSurgeRef.current = onSurge
  }, [onSurge])

  // Shared mutable simulation triggers exposed to event handlers without re-mounting canvas engine
  const engineTriggerRef = useRef<((opts?: { seismic?: boolean }) => void) | null>(null)
  const refreshEngineRef = useRef<(() => void) | null>(null)
  const pointerStateRef = useRef({
    active: false,
    x: 0,
    y: 0,
    nx: 0,
    ny: 0,
  })

  const fireRecoilUi = useCallback((seismic: boolean) => {
    if (isReducedMotionActive()) return
    if (surgeUiTimerRef.current !== undefined) window.clearTimeout(surgeUiTimerRef.current)
    if (surgeUiRafRef.current !== undefined) window.cancelAnimationFrame(surgeUiRafRef.current)
    setSurging(false)
    surgeUiRafRef.current = window.requestAnimationFrame(() => {
      surgeUiRafRef.current = undefined
      setSurging(true)
    })
    surgeUiTimerRef.current = window.setTimeout(() => {
      setSurging(false)
      surgeUiTimerRef.current = undefined
    }, 560)
    if (seismic) onSurgeRef.current?.()
  }, [])

  const triggerSurge = useCallback((seismic = true) => {
    engineTriggerRef.current?.({ seismic })
    fireRecoilUi(seismic)
  }, [fireRecoilUi])

  useEffect(() => {
    return () => {
      if (surgeUiTimerRef.current !== undefined) window.clearTimeout(surgeUiTimerRef.current)
      if (surgeUiRafRef.current !== undefined) window.cancelAnimationFrame(surgeUiRafRef.current)
    }
  }, [])

  const handlePointerMove = (e: ReactPointerEvent<HTMLDivElement>) => {
    const el = stageRef.current
    if (!el) return
    const rect = el.getBoundingClientRect()
    if (!rect.width || !rect.height) return
    const localX = e.clientX - rect.left
    const localY = e.clientY - rect.top
    const nx = clamp((localX / rect.width - 0.5) * 2, -1, 1)
    const ny = clamp((localY / rect.height - 0.5) * 2, -1, 1)
    pointerStateRef.current = { active: true, x: localX, y: localY, nx, ny }

    if (!isReducedMotionActive() && mascotLayerRef.current) {
      mascotLayerRef.current.style.setProperty('--tilt-x', `${(-ny * 9).toFixed(2)}deg`)
      mascotLayerRef.current.style.setProperty('--tilt-y', `${(nx * 11).toFixed(2)}deg`)
    }
  }

  const handlePointerLeave = () => {
    pointerStateRef.current.active = false
    pointerStateRef.current.nx = 0
    pointerStateRef.current.ny = 0
    if (mascotLayerRef.current) {
      mascotLayerRef.current.style.setProperty('--tilt-x', '0deg')
      mascotLayerRef.current.style.setProperty('--tilt-y', '0deg')
    }
  }

  const handleStageClick = (e: React.MouseEvent<HTMLDivElement>) => {
    if ((e.target as HTMLElement).closest('.apex-mascot-layer')) return
    triggerSurge(true)
  }

  useEffect(() => {
    const onExternalSurge = () => {
      engineTriggerRef.current?.({ seismic: true })
      fireRecoilUi(true)
    }
    window.addEventListener('ironlog:apex-surge', onExternalSurge)
    return () => {
      window.removeEventListener('ironlog:apex-surge', onExternalSurge)
    }
  }, [fireRecoilUi])

  // Main dual-layer 3D HiDPI Canvas simulation engine
  useEffect(() => {
    const stageEl = stageRef.current
    const backCanvas = backCanvasRef.current
    const frontCanvas = frontCanvasRef.current
    if (!stageEl || !backCanvas || !frontCanvas) return

    const isUnmockedJsdom =
      typeof navigator !== 'undefined' &&
      /jsdom/i.test(navigator.userAgent) &&
      !('mock' in backCanvas.getContext)
    if (isUnmockedJsdom) return

    let backCtx: CanvasRenderingContext2D | null = null
    let frontCtx: CanvasRenderingContext2D | null = null
    try {
      backCtx = backCanvas.getContext('2d')
      frontCtx = frontCanvas.getContext('2d')
    } catch {
      return
    }
    if (!backCtx || !frontCtx) return

    const bCtx = backCtx
    const fCtx = frontCtx

    let width = 360
    let height = 240
    let baseR = 96
    let palette = readStagePalette(gatorRef.current)
    let rings = createInitialRings()
    let ambientEmbers = createAmbientEmbers(baseR)
    const burstParticles: Particle3D[] = []
    const shockwaves: Shockwave3D[] = []
    const bolts: LightningBolt[] = []

    let simTime = 1.35
    let surgeFlash = 0
    let ringBoost = 0
    let curParallaxX = 0
    let curParallaxY = 0
    let nextAmbientArcAt = 2.4
    let entranceTriggered = false

    let rafId: number | undefined
    let entranceTimer: number | undefined
    let entranceRaf1: number | undefined
    let entranceRaf2: number | undefined
    let lastTimestamp = 0
    let destroyed = false

    const resize = () => {
      const rect = stageEl.getBoundingClientRect()
      const nextW = Math.max(180, Math.round(rect.width || stageEl.clientWidth || 360))
      const nextH = Math.max(180, Math.round(rect.height || stageEl.clientHeight || 240))
      const dpr = Math.min(window.devicePixelRatio || 1, 2)
      width = nextW
      height = nextH
      baseR = Math.max(66, Math.min(width * 0.41, height * 0.415))

      for (const canvas of [backCanvas, frontCanvas]) {
        const targetW = Math.round(width * dpr)
        const targetH = Math.round(height * dpr)
        if (canvas.width !== targetW || canvas.height !== targetH) {
          canvas.width = targetW
          canvas.height = targetH
        }
      }
      bCtx.setTransform(dpr, 0, 0, dpr, 0, 0)
      fCtx.setTransform(dpr, 0, 0, dpr, 0, 0)

      // Rescale ambient orbit radii smoothly to match stage size
      for (let i = 0; i < ambientEmbers.length; i++) {
        ambientEmbers[i].orbitRadius = baseR * (0.76 + ((i * 37) % 100) / 145)
        ambientEmbers[i].orbitY = (((i * 53) % 100) / 100 - 0.5) * baseR * 1.18
      }
    }

    const spawnSurge = () => {
      if (isReducedMotionActive()) return
      surgeFlash = 1.0
      ringBoost = 2.4

      // 3 staggered 3D perspective shockwave rings
      shockwaves.push(
        {
          age: 0,
          duration: 0.72,
          delay: 0,
          startR: baseR * 0.28,
          maxR: baseR * 1.62,
          tiltX: 0.42,
          tiltZ: -0.18,
          yOffset: 0,
          colorRole: 'primary',
        },
        {
          age: 0,
          duration: 0.78,
          delay: 0.095,
          startR: baseR * 0.24,
          maxR: baseR * 1.56,
          tiltX: 1.06,
          tiltZ: 0.44,
          yOffset: -baseR * 0.04,
          colorRole: 'secondary',
        },
        {
          age: 0,
          duration: 0.84,
          delay: 0.19,
          startR: baseR * 0.26,
          maxR: baseR * 1.72,
          tiltX: 0.06,
          tiltZ: 0,
          yOffset: baseR * 0.76,
          colorRole: 'tertiary',
        },
      )

      // 3 jagged recursive midpoint-displacement electric lightning bolts
      bolts.push(
        createLightningBolt(baseR, 'primary', 0.30),
        createLightningBolt(baseR, 'secondary', 0.27),
        createLightningBolt(baseR, 'primary', 0.34),
      )

      // 68 high-velocity glowing embers & angular plasma shards in 3D space
      const roles: Array<Particle3D['colorRole']> = [
        'primary',
        'primary',
        'secondary',
        'tertiary',
        'core',
      ]
      for (let i = 0; i < SURGE_PARTICLE_COUNT; i++) {
        if (burstParticles.length >= MAX_BURST_PARTICLES) {
          burstParticles.shift()
        }
        const theta = Math.random() * Math.PI * 2
        const phi = Math.acos(2 * Math.random() - 1)
        const speed = baseR * (2.1 + Math.random() * 3.7)
        const sx = Math.sin(phi) * Math.cos(theta)
        const sy = Math.cos(phi) * 0.86 - 0.18
        const sz = Math.sin(phi) * Math.sin(theta)
        const startDist = baseR * (0.08 + Math.random() * 0.26)
        const x = sx * startDist
        const y = sy * startDist
        const z = sz * startDist
        const isShard = i % 3 === 0

        burstParticles.push({
          kind: isShard ? 'shard' : 'ember',
          x,
          y,
          z,
          px: x,
          py: y,
          pz: z,
          vx: sx * speed,
          vy: sy * speed,
          vz: sz * speed,
          size: isShard ? 2.8 + Math.random() * 3.4 : 1.7 + Math.random() * 2.8,
          life: 1,
          maxLife: 0.62 + Math.random() * 0.62,
          colorRole: roles[i % roles.length],
          orbitAngle: 0,
          orbitRadius: 0,
          orbitSpeed: 0,
          orbitY: 0,
          orbitTilt: 0,
          magnetBoost: 0,
        })
      }
    }

    engineTriggerRef.current = () => {
      spawnSurge()
    }

    const drawVolumetricCore = (ctx: CanvasRenderingContext2D, cx: number, cy: number, overdrive: boolean) => {
      const intensity = (overdrive ? 1.7 : 1.0) + surgeFlash * 1.15
      const pulse = 0.92 + Math.sin(simTime * (overdrive ? 4.4 : 2.3)) * 0.08
      const coreR = baseR * (1.28 + surgeFlash * 0.24) * pulse
      const coreY = cy - baseR * 0.03

      ctx.save()
      ctx.globalCompositeOperation = palette.isLight ? 'source-over' : 'lighter'

      if (palette.isGator) {
        // Dual-tone UF Orange + UF Royal Blue stadium energy aura
        const orangeGrad = ctx.createRadialGradient(
          cx + baseR * 0.22,
          coreY - baseR * 0.14,
          baseR * 0.06,
          cx + baseR * 0.12,
          coreY,
          coreR * 0.96,
        )
        const aO = (palette.isLight ? 0.16 : 0.25) * Math.min(intensity, 2.2)
        orangeGrad.addColorStop(0, rgba(palette.primary, aO))
        orangeGrad.addColorStop(0.5, rgba(palette.primary, aO * 0.36))
        orangeGrad.addColorStop(1, rgba(palette.primary, 0))
        ctx.fillStyle = orangeGrad
        ctx.beginPath()
        ctx.arc(cx + baseR * 0.12, coreY, coreR * 0.96, 0, Math.PI * 2)
        ctx.fill()

        const blueGrad = ctx.createRadialGradient(
          cx - baseR * 0.22,
          coreY + baseR * 0.1,
          baseR * 0.06,
          cx - baseR * 0.1,
          coreY,
          coreR,
        )
        const aB = (palette.isLight ? 0.14 : 0.28) * Math.min(intensity, 2.2)
        blueGrad.addColorStop(0, rgba(palette.secondary, aB))
        blueGrad.addColorStop(0.55, rgba(palette.secondary, aB * 0.34))
        blueGrad.addColorStop(1, rgba(palette.secondary, 0))
        ctx.fillStyle = blueGrad
        ctx.beginPath()
        ctx.arc(cx - baseR * 0.1, coreY, coreR, 0, Math.PI * 2)
        ctx.fill()
      } else {
        const grad = ctx.createRadialGradient(cx, coreY, baseR * 0.06, cx, coreY, coreR)
        const baseAlpha = (palette.isLight ? 0.16 : 0.28) * Math.min(intensity, 2.2)
        grad.addColorStop(0, rgba(palette.tertiary, baseAlpha * 1.12))
        grad.addColorStop(0.36, rgba(palette.primary, baseAlpha * 0.62))
        grad.addColorStop(0.72, rgba(palette.primary, baseAlpha * 0.15))
        grad.addColorStop(1, rgba(palette.primary, 0))
        ctx.fillStyle = grad
        ctx.beginPath()
        ctx.arc(cx, coreY, coreR, 0, Math.PI * 2)
        ctx.fill()
      }

      if (surgeFlash > 0.02) {
        const flashGrad = ctx.createRadialGradient(cx, coreY, 0, cx, coreY, baseR * (0.9 + surgeFlash * 0.45))
        flashGrad.addColorStop(0, rgba(palette.core, (palette.isLight ? 0.36 : 0.55) * surgeFlash))
        flashGrad.addColorStop(0.45, rgba(palette.primary, (palette.isLight ? 0.24 : 0.34) * surgeFlash))
        flashGrad.addColorStop(1, rgba(palette.primary, 0))
        ctx.fillStyle = flashGrad
        ctx.beginPath()
        ctx.arc(cx, coreY, baseR * (0.9 + surgeFlash * 0.45), 0, Math.PI * 2)
        ctx.fill()
      }

      ctx.restore()
    }

    const drawFloorGrid = (
      ctx: CanvasRenderingContext2D,
      cx: number,
      cy: number,
      fov: number,
      overdrive: boolean,
    ) => {
      const floorY = baseR * 0.77
      const tiltX = 0.22 + curParallaxY * 0.22
      const tiltY = curParallaxX * 0.24
      const ringRadii = [0.34, 0.66, 0.98, 1.32]
      const pulseWave = (simTime * (overdrive ? 0.85 : 0.48)) % 1

      ctx.save()
      ctx.globalCompositeOperation = palette.isLight ? 'source-over' : 'lighter'

      // Reactor pad glow directly under mascot feet/torso
      const padCenter = project3D(
        rotate3D(0, floorY, 0, tiltX, tiltY, 0),
        cx,
        cy,
        fov,
      )
      const padRx = baseR * 0.88 * padCenter.scale
      const padRy = baseR * 0.22 * padCenter.scale
      ctx.save()
      ctx.translate(padCenter.x, padCenter.y)
      ctx.scale(1, Math.max(0.18, padRy / Math.max(1, padRx)))
      const padGrad = ctx.createRadialGradient(0, 0, padRx * 0.05, 0, 0, padRx)
      const padAlpha = (palette.isLight ? 0.2 : 0.32) * (overdrive ? 1.45 : 1) + surgeFlash * 0.28
      padGrad.addColorStop(0, rgba(palette.primary, Math.min(0.7, padAlpha)))
      padGrad.addColorStop(0.55, rgba(palette.secondary, Math.min(0.4, padAlpha * 0.42)))
      padGrad.addColorStop(1, rgba(palette.primary, 0))
      ctx.fillStyle = padGrad
      ctx.beginPath()
      ctx.arc(0, 0, padRx, 0, Math.PI * 2)
      ctx.fill()
      ctx.restore()

      // Concentric perspective floor rings
      for (let rIdx = 0; rIdx < ringRadii.length; rIdx++) {
        const radius = ringRadii[rIdx] * baseR
        const waveProximity = 1 - clamp(Math.abs(pulseWave - rIdx / ringRadii.length) * 3.1, 0, 1)
        const baseAlpha = (palette.isLight ? 0.19 : 0.22) * (1 - rIdx * 0.16)
        const ringAlpha = baseAlpha + waveProximity * (palette.isLight ? 0.22 : 0.32) + surgeFlash * 0.22
        const color = rIdx === 0 || waveProximity > 0.45 ? palette.primary : palette.floorLine

        ctx.beginPath()
        const steps = 56
        for (let s = 0; s <= steps; s++) {
          const a = (s / steps) * Math.PI * 2
          const pt = rotate3D(Math.cos(a) * radius, floorY, Math.sin(a) * radius, tiltX, tiltY, 0)
          const proj = project3D(pt, cx, cy, fov)
          if (s === 0) ctx.moveTo(proj.x, proj.y)
          else ctx.lineTo(proj.x, proj.y)
        }
        ctx.strokeStyle = rgba(color, ringAlpha)
        ctx.lineWidth = rIdx === 0 ? 1.5 : 1.0 + waveProximity * 0.8
        ctx.stroke()
      }

      // 12 radial precision spokes fanning outward
      const spokeCount = 12
      for (let i = 0; i < spokeCount; i++) {
        const angle = (i / spokeCount) * Math.PI * 2 + simTime * 0.08
        const inner = project3D(
          rotate3D(Math.cos(angle) * baseR * 0.24, floorY, Math.sin(angle) * baseR * 0.24, tiltX, tiltY, 0),
          cx,
          cy,
          fov,
        )
        const outer = project3D(
          rotate3D(Math.cos(angle) * baseR * 1.34, floorY, Math.sin(angle) * baseR * 1.34, tiltX, tiltY, 0),
          cx,
          cy,
          fov,
        )
        const isMajor = i % 3 === 0
        const grad = ctx.createLinearGradient(inner.x, inner.y, outer.x, outer.y)
        const spokeAlpha = (isMajor ? (palette.isLight ? 0.3 : 0.34) : (palette.isLight ? 0.14 : 0.16)) + surgeFlash * 0.16
        grad.addColorStop(0, rgba(isMajor ? palette.primary : palette.floorLine, spokeAlpha))
        grad.addColorStop(1, rgba(palette.floorLine, 0))
        ctx.strokeStyle = grad
        ctx.lineWidth = isMajor ? 1.25 : 0.85
        ctx.beginPath()
        ctx.moveTo(inner.x, inner.y)
        ctx.lineTo(outer.x, outer.y)
        ctx.stroke()
      }

      ctx.restore()
    }

    const drawGyroRings = (cx: number, cy: number, fov: number, overdrive: boolean) => {
      const compOp = palette.isLight ? 'source-over' : 'lighter'
      bCtx.save()
      fCtx.save()
      bCtx.globalCompositeOperation = compOp
      fCtx.globalCompositeOperation = compOp
      bCtx.lineCap = 'round'
      fCtx.lineCap = 'round'

      const surgeRipple = surgeFlash * baseR * 0.045

      for (let rIndex = 0; rIndex < rings.length; rIndex++) {
        const ring = rings[rIndex]
        const R = baseR * ring.radiusScale
        const wobble = Math.sin(simTime * ring.wobbleFreq + ring.wobblePhase) * ring.wobbleAmp
        const rx = ring.tiltX + wobble + curParallaxY * 0.38
        const ry = ring.tiltY + curParallaxX * 0.44
        const rz = ring.tiltZ + curParallaxX * 0.14
        const ringColor =
          ring.style === 'primary'
            ? palette.primary
            : ring.style === 'secondary'
              ? palette.secondary
              : palette.tertiary

        // Precompute ring points in 3D + screen space
        const pts: ProjPoint[] = new Array(RING_SEGMENTS + 1)
        for (let i = 0; i <= RING_SEGMENTS; i++) {
          const theta = (i / RING_SEGMENTS) * Math.PI * 2 + ring.spin
          const radialOffset = surgeRipple > 0.1 ? Math.sin(theta * 5 + simTime * 22) * surgeRipple : 0
          const rr = R + radialOffset
          const localX = Math.cos(theta) * rr
          const localZ = Math.sin(theta) * rr
          const rotated = rotate3D(localX, 0, localZ, rx, ry, rz)
          pts[i] = project3D(rotated, cx, cy, fov)
        }

        // Draw ring segments depth-split cleanly across backCanvas (z < 0) and frontCanvas (z >= 0)
        for (let i = 0; i < RING_SEGMENTS; i++) {
          if (ring.style === 'bracket') {
            // 3 sweeping 82-degree power brackets with clean technical gaps
            const sectorPos = ((i / RING_SEGMENTS) * 3) % 1
            if (sectorPos > 0.72) continue
          }
          const p0 = pts[i]
          const p1 = pts[i + 1]
          const zMid = (p0.z + p1.z) * 0.5
          const targetCtx = zMid < 0 ? bCtx : fCtx
          const depth = clamp((zMid / Math.max(1, R) + 1) * 0.5, 0, 1)
          const scaleMid = (p0.scale + p1.scale) * 0.5
          const baseWidth = (ring.style === 'primary' ? 2.15 : ring.style === 'secondary' ? 1.75 : 1.55) * scaleMid
          const lineW = baseWidth * (0.58 + depth * 0.78)
          const alpha =
            (palette.isLight ? 0.34 : 0.22) +
            depth * (palette.isLight ? 0.54 : 0.68) +
            (overdrive ? 0.1 : 0) +
            surgeFlash * 0.22

          // Soft plasma halo pass (stronger on front arcs)
          if (depth > 0.34) {
            targetCtx.strokeStyle = rgba(ringColor, alpha * (palette.isLight ? 0.24 : 0.34))
            targetCtx.lineWidth = lineW * 3.1
            targetCtx.beginPath()
            targetCtx.moveTo(p0.x, p0.y)
            targetCtx.lineTo(p1.x, p1.y)
            targetCtx.stroke()
          }

          // Crisp machined/plasma core track
          targetCtx.strokeStyle = rgba(ringColor, Math.min(1, alpha))
          targetCtx.lineWidth = lineW
          targetCtx.beginPath()
          targetCtx.moveTo(p0.x, p0.y)
          targetCtx.lineTo(p1.x, p1.y)
          targetCtx.stroke()
        }

        // Precision dashed telemetry ticks outside Ring 1 & Ring 2
        if (ring.hasTicks) {
          const tickCount = 36
          for (let tIdx = 0; tIdx < tickCount; tIdx++) {
            const theta = (tIdx / tickCount) * Math.PI * 2 + ring.spin * 0.85
            const isMajor = tIdx % 3 === 0
            const rInner = R * (ring.style === 'primary' ? 1.04 : 0.91)
            const rOuter = R * (ring.style === 'primary' ? (isMajor ? 1.125 : 1.08) : (isMajor ? 0.84 : 0.875))
            const v0 = rotate3D(Math.cos(theta) * rInner, 0, Math.sin(theta) * rInner, rx, ry, rz)
            const v1 = rotate3D(Math.cos(theta) * rOuter, 0, Math.sin(theta) * rOuter, rx, ry, rz)
            const p0 = project3D(v0, cx, cy, fov)
            const p1 = project3D(v1, cx, cy, fov)
            const zMid = (p0.z + p1.z) * 0.5
            const depth = clamp((zMid / Math.max(1, R) + 1) * 0.5, 0, 1)
            const targetCtx = zMid < 0 ? bCtx : fCtx
            const tickAlpha = (isMajor ? 0.52 : 0.26) * (0.35 + depth * 0.65) + surgeFlash * 0.18
            targetCtx.strokeStyle = rgba(isMajor ? ringColor : palette.core, Math.min(0.95, tickAlpha))
            targetCtx.lineWidth = (isMajor ? 1.55 : 0.9) * ((p0.scale + p1.scale) * 0.5)
            targetCtx.beginPath()
            targetCtx.moveTo(p0.x, p0.y)
            targetCtx.lineTo(p1.x, p1.y)
            targetCtx.stroke()
          }
        }

        // Specular glowing plasma comet arcs racing along the ring track
        for (let aIdx = 0; aIdx < ring.arcCount; aIdx++) {
          const headAngle =
            ring.spin * (1.85 + rIndex * 0.25) + (aIdx / ring.arcCount) * Math.PI * 2
          const arcSteps = 14
          const arcSpan = 0.62
          for (let s = 0; s < arcSteps; s++) {
            const frac0 = s / arcSteps
            const frac1 = (s + 1) / arcSteps
            const th0 = headAngle - (1 - frac0) * arcSpan * Math.sign(ring.baseSpeed)
            const th1 = headAngle - (1 - frac1) * arcSpan * Math.sign(ring.baseSpeed)
            const v0 = rotate3D(Math.cos(th0) * R, 0, Math.sin(th0) * R, rx, ry, rz)
            const v1 = rotate3D(Math.cos(th1) * R, 0, Math.sin(th1) * R, rx, ry, rz)
            const p0 = project3D(v0, cx, cy, fov)
            const p1 = project3D(v1, cx, cy, fov)
            const zMid = (p0.z + p1.z) * 0.5
            const targetCtx = zMid < 0 ? bCtx : fCtx
            const depth = clamp((zMid / Math.max(1, R) + 1) * 0.5, 0, 1)
            const tailFade = Math.pow(frac1, 1.7)
            const glowAlpha = tailFade * (0.45 + depth * 0.55)
            const widthScale = (p0.scale + p1.scale) * 0.5

            targetCtx.strokeStyle = rgba(ringColor, glowAlpha * (palette.isLight ? 0.55 : 0.72))
            targetCtx.lineWidth = (2.2 + tailFade * 4.2) * widthScale
            targetCtx.beginPath()
            targetCtx.moveTo(p0.x, p0.y)
            targetCtx.lineTo(p1.x, p1.y)
            targetCtx.stroke()

            targetCtx.strokeStyle = rgba(palette.core, glowAlpha * (palette.isLight ? 0.75 : 0.95))
            targetCtx.lineWidth = (0.9 + tailFade * 1.65) * widthScale
            targetCtx.beginPath()
            targetCtx.moveTo(p0.x, p0.y)
            targetCtx.lineTo(p1.x, p1.y)
            targetCtx.stroke()
          }
        }

        // Orbital glowing plasma nodes riding along the ring
        for (let nIdx = 0; nIdx < ring.nodeCount; nIdx++) {
          const theta = ring.spin * 1.45 + (nIdx / ring.nodeCount) * Math.PI * 2 + rIndex * 0.9
          const v = rotate3D(Math.cos(theta) * R, 0, Math.sin(theta) * R, rx, ry, rz)
          const p = project3D(v, cx, cy, fov)
          const targetCtx = p.z < 0 ? bCtx : fCtx
          const depth = clamp((p.z / Math.max(1, R) + 1) * 0.5, 0, 1)
          const nodeR = (2.6 + depth * 2.4 + surgeFlash * 1.6) * p.scale

          const haloGrad = targetCtx.createRadialGradient(p.x, p.y, 0, p.x, p.y, nodeR * 3.4)
          haloGrad.addColorStop(0, rgba(palette.core, palette.isLight ? 0.9 : 0.95))
          haloGrad.addColorStop(0.32, rgba(ringColor, palette.isLight ? 0.65 : 0.85))
          haloGrad.addColorStop(1, rgba(ringColor, 0))
          targetCtx.fillStyle = haloGrad
          targetCtx.beginPath()
          targetCtx.arc(p.x, p.y, nodeR * 3.4, 0, Math.PI * 2)
          targetCtx.fill()

          targetCtx.fillStyle = rgba(palette.core, 0.96)
          targetCtx.beginPath()
          targetCtx.arc(p.x, p.y, nodeR * 0.52, 0, Math.PI * 2)
          targetCtx.fill()
        }
      }

      bCtx.restore()
      fCtx.restore()
    }

    const drawShockwaves = (cx: number, cy: number, fov: number) => {
      if (!shockwaves.length) return
      const compOp = palette.isLight ? 'source-over' : 'lighter'
      bCtx.save()
      fCtx.save()
      bCtx.globalCompositeOperation = compOp
      fCtx.globalCompositeOperation = compOp

      const segs = 64
      for (let s = 0; s < shockwaves.length; s++) {
        const sw = shockwaves[s]
        if (sw.age < sw.delay) continue
        const tNorm = clamp((sw.age - sw.delay) / sw.duration, 0, 1)
        const eased = 1 - Math.pow(1 - tNorm, 3)
        const fade = Math.pow(1 - tNorm, 1.45)
        const R = sw.startR + (sw.maxR - sw.startR) * eased
        const color = getRoleColor(palette, sw.colorRole)
        const rx = sw.tiltX + curParallaxY * 0.34
        const ry = curParallaxX * 0.36
        const rz = sw.tiltZ

        let prev: ProjPoint | null = null
        for (let i = 0; i <= segs; i++) {
          const a = (i / segs) * Math.PI * 2
          const v = rotate3D(Math.cos(a) * R, sw.yOffset, Math.sin(a) * R, rx, ry, rz)
          const p = project3D(v, cx, cy, fov)
          if (prev) {
            const zMid = (prev.z + p.z) * 0.5
            const targetCtx = zMid < 0 ? bCtx : fCtx
            const sc = (prev.scale + p.scale) * 0.5

            targetCtx.strokeStyle = rgba(color, fade * (palette.isLight ? 0.36 : 0.48))
            targetCtx.lineWidth = (4.5 + (1 - tNorm) * 6.5) * sc
            targetCtx.beginPath()
            targetCtx.moveTo(prev.x, prev.y)
            targetCtx.lineTo(p.x, p.y)
            targetCtx.stroke()

            targetCtx.strokeStyle = rgba(palette.core, fade * (palette.isLight ? 0.72 : 0.92))
            targetCtx.lineWidth = (1.3 + (1 - tNorm) * 1.8) * sc
            targetCtx.beginPath()
            targetCtx.moveTo(prev.x, prev.y)
            targetCtx.lineTo(p.x, p.y)
            targetCtx.stroke()
          }
          prev = p
        }
      }

      bCtx.restore()
      fCtx.restore()
    }

    const drawLightning = (cx: number, cy: number, fov: number) => {
      if (!bolts.length) return
      const compOp = palette.isLight ? 'source-over' : 'lighter'
      bCtx.save()
      fCtx.save()
      bCtx.globalCompositeOperation = compOp
      fCtx.globalCompositeOperation = compOp
      bCtx.lineCap = 'round'
      bCtx.lineJoin = 'round'
      fCtx.lineCap = 'round'
      fCtx.lineJoin = 'round'

      const drawPolyline = (
        pts3D: Vec3[],
        color: RGB,
        alpha: number,
        widthFactor: number,
      ) => {
        if (pts3D.length < 2) return
        const projected = pts3D.map(v => {
          const rot = rotate3D(v.x, v.y, v.z, curParallaxY * 0.38, curParallaxX * 0.42, 0)
          return project3D(rot, cx, cy, fov)
        })
        for (let i = 0; i < projected.length - 1; i++) {
          const p0 = projected[i]
          const p1 = projected[i + 1]
          const zMid = (p0.z + p1.z) * 0.5
          const targetCtx = zMid < 0 ? bCtx : fCtx
          const sc = (p0.scale + p1.scale) * 0.5

          // Outer plasma halo
          targetCtx.strokeStyle = rgba(color, alpha * (palette.isLight ? 0.34 : 0.44))
          targetCtx.lineWidth = 8.4 * widthFactor * sc
          targetCtx.beginPath()
          targetCtx.moveTo(p0.x, p0.y)
          targetCtx.lineTo(p1.x, p1.y)
          targetCtx.stroke()

          // Mid vibrant arc
          targetCtx.strokeStyle = rgba(color, alpha * 0.92)
          targetCtx.lineWidth = 3.0 * widthFactor * sc
          targetCtx.beginPath()
          targetCtx.moveTo(p0.x, p0.y)
          targetCtx.lineTo(p1.x, p1.y)
          targetCtx.stroke()

          // White-hot razor center
          targetCtx.strokeStyle = rgba(palette.core, alpha)
          targetCtx.lineWidth = 1.25 * widthFactor * sc
          targetCtx.beginPath()
          targetCtx.moveTo(p0.x, p0.y)
          targetCtx.lineTo(p1.x, p1.y)
          targetCtx.stroke()
        }
      }

      for (let i = 0; i < bolts.length; i++) {
        const bolt = bolts[i]
        const tNorm = clamp(bolt.age / bolt.duration, 0, 1)
        const flicker = 0.72 + 0.28 * Math.sin(simTime * 68 + bolt.flickerPhase)
        const alpha = Math.pow(1 - tNorm, 0.9) * flicker
        const color = getRoleColor(palette, bolt.colorRole)
        drawPolyline(bolt.points, color, alpha, 1.0)
        for (let b = 0; b < bolt.branches.length; b++) {
          drawPolyline(bolt.branches[b], color, alpha * 0.76, 0.62)
        }
      }

      bCtx.restore()
      fCtx.restore()
    }

    const drawParticles = (cx: number, cy: number, fov: number) => {
      const compOp = palette.isLight ? 'source-over' : 'lighter'
      bCtx.save()
      fCtx.save()
      bCtx.globalCompositeOperation = compOp
      fCtx.globalCompositeOperation = compOp
      bCtx.lineCap = 'round'
      fCtx.lineCap = 'round'

      const renderList = (list: Particle3D[]) => {
        for (let i = 0; i < list.length; i++) {
          const p = list[i]
          const rotNow = rotate3D(p.x, p.y, p.z, curParallaxY * 0.38, curParallaxX * 0.42, 0)
          const rotPrev = rotate3D(p.px, p.py, p.pz, curParallaxY * 0.38, curParallaxX * 0.42, 0)
          const proj = project3D(rotNow, cx, cy, fov)
          const prevProj = project3D(rotPrev, cx, cy, fov)
          const targetCtx = proj.z < 0 ? bCtx : fCtx
          const depth = clamp((proj.z / Math.max(1, baseR * 1.4) + 1) * 0.5, 0, 1)
          const color = getRoleColor(palette, p.colorRole)

          const lifeAlpha = p.kind === 'ambient'
            ? (palette.isLight ? 0.48 : 0.52) + depth * 0.42 + p.magnetBoost * 0.3
            : clamp(p.life * 1.25, 0, 1)
          const alpha = clamp(lifeAlpha * (0.45 + depth * 0.55), 0, 1)
          const r = p.size * proj.scale * (0.65 + depth * 0.55)

          // Velocity comet tail
          const dx = proj.x - prevProj.x
          const dy = proj.y - prevProj.y
          const trailLenSq = dx * dx + dy * dy
          if (trailLenSq > 1.2) {
            const tailX = proj.x - dx * (p.kind === 'ambient' ? 3.2 : 2.35)
            const tailY = proj.y - dy * (p.kind === 'ambient' ? 3.2 : 2.35)
            const grad = targetCtx.createLinearGradient(proj.x, proj.y, tailX, tailY)
            grad.addColorStop(0, rgba(color, alpha * 0.88))
            grad.addColorStop(1, rgba(color, 0))
            targetCtx.strokeStyle = grad
            targetCtx.lineWidth = Math.max(1, r * (p.kind === 'shard' ? 0.95 : 1.1))
            targetCtx.beginPath()
            targetCtx.moveTo(proj.x, proj.y)
            targetCtx.lineTo(tailX, tailY)
            targetCtx.stroke()
          }

          if (p.kind === 'shard') {
            // Sharp velocity-aligned plasma shard diamond
            const angle = trailLenSq > 0.2 ? Math.atan2(dy, dx) : i * 0.7
            targetCtx.save()
            targetCtx.translate(proj.x, proj.y)
            targetCtx.rotate(angle)
            targetCtx.fillStyle = rgba(color, alpha)
            targetCtx.beginPath()
            targetCtx.moveTo(r * 2.1, 0)
            targetCtx.lineTo(0, r * 0.78)
            targetCtx.lineTo(-r * 1.6, 0)
            targetCtx.lineTo(0, -r * 0.78)
            targetCtx.closePath()
            targetCtx.fill()

            targetCtx.fillStyle = rgba(palette.core, alpha * 0.9)
            targetCtx.beginPath()
            targetCtx.moveTo(r * 1.1, 0)
            targetCtx.lineTo(0, r * 0.34)
            targetCtx.lineTo(-r * 0.75, 0)
            targetCtx.lineTo(0, -r * 0.34)
            targetCtx.closePath()
            targetCtx.fill()
            targetCtx.restore()
          } else {
            // Glowing plasma ember with soft aura + hot core
            const halo = targetCtx.createRadialGradient(proj.x, proj.y, 0, proj.x, proj.y, r * 2.6)
            halo.addColorStop(0, rgba(palette.core, alpha * (palette.isLight ? 0.8 : 0.92)))
            halo.addColorStop(0.36, rgba(color, alpha * 0.78))
            halo.addColorStop(1, rgba(color, 0))
            targetCtx.fillStyle = halo
            targetCtx.beginPath()
            targetCtx.arc(proj.x, proj.y, r * 2.6, 0, Math.PI * 2)
            targetCtx.fill()
          }
        }
      }

      renderList(ambientEmbers)
      renderList(burstParticles)

      bCtx.restore()
      fCtx.restore()
    }

    const drawMagneticFieldHalo = (ctx: CanvasRenderingContext2D) => {
      const ptr = pointerStateRef.current
      if (!ptr.active || isReducedMotionActive()) return
      ctx.save()
      ctx.globalCompositeOperation = palette.isLight ? 'source-over' : 'lighter'
      const haloR = baseR * 0.36
      const grad = ctx.createRadialGradient(ptr.x, ptr.y, 0, ptr.x, ptr.y, haloR)
      grad.addColorStop(0, rgba(palette.primary, palette.isLight ? 0.16 : 0.22))
      grad.addColorStop(0.55, rgba(palette.secondary, palette.isLight ? 0.08 : 0.1))
      grad.addColorStop(1, rgba(palette.primary, 0))
      ctx.fillStyle = grad
      ctx.beginPath()
      ctx.arc(ptr.x, ptr.y, haloR, 0, Math.PI * 2)
      ctx.fill()

      ctx.strokeStyle = rgba(palette.primary, palette.isLight ? 0.34 : 0.42)
      ctx.lineWidth = 1
      ctx.setLineDash([4, 5])
      ctx.beginPath()
      ctx.arc(ptr.x, ptr.y, 13 + Math.sin(simTime * 5) * 1.8, simTime * 1.4, simTime * 1.4 + Math.PI * 2)
      ctx.stroke()
      ctx.restore()
    }

    const renderFrame = () => {
      bCtx.clearRect(0, 0, width, height)
      fCtx.clearRect(0, 0, width, height)

      const cx = width * 0.5
      const cy = height * 0.5
      const fov = baseR * 3.85
      const overdrive = activeRef.current

      drawVolumetricCore(bCtx, cx, cy, overdrive)
      drawFloorGrid(bCtx, cx, cy, fov, overdrive)
      drawGyroRings(cx, cy, fov, overdrive)
      drawShockwaves(cx, cy, fov)
      drawLightning(cx, cy, fov)
      drawParticles(cx, cy, fov)
      drawMagneticFieldHalo(fCtx)
    }

    const stepPhysics = (dt: number) => {
      const overdrive = activeRef.current
      simTime += dt

      // Spring-interpolate pointer parallax
      const ptr = pointerStateRef.current
      const targetPX = ptr.active ? ptr.nx : 0
      const targetPY = ptr.active ? ptr.ny : 0
      const spring = 1 - Math.exp(-dt * 8.5)
      curParallaxX += (targetPX - curParallaxX) * spring
      curParallaxY += (targetPY - curParallaxY) * spring

      // Decay surge glow & spin burst
      surgeFlash = Math.max(0, surgeFlash - dt * 1.85)
      ringBoost = Math.max(0, ringBoost * Math.exp(-dt * 2.6))

      // Advance gyroscopic rings (1.6x faster in Active Workout OVERDRIVE mode)
      const speedMul = (overdrive ? 1.6 : 1.0) + ringBoost
      for (let i = 0; i < rings.length; i++) {
        rings[i].spin += rings[i].baseSpeed * speedMul * dt
      }

      // Periodic lightning crackle (frequent in OVERDRIVE, subtle occasional forge arc in idle)
      if (simTime >= nextAmbientArcAt) {
        const boltRole = Math.random() > 0.42 ? 'primary' : 'secondary'
        bolts.push(createLightningBolt(baseR, boltRole, overdrive ? 0.24 : 0.19))
        if (overdrive && Math.random() > 0.52) {
          bolts.push(createLightningBolt(baseR, 'secondary', 0.22))
        }
        nextAmbientArcAt = simTime + (overdrive ? 0.95 + Math.random() * 1.15 : 3.2 + Math.random() * 2.4)
      }

      // Update shockwaves
      for (let i = shockwaves.length - 1; i >= 0; i--) {
        const sw = shockwaves[i]
        sw.age += dt
        if (sw.age >= sw.delay + sw.duration) {
          shockwaves.splice(i, 1)
        }
      }

      // Update lightning bolts
      for (let i = bolts.length - 1; i >= 0; i--) {
        bolts[i].age += dt
        if (bolts[i].age >= bolts[i].duration) {
          bolts.splice(i, 1)
        }
      }

      // Update ambient orbital embers + magnetic pointer field attraction
      const cx = width * 0.5
      const cy = height * 0.5
      const fov = baseR * 3.85
      const ptrRelX = ptr.x - cx
      const ptrRelY = ptr.y - cy
      const magnetRange = baseR * 1.25

      for (let i = 0; i < ambientEmbers.length; i++) {
        const e = ambientEmbers[i]
        e.px = e.x
        e.py = e.y
        e.pz = e.z

        e.orbitAngle += e.orbitSpeed * (overdrive ? 1.45 : 1.0) * dt
        const targetX = Math.cos(e.orbitAngle) * e.orbitRadius
        const targetZ = Math.sin(e.orbitAngle) * e.orbitRadius
        const targetY = e.orbitY + Math.sin(simTime * 1.5 + i) * baseR * 0.12 + Math.sin(e.orbitAngle) * e.orbitRadius * e.orbitTilt * 0.35

        // Elastic return toward orbit
        e.vx += (targetX - e.x) * 4.8 * dt
        e.vy += (targetY - e.y) * 4.8 * dt
        e.vz += (targetZ - e.z) * 4.8 * dt

        // Magnetic pointer pull + tangential swirl
        e.magnetBoost = Math.max(0, e.magnetBoost - dt * 3.5)
        if (ptr.active) {
          const rot = rotate3D(e.x, e.y, e.z, curParallaxY * 0.38, curParallaxX * 0.42, 0)
          const proj = project3D(rot, cx, cy, fov)
          const dx = ptr.x - proj.x
          const dy = ptr.y - proj.y
          const dist = Math.hypot(dx, dy)
          if (dist < magnetRange && dist > 3) {
            const pull = (1 - dist / magnetRange)
            const pullForce = pull * baseR * 14.5 * dt
            const dirX = dx / dist
            const dirY = dy / dist
            // Pull magnetically toward pointer + add crisp tangential swirl
            e.vx += (dirX * 0.82 - dirY * 0.48) * pullForce + (ptrRelX - e.x) * pull * 1.6 * dt
            e.vy += (dirY * 0.82 + dirX * 0.48) * pullForce + (ptrRelY - e.y) * pull * 1.6 * dt
            e.vz += (baseR * 0.48 - e.z) * pull * 2.4 * dt
            e.magnetBoost = Math.min(1, e.magnetBoost + pull * 1.2)
          }
        }

        const damp = Math.exp(-dt * 4.2)
        e.vx *= damp
        e.vy *= damp
        e.vz *= damp
        e.x += e.vx * dt
        e.y += e.vy * dt
        e.z += e.vz * dt
      }

      // Update explosive surge embers & shards (gravity, drag, 3D floor bounce!)
      const floorY = baseR * 0.77
      const gravity = baseR * 3.1
      const drag = Math.pow(0.965, dt * 60)

      for (let i = burstParticles.length - 1; i >= 0; i--) {
        const p = burstParticles[i]
        p.px = p.x
        p.py = p.y
        p.pz = p.z

        p.vy += gravity * dt
        p.vx *= drag
        p.vy *= drag
        p.vz *= drag

        p.x += p.vx * dt
        p.y += p.vy * dt
        p.z += p.vz * dt

        // Bounce off the 3D perspective floor deck
        if (p.y > floorY && p.vy > 0) {
          p.y = floorY
          p.vy = -p.vy * 0.46
          p.vx *= 0.82
          p.vz *= 0.82
        }

        p.life -= dt / p.maxLife
        if (p.life <= 0) {
          burstParticles.splice(i, 1)
        }
      }
    }

    const stopLoop = () => {
      if (rafId !== undefined) {
        window.cancelAnimationFrame(rafId)
        rafId = undefined
      }
    }

    const tick = (ts: number) => {
      rafId = undefined
      if (destroyed || document.visibilityState === 'hidden' || isReducedMotionActive()) return
      if (lastTimestamp === 0) lastTimestamp = ts
      const dt = Math.min(0.05, Math.max(0.001, (ts - lastTimestamp) / 1000))
      lastTimestamp = ts
      stepPhysics(dt)
      renderFrame()
      rafId = window.requestAnimationFrame(tick)
    }

    const scheduleEntranceSurge = () => {
      if (entranceTriggered || isReducedMotionActive() || document.visibilityState === 'hidden') return
      entranceTriggered = true
      entranceRaf1 = window.requestAnimationFrame(() => {
        entranceRaf2 = window.requestAnimationFrame(() => {
          entranceTimer = window.setTimeout(() => {
            if (!destroyed && !isReducedMotionActive() && document.visibilityState !== 'hidden') {
              spawnSurge()
              fireRecoilUi(false)
            }
          }, 120)
        })
      })
    }

    const syncLoopState = () => {
      if (destroyed) return
      palette = readStagePalette(gatorRef.current)
      if (isReducedMotionActive()) {
        stopLoop()
        // Render one crisp static 3D hero frame immediately
        renderFrame()
        return
      }
      if (document.visibilityState === 'hidden') {
        stopLoop()
        return
      }
      if (rafId === undefined) {
        lastTimestamp = 0
        rafId = window.requestAnimationFrame(tick)
      }
      scheduleEntranceSurge()
    }

    refreshEngineRef.current = syncLoopState

    resize()
    renderFrame()
    syncLoopState()

    // Resize observer with safe fallback
    let ro: ResizeObserver | undefined
    const onWindowResize = () => {
      resize()
      renderFrame()
    }
    if (typeof ResizeObserver !== 'undefined') {
      ro = new ResizeObserver(() => {
        resize()
        if (isReducedMotionActive() || document.visibilityState === 'hidden') {
          renderFrame()
        }
      })
      ro.observe(stageEl)
    } else {
      window.addEventListener('resize', onWindowResize)
    }

    // Observe live theme, accent, brand, and motion attribute changes
    const mo = new MutationObserver(() => {
      syncLoopState()
    })
    mo.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ['style', 'data-theme', 'data-brand', 'data-motion'],
    })

    const motionQuery = window.matchMedia?.('(prefers-reduced-motion: reduce)')
    const onMotionChange = () => syncLoopState()
    motionQuery?.addEventListener?.('change', onMotionChange)

    const onVisibilityChange = () => {
      if (document.visibilityState === 'hidden') {
        stopLoop()
      } else {
        lastTimestamp = 0
        syncLoopState()
      }
    }
    document.addEventListener('visibilitychange', onVisibilityChange)

    return () => {
      destroyed = true
      engineTriggerRef.current = null
      refreshEngineRef.current = null
      stopLoop()
      if (entranceRaf1 !== undefined) window.cancelAnimationFrame(entranceRaf1)
      if (entranceRaf2 !== undefined) window.cancelAnimationFrame(entranceRaf2)
      if (entranceTimer !== undefined) window.clearTimeout(entranceTimer)
      ro?.disconnect()
      window.removeEventListener('resize', onWindowResize)
      mo.disconnect()
      motionQuery?.removeEventListener?.('change', onMotionChange)
      document.removeEventListener('visibilitychange', onVisibilityChange)
      rings = []
      ambientEmbers = []
    }
  }, [fireRecoilUi])

  // Keep canvas palette & overdrive visuals immediately synced if props toggle
  useEffect(() => {
    gatorRef.current = gatorMode
    activeRef.current = active
    refreshEngineRef.current?.()
  }, [gatorMode, active])

  const mascotBrand = gatorMode ? 'gator' : 'chimp'

  return (
    <div
      ref={stageRef}
      className={`apex-stage ${active ? 'is-overdrive' : ''} ${gatorMode ? 'is-gator' : 'is-chimp'}`}
      data-brand-stage={mascotBrand}
      onPointerMove={handlePointerMove}
      onPointerLeave={handlePointerLeave}
      onClick={handleStageClick}
    >
      <canvas
        ref={backCanvasRef}
        className="apex-canvas-back"
        aria-hidden="true"
      />
      <div
        ref={mascotLayerRef}
        className={`apex-mascot-layer home-hero-mascot-wrap ${surging ? 'is-surging' : ''}`}
      >
        <div className="apex-mascot-recoil">
          <IronMascot
            pose="hero"
            brand={mascotBrand}
            interactive
            size="100%"
            onActivate={() => triggerSurge(true)}
          />
        </div>
      </div>
      <canvas
        ref={frontCanvasRef}
        className="apex-canvas-front"
        aria-hidden="true"
      />
    </div>
  )
}
