import './charts.css'
import { useId, useMemo, useRef, useState } from 'react'
import type { KeyboardEvent, PointerEvent } from 'react'

type Point = { timestampMs: number; value: number; workoutId: number }
type Props = { points: Point[]; formatValue: (value: number) => string; label: string }

const date = new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
const width = 600
const left = 12
const right = 588
const top = 12
const bottom = 116

function plotGeometry(points: Point[]) {
  if (!points.length) return null
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
  const positions = points.map(p => ({ x: x(p.timestampMs), y: y(p.value) }))
  const line = positions.map(p => p.x + ',' + p.y).join(' ')
  const area = positions.length > 1
    ? `${positions[0].x},${bottom} ${line} ${positions[positions.length - 1].x},${bottom}`
    : ''
  return { min, max, positions, line, area }
}

export default function InsightLineChart({ points, formatValue, label }: Props) {
  const [selected, setSelected] = useState<number | null>(null)
  const capture = useRef<{ id: number; left: number; width: number } | null>(null)
  const chartId = useId()
  const gradId = chartId.replace(/[^a-zA-Z0-9_-]/g, '') + '-area'
  const geometry = useMemo(() => plotGeometry(points), [points])
  if (!geometry) return <div className="il-chart-empty">No comparable sessions in this period.</div>

  const { min, max, positions, line, area } = geometry
  const index = Math.min(selected ?? points.length - 1, points.length - 1)
  const current = points[index]
  const position = positions[index]
  const first = points[0]
  const last = points[points.length - 1]
  const lastPosition = positions[positions.length - 1]
  const pick = (event: PointerEvent<SVGSVGElement>) => {
    const box = capture.current
    if (!box || box.id !== event.pointerId || !box.width) return
    const at = (event.clientX - box.left) / box.width * width
    let nearest = 0
    for (let i = 1; i < points.length; i++) {
      if (Math.abs(positions[i].x - at) < Math.abs(positions[nearest].x - at)) nearest = i
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
    <figcaption>
      <strong>{formatValue(current.value)}</strong>
      <time className="il-chart-callout" data-active={selected !== null} dateTime={new Date(current.timestampMs).toISOString()}>
        {date.format(current.timestampMs)}
      </time>
    </figcaption>
    <div className="il-line-plot"><div className="il-chart-scale" aria-hidden="true"><span>{formatValue(max)}</span><span>{formatValue(min)}</span></div>
    <svg viewBox={`0 0 ${width} 130`} preserveAspectRatio="none" tabIndex={0}
      role="slider" aria-label={label + ' by session'} aria-orientation="horizontal" aria-valuemin={1} aria-valuemax={points.length}
      aria-valuenow={index + 1} aria-valuetext={date.format(current.timestampMs) + ', ' + formatValue(current.value)}
      aria-describedby={chartId} onKeyDown={key} onPointerDown={event => {
        if (capture.current) return
        const box = event.currentTarget.getBoundingClientRect()
        capture.current = { id: event.pointerId, left: box.left, width: box.width }
        event.currentTarget.setPointerCapture(event.pointerId)
        pick(event)
      }} onPointerMove={pick}
      onLostPointerCapture={event => {
        if (capture.current?.id === event.pointerId) capture.current = null
      }}>
      <defs>
        <linearGradient id={gradId} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="var(--accent-ink)" stopOpacity="0.24" />
          <stop offset="100%" stopColor="var(--accent-ink)" stopOpacity="0" />
        </linearGradient>
      </defs>
      {[top, (top + bottom) / 2, bottom].map(h => <line key={h} x1={left} x2={right} y1={h} y2={h} className="il-chart-grid" />)}
      {area && <polygon className="il-chart-area" points={area} fill={`url(#${gradId})`} />}
      <polyline className="il-chart-line" pathLength={1} points={line} />
      <line className="il-crosshair" x1={position.x} x2={position.x} y1={top} y2={bottom} />
      {selected == null && <g aria-hidden="true" transform={`translate(${lastPosition.x} ${lastPosition.y})`}>
        <path key={last.workoutId} className="il-chart-reticle" d="M-7 -3v-4h4 M3 -7h4v4 M7 3v4H3 M-3 7h-4V3"/>
      </g>}
      <svg x={position.x - 10} y={position.y - 10} width="20" height="20">
        <circle r="7" cx="10" cy="10" className="il-focus-halo" />
        <circle r="4" cx="10" cy="10" className="il-focus-point" />
      </svg>
    </svg>
    </div>
    <div className="il-chart-ends"><span>{date.format(first.timestampMs)}</span>{points.length > 1 && <span>{date.format(last.timestampMs)}</span>}</div>
    <span id={chartId} className="il-sr-only">Tap or drag to a session. Use the arrow keys to step through values.</span>
  </figure>
}
