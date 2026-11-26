import { useEffect, useRef } from 'react'
import { useLiveQuery } from 'dexie-react-hooks'
import { useStore } from 'zustand'
import { createStore } from 'zustand/vanilla'
import { db, defaultPreferences } from '../db/database'

const storageKey = 'ironlog.rest.endEpochMs'

export function secondsRemaining(endEpochMs: number | null, now = Date.now()) {
  return endEpochMs === null ? 0 : Math.max(0, Math.ceil((endEpochMs - now) / 1000))
}

function parseDeadline(raw: string | null) {
  const value = Number(raw)
  return Number.isSafeInteger(value) && value > 0 ? value : null
}

function readDeadline() {
  try {
    return parseDeadline(window.localStorage.getItem(storageKey))
  } catch {
    return null
  }
}

const initialDeadline = readDeadline()
export const restClock = createStore(() => ({
  endEpochMs: initialDeadline,
  remaining: secondsRemaining(initialDeadline),
}))

function setDeadline(endEpochMs: number | null, persist = true) {
  if (persist) {
    try {
      if (endEpochMs === null) window.localStorage.removeItem(storageKey)
      else window.localStorage.setItem(storageKey, String(endEpochMs))
    } catch {
      // The in-memory clock still works when browser storage is unavailable.
    }
  }
  restClock.setState({ endEpochMs, remaining: secondsRemaining(endEpochMs) })
}

export function startRestTimer(seconds: number) {
  if (!Number.isFinite(seconds) || seconds <= 0) {
    stopRestTimer()
    return
  }
  setDeadline(Date.now() + Math.max(1, Math.round(seconds)) * 1000)
}

export function stopRestTimer() {
  setDeadline(null)
}

export function adjustRestTimer(deltaSeconds: number) {
  const { endEpochMs } = restClock.getState()
  if (endEpochMs === null || !Number.isFinite(deltaSeconds)) return
  const now = Date.now()
  const base = deltaSeconds > 0 ? Math.max(now, endEpochMs) : endEpochMs
  const deadline = base + Math.round(deltaSeconds) * 1000
  setDeadline(deadline > now ? deadline : null)
}

export function useRestTimerRemaining() {
  return useStore(restClock, state => state.remaining)
}

export function formatRestTime(value: number) {
  const seconds = Number.isFinite(value) ? Math.max(0, Math.floor(value)) : 0
  return [Math.floor(seconds / 60), seconds % 60].map(part => String(part).padStart(2, '0')).join(':')
}

// Keep one unlocked context across navigation. Retry on a gesture after an iOS interruption.
let audio: AudioContext | undefined
function unlockAudio() {
  try {
    const Context = window.AudioContext ?? (window as Window & { webkitAudioContext?: typeof AudioContext }).webkitAudioContext
    if (!Context) return
    if (!audio || audio.state === 'closed') audio = new Context()
    if (audio.state !== 'running') void audio.resume().catch(() => {})
  } catch {
    // Audio is optional on embedded browsers.
  }
}

function ringBell() {
  if (!audio || audio.state !== 'running') return
  const context = audio
  const start = context.currentTime
  const gain = context.createGain()
  gain.connect(context.destination)
  gain.gain.setValueAtTime(0.0001, start)
  gain.gain.exponentialRampToValueAtTime(0.19, start + 0.008)
  gain.gain.exponentialRampToValueAtTime(0.015, start + 0.95)
  gain.gain.linearRampToValueAtTime(0, start + 1.45)

  // Inharmonic partials reproduce a short, metallic struck bell.
  const partials = [[720, 0.62], [1134, 0.24], [2011, 0.11], [3054, 0.03]]
  let unfinished = partials.length
  for (const [frequency, level] of partials) {
    const oscillator = context.createOscillator()
    const partial = context.createGain()
    oscillator.type = 'sine'
    oscillator.frequency.value = frequency
    partial.gain.value = level
    oscillator.connect(partial)
    partial.connect(gain)
    oscillator.onended = () => {
      oscillator.disconnect()
      partial.disconnect()
      if (--unfinished === 0) gain.disconnect()
    }
    oscillator.start(start)
    oscillator.stop(start + 1.45)
  }
}

export function attachRestClock(onComplete: () => void) {
  let timeout: ReturnType<typeof setTimeout> | undefined
  function refresh(resuming = false) {
    clearTimeout(timeout)
    const { endEpochMs, remaining } = restClock.getState()
    if (endEpochMs === null) return
    const ms = endEpochMs - Date.now()
    if (ms <= 0) {
      stopRestTimer()
      // iOS can freeze a background tab for hours. Never ring an old rest bell on return.
      if (document.visibilityState === 'visible' && (!resuming || ms > -1000)) onComplete()
      return
    }
    const seconds = Math.ceil(ms / 1000)
    if (remaining !== seconds) restClock.setState({ remaining: seconds })
    if (document.visibilityState === 'visible') {
      timeout = setTimeout(refresh, ms - (seconds - 1) * 1000)
    }
  }
  function onStorage(event: StorageEvent) {
    if (event.key === storageKey || event.key === null) setDeadline(readDeadline(), false)
  }
  const unsubscribe = restClock.subscribe((current, previous) => {
    if (current.endEpochMs !== previous.endEpochMs) refresh()
  })
  const onReturn = () => refresh(true)
  document.addEventListener('visibilitychange', onReturn)
  window.addEventListener('pageshow', onReturn)
  window.addEventListener('storage', onStorage)
  refresh(true)
  return () => {
    clearTimeout(timeout)
    unsubscribe()
    document.removeEventListener('visibilitychange', onReturn)
    window.removeEventListener('pageshow', onReturn)
    window.removeEventListener('storage', onStorage)
  }
}

export function keepDisplayAwake() {
  let sentinel: WakeLockSentinel | undefined
  let requestPending = false
  let disposed = false

  async function acquire() {
    if (disposed || document.visibilityState !== 'visible' || !navigator.wakeLock ||
        requestPending || (sentinel && !sentinel.released)) return
    requestPending = true
    try {
      const lock = await navigator.wakeLock.request('screen')
      if (disposed || document.visibilityState !== 'visible') {
        await lock.release()
        return
      }
      sentinel = lock
    } catch {
      // Low Power Mode, Permissions Policy, and the OS may reject this request.
    } finally {
      requestPending = false
    }
  }
  document.addEventListener('visibilitychange', acquire)
  window.addEventListener('pageshow', acquire)
  void acquire()
  return () => {
    disposed = true
    document.removeEventListener('visibilitychange', acquire)
    window.removeEventListener('pageshow', acquire)
    if (sentinel && !sentinel.released) void sentinel.release().catch(() => {})
  }
}

export function useRestTimerLifecycle(activeWorkout: boolean | undefined) {
  const prefs = useLiveQuery(() => db.preferences.get('default'), [])
  const preferences = useRef(prefs)
  preferences.current = prefs

  useEffect(() => {
    if (activeWorkout === false) stopRestTimer()
  }, [activeWorkout])

  useEffect(() => {
    const detach = attachRestClock(() => {
      const current = preferences.current ?? defaultPreferences
      if (current.timerAudioEnabled) ringBell()
      if (current.timerVibrateEnabled) navigator.vibrate?.(500)
    })
    document.addEventListener('click', unlockAudio, true)
    document.addEventListener('keydown', unlockAudio, true)
    return () => {
      detach()
      document.removeEventListener('click', unlockAudio, true)
      document.removeEventListener('keydown', unlockAudio, true)
    }
  }, [])

  useEffect(() => {
    if (activeWorkout && prefs?.keepScreenOn) return keepDisplayAwake()
  }, [activeWorkout, prefs?.keepScreenOn])
}
