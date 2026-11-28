import { useEffect, useMemo, useState } from 'react'
import { Copy, ImageDown } from 'lucide-react'
import { formatWorkoutShare, type ShareableWorkout } from '../domain/workoutShare'
import { formatWeight, weightLabel } from '../domain/units'
import type { Preferences } from '../db/types'
import { renderGatorBadgeToCanvas } from '../components/mascot'

function hexWithAlpha(hex: string, alpha: number): string {
  const r = parseInt(hex.slice(1, 3), 16)
  const g = parseInt(hex.slice(3, 5), 16)
  const b = parseInt(hex.slice(5, 7), 16)
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}

async function renderShareCardPng(
  share: ShareableWorkout,
  prefs: Preferences,
  accent: string,
): Promise<Blob> {
  const canvas = document.createElement('canvas')
  const width = 1080
  const height = 1280
  canvas.width = width
  canvas.height = height
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('Image export is unavailable in this browser.')

  const isGator = prefs.appIcon === 'swole_alligator'
  const unit = prefs.preferredWeightUnit
  const resolvedAccent = /^#[0-9a-f]{6}$/i.test(accent) ? accent : (isGator ? '#FA4616' : '#A3E635')

  if (isGator) {
    // UF Swamp Navy backdrop with subtle radial highlight
    const bgGrad = ctx.createLinearGradient(0, 0, width, height)
    bgGrad.addColorStop(0, '#040A1C')
    bgGrad.addColorStop(0.55, '#071230')
    bgGrad.addColorStop(1, '#040A1C')
    ctx.fillStyle = bgGrad
    ctx.fillRect(0, 0, width, height)

    // Subtle gator-scale dot grid
    ctx.fillStyle = 'rgba(147, 197, 253, 0.06)'
    for (let x = 28; x < width; x += 28) {
      for (let y = 28; y < height; y += 28) {
        ctx.fillRect(x, y, 2, 2)
      }
    }

    // Dual UF varsity top & bottom stripes (Orange + Blue)
    ctx.fillStyle = '#FA4616'
    ctx.fillRect(0, 0, width * 0.5, 14)
    ctx.fillStyle = '#0021A5'
    ctx.fillRect(width * 0.5, 0, width * 0.5, 14)

    ctx.fillStyle = '#0021A5'
    ctx.fillRect(0, height - 14, width * 0.5, 14)
    ctx.fillStyle = '#FA4616'
    ctx.fillRect(width * 0.5, height - 14, width * 0.5, 14)
  } else {
    // Machined obsidian backdrop with subtle radial energy glow
    const bgGrad = ctx.createLinearGradient(0, 0, width, height)
    bgGrad.addColorStop(0, '#090A0D')
    bgGrad.addColorStop(0.55, '#11141C')
    bgGrad.addColorStop(1, '#090A0D')
    ctx.fillStyle = bgGrad
    ctx.fillRect(0, 0, width, height)

    const obsidianGlow = ctx.createRadialGradient(width * 0.84, 128, 16, width * 0.84, 128, 620)
    obsidianGlow.addColorStop(0, hexWithAlpha(resolvedAccent, 0.16))
    obsidianGlow.addColorStop(0.45, 'rgba(30, 35, 48, 0.28)')
    obsidianGlow.addColorStop(1, 'rgba(9, 10, 13, 0)')
    ctx.fillStyle = obsidianGlow
    ctx.fillRect(0, 0, width, height)

    // Subtle machined plate dot grid
    ctx.fillStyle = 'rgba(244, 246, 250, 0.035)'
    for (let x = 28; x < width; x += 28) {
      for (let y = 28; y < height; y += 28) {
        ctx.fillRect(x, y, 2, 2)
      }
    }

    // Top & bottom accent energy bars
    ctx.fillStyle = resolvedAccent
    ctx.fillRect(0, 0, width, 14)
    ctx.fillStyle = hexWithAlpha(resolvedAccent, 0.48)
    ctx.fillRect(0, height - 10, width, 10)
  }

  function label(text: string, x: number, y: number, px: number, color = '#F4F6FA', weight = 600, maxWidth = 950) {
    ctx!.fillStyle = color
    ctx!.font = `${weight} ${px}px system-ui, sans-serif`
    ctx!.fillText(text, x, y, maxWidth)
  }

  if (isGator) {
    label('UF · IRONLOG', 64, 96, 26, '#FA4616', 800)
    label('SWOLE GATOR', 64, 152, 54, '#FFFFFF', 900)
    const img = new Image()
    img.src = '/icons/swole-alligator-192.png'
    try {
      await img.decode()
      ctx.drawImage(img, 896, 48, 120, 120)
    } catch {
      renderGatorBadgeToCanvas(ctx, 896, 48, 120)
    }
  } else {
    label('IRONLOG · SWOLE CHIMP', 64, 96, 26, resolvedAccent, 800)
    label('IRONLOG', 64, 152, 54, '#FFFFFF', 900)
    const img = new Image()
    img.src = '/icons/classic-192.png'
    try {
      await img.decode()
      ctx.drawImage(img, 896, 48, 120, 120)
    } catch {
      /* wordmark fallback */
    }
  }

  const mutedColor = isGator ? '#9BB0E0' : '#A3A9B6'
  const dividerColor = isGator ? 'rgba(147, 197, 253, 0.25)' : '#3B3E47'
  const highlightColor = isGator ? '#FA4616' : resolvedAccent

  label(
    new Date(share.date).toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric', year: 'numeric' }),
    64,
    235,
    30,
    mutedColor,
    500,
  )
  label(share.title, 64, 340, 82, '#F4F7FF', 800)

  // KPI surface card
  ctx.fillStyle = isGator ? '#0B1533' : '#12141A'
  ctx.fillRect(64, 405, 952, 295)
  ctx.strokeStyle = isGator ? '#0021A5' : hexWithAlpha(resolvedAccent, 0.35)
  ctx.lineWidth = 2
  ctx.strokeRect(64, 405, 952, 295)

  label('TOTAL VOLUME', 100, 468, 24, mutedColor, 700)
  label(formatWeight(share.totalVolume, unit) + ' ' + weightLabel(unit), 100, 582, 102, highlightColor, 800, 880)
  label(share.totalWorkingSets + ' working sets', 100, 660, 38, '#F4F7FF', 600)
  label(share.durationMinutes === null ? 'In progress' : share.durationMinutes + ' min', 640, 660, 38, '#F4F7FF', 600)

  ctx.strokeStyle = dividerColor
  ctx.beginPath()
  ctx.moveTo(64, 750)
  ctx.lineTo(1016, 750)
  ctx.stroke()

  const shown = share.exercises.slice(0, 5)
  for (const [i, exercise] of shown.entries()) {
    const y = 820 + i * 74
    const workingSets = exercise.sets.filter(s => !s.isWarmup)
    const hasPr = exercise.sets.some(s => s.isPersonalRecord)
    label(exercise.name, 64, y, 36, '#F4F6FA', 600, 660)
    if (hasPr) label('PR', 750, y, 26, highlightColor, 800)
    label(workingSets.length + (workingSets.length === 1 ? ' set' : ' sets'), 820, y, 30, mutedColor, 600)
  }
  if (share.exercises.length > shown.length) {
    label('+' + (share.exercises.length - shown.length) + ' more exercises', 64, 1190, 28, mutedColor, 500)
  }

  return new Promise((resolve, reject) =>
    canvas.toBlob(blob => (blob ? resolve(blob) : reject(new Error('Image export failed.'))), 'image/png'),
  )
}

export function ShareActions({ share, prefs }: { share: ShareableWorkout; prefs: Preferences }) {
  const [file, setFile] = useState<File | null>(null)
  const [status, setStatus] = useState('')
  const text = useMemo(() => formatWorkoutShare(share), [share])
  useEffect(() => {
    let cancelled = false
    setFile(null)
    const accent = getComputedStyle(document.documentElement).getPropertyValue('--accent').trim()
    renderShareCardPng(share, prefs, accent).then(blob => {
      if (!cancelled) setFile(new File([blob], 'ironlog-workout-' + share.date + '.png', { type:'image/png' }))
    }).catch(() => { if (!cancelled) setStatus('Couldn’t create a workout image.') })
    return () => { cancelled = true }
  }, [share, prefs.preferredWeightUnit, prefs.appIcon, prefs.themeColor, prefs.customThemeColor])

  async function sharePng() {
    if (!file) return
    if (navigator.canShare?.({files:[file]})) {
      try {
        await navigator.share({files:[file], title:share.title})
      } catch (err) {
        if (err instanceof DOMException && err.name === 'AbortError') return
        setStatus('Sharing wasn’t available. The image was downloaded.')
        download()
      }
    } else {
      download()
      setStatus('Workout image downloaded.')
    }
    function download() {
      const link = document.createElement('a')
      const url = URL.createObjectURL(file!)
      link.href = url
      link.download = file!.name
      document.body.append(link)
      link.click()
      link.remove()
      window.setTimeout(() => URL.revokeObjectURL(url), 60_000)
    }
  }

  return <>
    <div className="his-share">
      <button className="btn" type="button" disabled={!file} onClick={() => void sharePng()}><ImageDown size={18}/>Share PNG</button>
      <button className="btn" type="button" onClick={async () => {
        try { await navigator.clipboard.writeText(text); setStatus('Workout text copied.') }
        catch { setStatus('Select and copy the workout text below.') }
      }}><Copy size={18}/>Copy text</button>
    </div>
    {status && <p role="status" className="his-message">{status}</p>}
    {status.startsWith('Select and copy') && <textarea aria-label="Workout share text" className="his-input" rows={5} readOnly value={text} onFocus={e => e.target.select()}/>}
  </>
}
