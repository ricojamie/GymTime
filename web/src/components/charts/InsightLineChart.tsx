import './charts.css'
import { useId, useRef, useState } from 'react'
import type { KeyboardEvent, PointerEvent } from 'react'

type Point = { timestampMs: number; value: number; workoutId: number }
type Props = { points: Point[]; formatValue: (value: number) => string; label: string }

const date = new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
const width = 600
const left = 12
const right = 588
const top = 12
const bottom = 116

export default function InsightLineChart({ points, formatValue, label }: Props) {
  const [selected, setSelected] = useState<number | null>(null)
  const capture = useRef<number | null>(null)
  const chartId = useId()
  if (!points.length) return <div className="il-chart-empty">No comparable sessions in this period.</div>

  const index = Math.min(selected ?? points.length - 1, points.length - 1)
  const current = points[index]
  const first = points[0]
  const last = points[points.length - 1]
  const values = points.map(p => p.value)
  const low = Math.min(...values)
  const high = Math.max(...values)
  const pad = Math.max((high - low) * 0.12, high * 0.02, 0.01)
  const min = Math.max(0, low - pad)
  const max = high + pad
  const x = (time: number) => last.timestampMs === first.timestampMs ? width / 2 : left + (right - left) * (time - first.timestampMs) / (last.timestampMs - first.timestampMs)
  const y = (value: number) => bottom - (bottom - top) * (value - min) / (max - min)
  const pick = (event: PointerEvent<SVGSVGElement>) => {
    const box = event.currentTarget.getBoundingClientRect()
    const at = (event.clientX - box.left) / box.width * width
    let nearest = 0
    for (let i = 1; i < points.length; i++) {
      if (Math.abs(x(points[i].timestampMs) - at) < Math.abs(x(points[nearest].timestampMs) - at)) nearest = i
    }
    setSelected(nearest)
  }
  const key = (event: KeyboardEvent<SVGSVGElement>) => {
    const targets: Record<string, number> = { ArrowLeft: index - 1, ArrowDown: index - 1, ArrowRight: index + 1, ArrowUp: index + 1, Home: 0, End: points.length - 1 }
    if (!(event.key in targets)) return
    event.preventDefault()
    setSelected(Math.max(0, Math.min(targets[event.key], points.length - 1)))
  }

  return <figure className="il-line-chart">
    <figcaption><strong>{formatValue(current.value)}</strong><time dateTime={new Date(current.timestampMs).toISOString()}>{date.format(current.timestampMs)}</time></figcaption>
    <div className="il-line-plot"><div className="il-chart-scale" aria-hidden="true"><span>{formatValue(max)}</span><span>{formatValue(min)}</span></div>
    <svg viewBox={`0 0 ${width} 130`} preserveAspectRatio="none" tabIndex={0}
      role="slider" aria-label={label + ' by session'} aria-orientation="horizontal" aria-valuemin={1} aria-valuemax={points.length}
      aria-valuenow={index + 1} aria-valuetext={date.format(current.timestampMs) + ', ' + formatValue(current.value)}
      aria-describedby={chartId} onKeyDown={key} onPointerDown={event => {
        capture.current = event.pointerId
        event.currentTarget.setPointerCapture(event.pointerId)
        pick(event)
      }} onPointerMove={event => { if (capture.current === event.pointerId) pick(event) }}
      onLostPointerCapture={() => { capture.current = null }}>
      {[top, (top + bottom) / 2, bottom].map(h => <line key={h} x1={left} x2={right} y1={h} y2={h} className="il-chart-grid" />)}
      <polyline className="il-chart-line" points={points.map(p => x(p.timestampMs) + ',' + y(p.value)).join(' ')} />
      <line className="il-crosshair" x1={x(current.timestampMs)} x2={x(current.timestampMs)} y1={top} y2={bottom} />
      <svg x={x(current.timestampMs) - 5} y={y(current.value) - 5} width="10" height="10"><circle r="4" cx="5" cy="5" className="il-focus-point" /></svg>
    </svg>
    </div>
    <div className="il-chart-ends"><span>{date.format(first.timestampMs)}</span>{points.length > 1 && <span>{date.format(last.timestampMs)}</span>}</div>
    <span id={chartId} className="il-sr-only">Tap or drag to a session. Use the arrow keys to step through values.</span>
  </figure>
}
