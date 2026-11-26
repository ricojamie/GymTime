import type { DistanceUnit, WeightUnit } from '../db/types'

export const LB_PER_KG = 2.20462
export const toLbs = (value: number, unit: WeightUnit) => unit === 'kg' ? value * LB_PER_KG : value
export const fromLbs = (lbs: number, unit: WeightUnit) => unit === 'kg' ? lbs / LB_PER_KG : lbs
export const weightLabel = (unit: WeightUnit) => unit === 'kg' ? 'kg' : 'lb'

// Only round at the display boundary. Inputs, history and backups keep the stored value.
export function formatWeight(lbs: number, unit: WeightUnit = 'lbs'): string {
  if (!Number.isFinite(lbs)) return '—'
  return String(Number(fromLbs(lbs, unit).toFixed(2)))
}

export const distanceLabels: Record<DistanceUnit, string> = {
  METERS: 'm', KILOMETERS: 'km', YARDS: 'yd', FEET: 'ft', MILES: 'mi', STEPS: 'steps', FLOORS: 'floors',
}
const meterFactors = { METERS: 1, KILOMETERS: 1000, MILES: 1609.344, FEET: 0.3048, YARDS: 0.9144 }
export function isConvertibleToMeters(unit: DistanceUnit): unit is keyof typeof meterFactors {
  return Object.hasOwn(meterFactors, unit)
}
export function distanceToMeters(value: number | null, unit: DistanceUnit | null): number | null {
  return value !== null && Number.isFinite(value) && unit && isConvertibleToMeters(unit)
    ? value * meterFactors[unit] : null
}
export function metersToDistance(value: number, unit: DistanceUnit): number | null {
  return Number.isFinite(value) && isConvertibleToMeters(unit) ? value / meterFactors[unit] : null
}
