export { SwoleGator, useIsGatorMode, type GatorPose, type SwoleGatorProps } from './SwoleGator'

/**
 * Lightweight synchronous Canvas 2D helper to draw the UF Swole Gator badge
 * emblem onto exported share card PNGs without async SVG image loading.
 */
export function renderGatorBadgeToCanvas(
  ctx: CanvasRenderingContext2D,
  x: number,
  y: number,
  size: number,
): void {
  const s = size / 128
  ctx.save()
  ctx.translate(x, y)
  ctx.scale(s, s)

  // Outer UF Swamp Navy crest & varsity rings
  ctx.beginPath()
  ctx.arc(64, 64, 61, 0, Math.PI * 2)
  ctx.fillStyle = '#040A1C'
  ctx.fill()

  ctx.beginPath()
  ctx.arc(64, 64, 57, 0, Math.PI * 2)
  ctx.strokeStyle = '#0021A5'
  ctx.lineWidth = 5
  ctx.stroke()

  ctx.beginPath()
  ctx.arc(64, 64, 53.5, 0, Math.PI * 2)
  ctx.strokeStyle = '#FA4616'
  ctx.lineWidth = 2.5
  ctx.stroke()

  // Traps & UF Blue collar
  ctx.beginPath()
  ctx.moveTo(22, 108)
  ctx.bezierCurveTo(24, 88, 38, 78, 64, 78)
  ctx.bezierCurveTo(90, 78, 104, 88, 106, 108)
  ctx.closePath()
  ctx.fillStyle = '#0021A5'
  ctx.fill()

  // UF Orange collar stripe
  ctx.beginPath()
  ctx.moveTo(36, 86)
  ctx.quadraticCurveTo(64, 98, 92, 86)
  ctx.lineTo(88, 94)
  ctx.quadraticCurveTo(64, 106, 40, 94)
  ctx.closePath()
  ctx.fillStyle = '#FA4616'
  ctx.fill()

  // Dorsal spikes
  ctx.beginPath()
  ctx.moveTo(32, 45)
  ctx.lineTo(24, 35)
  ctx.lineTo(38, 38)
  ctx.lineTo(37, 25)
  ctx.lineTo(48, 33)
  ctx.lineTo(55, 19)
  ctx.lineTo(64, 30)
  ctx.lineTo(74, 21)
  ctx.lineTo(78, 34)
  ctx.closePath()
  ctx.fillStyle = '#105A2B'
  ctx.fill()

  // Gator head & snout
  ctx.beginPath()
  ctx.moveTo(28, 74)
  ctx.bezierCurveTo(27, 54, 40, 42, 58, 42)
  ctx.bezierCurveTo(72, 42, 82, 47, 88, 53)
  ctx.lineTo(107, 56)
  ctx.bezierCurveTo(113, 57, 115, 64, 109, 68)
  ctx.lineTo(84, 72)
  ctx.lineTo(68, 74)
  ctx.lineTo(99, 75)
  ctx.bezierCurveTo(103, 77, 101, 85, 93, 88)
  ctx.bezierCurveTo(78, 93, 52, 94, 34, 86)
  ctx.closePath()
  ctx.fillStyle = '#1E8A44'
  ctx.fill()
  ctx.strokeStyle = '#041E0E'
  ctx.lineWidth = 2.5
  ctx.stroke()

  // Gold plated lower jaw
  ctx.beginPath()
  ctx.moveTo(52, 78)
  ctx.bezierCurveTo(66, 78, 82, 77, 97, 76)
  ctx.bezierCurveTo(99, 81, 94, 86, 85, 88)
  ctx.bezierCurveTo(71, 91, 55, 90, 43, 85)
  ctx.closePath()
  ctx.fillStyle = '#E8D284'
  ctx.fill()

  // Teeth
  ctx.fillStyle = '#FFFFFF'
  for (const [tx, ty] of [
    [64, 72],
    [75, 71],
    [86, 70],
    [97, 69],
  ]) {
    ctx.beginPath()
    ctx.moveTo(tx, ty)
    ctx.lineTo(tx + 4, ty + 6)
    ctx.lineTo(tx + 8, ty)
    ctx.closePath()
    ctx.fill()
  }

  // Fierce eye
  ctx.fillStyle = '#040A1C'
  ctx.beginPath()
  ctx.moveTo(60, 52)
  ctx.lineTo(77, 50)
  ctx.lineTo(79, 58)
  ctx.lineTo(64, 60)
  ctx.closePath()
  ctx.fill()

  ctx.fillStyle = '#F59E0B'
  ctx.beginPath()
  ctx.ellipse(70.5, 54.5, 4.5, 3.2, 0, 0, Math.PI * 2)
  ctx.fill()

  ctx.fillStyle = '#FFFFFF'
  ctx.beginPath()
  ctx.arc(72.5, 53.2, 1.4, 0, Math.PI * 2)
  ctx.fill()

  // UF chest mark
  ctx.fillStyle = '#FFFFFF'
  ctx.font = '900 16px Impact, "Arial Black", sans-serif'
  ctx.textAlign = 'center'
  ctx.fillText('UF', 64, 113)

  ctx.restore()
}
