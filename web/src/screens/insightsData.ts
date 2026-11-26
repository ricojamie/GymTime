import { useLiveQuery } from 'dexie-react-hooks'
import { useEffect, useState } from 'react'
import { db } from '../db/database'

export function useInsightsSource() {
  return useLiveQuery(() => db.transaction('r', [db.workouts, db.sets, db.exercises, db.muscle_groups, db.preferences], async () => {
    const [workouts, sets, exercises, muscles, preferences] = await Promise.all([
      db.workouts.toArray(), db.sets.toArray(), db.exercises.toArray(), db.muscle_groups.toArray(), db.preferences.get('default'),
    ])
    return { workouts, sets, exercises, muscleNames: muscles.map(m => m.name), unit: preferences?.preferredWeightUnit ?? 'lbs' }
  }), [])
}

export function useReportClock() {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const refresh = () => { if (document.visibilityState === 'visible') setNow(Date.now()) }
    document.addEventListener('visibilitychange', refresh)
    const interval = window.setInterval(refresh, 60000)
    return () => { document.removeEventListener('visibilitychange', refresh); window.clearInterval(interval) }
  }, [])
  return now
}
export const shortDate = new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric' })
export const fullDate = new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
export const numberFormat = new Intl.NumberFormat(undefined, { maximumFractionDigits: 1 })
