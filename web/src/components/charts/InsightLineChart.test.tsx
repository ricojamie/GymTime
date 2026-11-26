// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import InsightLineChart from './InsightLineChart'

const points = [
  { workoutId: 1, timestampMs: new Date(2026, 8, 1).getTime(), value: 125 },
  { workoutId: 2, timestampMs: new Date(2026, 8, 8).getTime(), value: 130 },
]
const formatValue = (n: number) => n + ' lb'
afterEach(cleanup)

describe('lift chart accessibility', () => {
  it('opens on the latest session and allows bounded keyboard scrubbing', () => {
    render(<InsightLineChart points={points} formatValue={formatValue} label="Estimated max"/>)
    const chart = screen.getByRole('slider', { name: 'Estimated max by session' })
    expect(chart.getAttribute('aria-valuenow')).toBe('2')
    fireEvent.keyDown(chart, {key:'ArrowLeft'})
    expect(chart.getAttribute('aria-valuetext')).toContain('125 lb')
    fireEvent.keyDown(chart, {key:'ArrowLeft'})
    expect(chart.getAttribute('aria-valuenow')).toBe('1')
    fireEvent.keyDown(chart, {key:'End'})
    expect(chart.getAttribute('aria-valuetext')).toContain('130 lb')
  })
  it('draws a single point without nonfinite coordinates', () => {
    const view = render(<InsightLineChart points={points.slice(0,1)} formatValue={formatValue} label="Estimated max"/>)
    expect(view.container.querySelector('polyline')?.getAttribute('points')).toMatch(/^300,[\d.]+$/)
  })
  it('keeps the series visible while a captured finger selects sessions immediately', () => {
    const view = render(<InsightLineChart points={points} formatValue={formatValue} label="Estimated max"/>)
    const chart = screen.getByRole('slider')
    const getBounds = vi.spyOn(chart, 'getBoundingClientRect').mockReturnValue(new DOMRect(20, 0, 300, 130))
    Object.defineProperty(chart, 'setPointerCapture', { value: vi.fn() })
    const trace = view.container.querySelector('polyline')!
    const line = trace.getAttribute('points')
    const send = (type: string, clientX: number, pointerId = 1) =>
      fireEvent(chart, Object.assign(new Event(type, { bubbles: true }), { clientX, pointerId }))

    send('pointerdown', 21)
    expect(chart.getAttribute('aria-valuetext')).toContain('125 lb')
    expect(view.container.querySelector('.il-chart-reticle')).toBeNull()
    send('pointermove', 319, 2)
    expect(chart.getAttribute('aria-valuenow')).toBe('1')
    send('lostpointercapture', 319, 2)
    send('pointermove', 319)
    expect(chart.getAttribute('aria-valuetext')).toContain('130 lb')
    send('lostpointercapture', 319)
    send('pointermove', 21)
    expect(chart.getAttribute('aria-valuenow')).toBe('2')
    expect(getBounds).toHaveBeenCalledTimes(1)
    expect(trace.getAttribute('points')).toBe(line)
  })
  it('shows the empty state without announcing a slider', () => {
    render(<InsightLineChart points={[]} formatValue={formatValue} label="Estimated max"/>)
    expect(screen.queryByRole('slider')).toBeNull()
    expect(screen.getByText('No comparable sessions in this period.')).toBeDefined()
  })
})
