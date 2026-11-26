export function calculateE1RM(weight: number | null, reps: number | null): number | null {
  if (weight === null || reps === null || !Number.isFinite(weight) || !Number.isInteger(reps)
      || weight <= 0 || reps < 1 || reps > 15) return null
  const estimate = weight * (1 + reps / 30)
  return Number.isFinite(estimate) ? estimate : null
}

export function calculateE10RM(weight: number | null, reps: number | null): number | null {
  const estimate = calculateE1RM(weight, reps)
  return estimate === null ? null : estimate / (1 + 10 / 30)
}

export function roundToNearest2_5(weight: number): number {
  return Number.isFinite(weight) ? Math.round(weight / 2.5) * 2.5 : 0
}
