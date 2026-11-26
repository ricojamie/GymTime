// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { adjustRestTimer, attachRestClock, formatRestTime, keepDisplayAwake, restClock, secondsRemaining, startRestTimer, stopRestTimer } from './restTimer'

const epoch = new Date('2026-11-26T14:00:00Z').valueOf()
let visible = true
const cleanups: (() => void)[] = []

function setVisible(value: boolean) {
  visible = value
  document.dispatchEvent(new Event('visibilitychange'))
}

beforeEach(() => {
  vi.useFakeTimers()
  vi.setSystemTime(epoch)
  visible = true
  vi.spyOn(document, 'visibilityState', 'get').mockImplementation(() => visible ? 'visible' : 'hidden')
  localStorage.clear()
  stopRestTimer()
})
afterEach(() => {
  for (const cleanup of cleanups.splice(0)) cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
  vi.useRealTimers()
})

describe('rest countdown', () => {
  it('keeps partial seconds visible and formats minutes without wrapping at one hour', () => {
    expect(secondsRemaining(epoch + 1001, epoch)).toBe(2)
    expect(secondsRemaining(null, epoch)).toBe(0)
    expect(secondsRemaining(epoch - 300, epoch)).toBe(0)
    expect(formatRestTime(3601)).toBe('60:01')
  })

  it('persists one wall-clock deadline and rings once on foreground expiry', () => {
    const ring = vi.fn()
    cleanups.push(attachRestClock(ring))
    startRestTimer(3)
    expect(localStorage.getItem('ironlog.rest.endEpochMs')).toBe(String(epoch + 3000))
    vi.advanceTimersByTime(1100)
    expect(restClock.getState().remaining).toBe(2)
    vi.advanceTimersByTime(2700)
    window.dispatchEvent(new Event('pageshow'))
    expect(restClock.getState()).toEqual({ endEpochMs: null, remaining: 0 })
    expect(ring).toHaveBeenCalledTimes(1)
    expect(localStorage.getItem('ironlog.rest.endEpochMs')).toBeNull()
  })

  it('immediately resynchronizes on visibility return after suspension', () => {
    const ring = vi.fn()
    cleanups.push(attachRestClock(ring))
    startRestTimer(90)
    setVisible(false)
    vi.setSystemTime(epoch + 60600)
    setVisible(true)
    expect(restClock.getState().remaining).toBe(30)
    setVisible(false)
    vi.setSystemTime(epoch + 3600000)
    setVisible(true)
    expect(restClock.getState().remaining).toBe(0)
    expect(ring).not.toHaveBeenCalled()
  })

  it('adjusts the actual deadline without rounding up partial seconds', () => {
    const ring = vi.fn()
    cleanups.push(attachRestClock(ring))
    startRestTimer(10)
    vi.advanceTimersByTime(1600)
    adjustRestTimer(5)
    expect(restClock.getState().endEpochMs).toBe(epoch + 15000)
    adjustRestTimer(-30)
    expect(restClock.getState().endEpochMs).toBeNull()
    expect(ring).not.toHaveBeenCalled()
  })

  it('reflects skips and restarts from another same-origin tab', () => {
    cleanups.push(attachRestClock(vi.fn()))
    const key = 'ironlog.rest.endEpochMs'
    localStorage.setItem(key, String(epoch + 8000))
    window.dispatchEvent(new StorageEvent('storage', { key }))
    expect(restClock.getState().remaining).toBe(8)
    localStorage.removeItem(key)
    window.dispatchEvent(new StorageEvent('storage', { key }))
    expect(restClock.getState().remaining).toBe(0)
  })

  it('keeps a running in-memory timer when local storage fails', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('Storage unavailable') })
    startRestTimer(7)
    expect(restClock.getState().remaining).toBe(7)
  })
})

describe('screen wake lock', () => {
  it('reacquires a system-released lock when the workout becomes visible', async () => {
    const first = { released: false, release: vi.fn().mockResolvedValue(undefined) }
    const second = { released: false, release: vi.fn().mockResolvedValue(undefined) }
    const request = vi.fn().mockResolvedValueOnce(first).mockResolvedValueOnce(second)
    vi.stubGlobal('navigator', { wakeLock: { request } })
    const release = keepDisplayAwake()
    await Promise.resolve()
    setVisible(false)
    first.released = true
    setVisible(true)
    await Promise.resolve()
    expect(request).toHaveBeenCalledTimes(2)
    expect(request).toHaveBeenLastCalledWith('screen')
    release()
    expect(second.release).toHaveBeenCalledOnce()
  })

  it('releases a pending lock if the workout finishes while requesting it', async () => {
    const lock = { released: false, release: vi.fn().mockResolvedValue(undefined) }
    let resolve!: (value: typeof lock) => void
    const pending = new Promise<typeof lock>(done => { resolve = done })
    vi.stubGlobal('navigator', { wakeLock: { request: () => pending } })
    const release = keepDisplayAwake()
    release()
    resolve(lock)
    await pending
    expect(lock.release).toHaveBeenCalledOnce()
  })
})
