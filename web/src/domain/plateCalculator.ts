export interface PlateLoadout {
  platesPerSide: number[]
  totalWeight: number
  isExact: boolean
}

const MAX_LOAD = 10_000
const INF = 1_000_001
const cents = (value: number) => Math.round(value * 100)
const bounded = (value: number, min: number, max: number) =>
  Number.isFinite(value) ? Math.max(min, Math.min(max, value)) : min
const validPlate = (value: number) => Number.isFinite(value) && value >= 0.01 && value <= 1_000

export function calculateTotalWeight(platesPerSide: readonly number[], barWeight = 45, loadingSides = 2) {
  const sides = Math.trunc(bounded(loadingSides, 1, 16))
  const total = platesPerSide.filter(validPlate).reduce((sum, weight) => sum + weight, 0)
  return cents(Math.min(MAX_LOAD, bounded(barWeight, 0, MAX_LOAD) + sides * total)) / 100
}

export function plateColor(lbs: number) {
  if (lbs >= 55) return '#E74C3C'
  if (lbs >= 45) return '#3498DB'
  if (lbs >= 35) return '#F39C12'
  if (lbs >= 25) return '#2ECC71'
  if (lbs >= 10) return '#ECF0F1'
  return '#BDC3C7'
}

function gcd(a: number, b: number): number {
  while (b) [a, b] = [b, a % b]
  return a
}

function nearestReachable(counts: Int32Array, goal: number, divisor: number) {
  let best = 0, distance = goal
  for (let i = 1; i < counts.length; i++) {
    const gap = Math.abs(i * divisor - goal)
    // Iterating upward means an equal-distance tie keeps the lower load.
    if (counts[i] < INF && gap < distance) {
      best = i
      distance = gap
    }
  }
  return best
}

interface Choice { weight: number; units: number; capacity: number }
function unlimited(options: Choice[], limit: number, goal: number, divisor: number) {
  const counts = new Int32Array(limit + 1).fill(INF)
  const chosen = new Int32Array(limit + 1)
  counts[0] = 0
  for (let amount = 1; amount <= limit; amount++) {
    // Descending denominations keep the larger plate when both paths use the same count.
    for (let i = options.length - 1; i >= 0; i--) {
      const {units} = options[i]
      if (units <= amount && counts[amount - units] + 1 < counts[amount]) {
        counts[amount] = counts[amount - units] + 1
        chosen[amount] = i
      }
    }
  }
  const result: number[] = []
  for (let amount = nearestReachable(counts, goal, divisor); amount > 0;) {
    const choice = options[chosen[amount]]
    result.push(choice.weight)
    amount -= choice.units
  }
  return result
}

// A monotone queue finds the cheapest feasible previous amount for each residue class.
// On equal cost, keeping the oldest queue entry uses more of the current (larger) plate.
function limitedPass(options: Choice[], end: number, limit: number, keepFrom: number) {
  let before = new Int32Array(limit + 1).fill(INF)
  let after = new Int32Array(limit + 1)
  const queue = new Int32Array(limit + 1)
  const used: Uint8Array[] = []
  before[0] = 0
  for (let i = 0; i < end; i++) {
    const {units, capacity} = options[i]
    const take = i < keepFrom ? undefined : new Uint8Array(limit + 1)
    if (take) used.push(take)
    for (let residue = 0; residue < units && residue <= limit; residue++) {
      let head = 0, tail = 0
      for (let amount = residue, k = 0; amount <= limit; amount += units, k++) {
        while (head < tail && queue[head] < k - capacity) head++
        if (before[amount] < INF) {
          const score = before[amount] - k
          while (tail > head) {
            const previous = queue[tail - 1]
            if (before[residue + previous * units] - previous <= score) break
            tail--
          }
          queue[tail++] = k
        }
        const best = queue[head]
        after[amount] = head < tail ? before[residue + best * units] + k - best : INF
        if (take && head < tail) take[amount] = k - best
      }
    }
    ;[before, after] = [after, before]
  }
  return {counts: before, used}
}

function limited(options: Choice[], limit: number, goal: number, divisor: number) {
  let end = options.length
  let amount: number | undefined
  const selected: number[] = []
  // For large custom inventories reconstruct in blocks, bounding retained backpointers to 12 MiB.
  while (end && amount !== 0) {
    const upper: number = amount ?? limit
    const block = Math.max(1, Math.floor(12 * 1024 * 1024 / (upper + 1)))
    const start = Math.max(0, end - block)
    const {counts, used} = limitedPass(options, end, upper, start)
    amount ??= nearestReachable(counts, goal, divisor)
    for (let i = end - 1; i >= start; i--) {
      const count: number = used[i - start][amount]
      for (let j = 0; j < count; j++) selected.push(options[i].weight)
      amount -= count * options[i].units
    }
    end = start
  }
  return selected
}

export function calculatePlates(targetWeight: number, availablePlates: readonly number[], barWeight = 45,
  loadingSides = 2, plateInventory: Readonly<Record<string, number>> | null = null): PlateLoadout {
  const bar = cents(bounded(barWeight, 0, MAX_LOAD)) / 100
  const target = Number.isFinite(targetWeight) ? bounded(targetWeight, 0, MAX_LOAD) : bar
  const sides = Math.trunc(bounded(loadingSides, 1, 16))
  const inventory = new Map<number, number>()
  for (const [raw, count] of Object.entries(plateInventory ?? {})) {
    const weight = Number(raw)
    if (!validPlate(weight)) continue
    const key = cents(weight)
    const cap = Math.floor(bounded(count, 0, 100) / sides)
    inventory.set(key, Math.min(inventory.get(key) ?? cap, cap))
  }
  const values = [...new Set(availablePlates.filter(validPlate).map(cents))].sort((a,b) => a - b)
  const options = values.map(c => ({weight: c / 100, units: c * sides, capacity: inventory.get(c) ?? 0}))
    .filter(option => !plateInventory || option.capacity > 0)
  if (target <= bar || !options.length) return {platesPerSide: [], totalWeight: bar, isExact: Math.abs(target - bar) < 0.01}
  const goal = cents(target - bar)
  const divisor = options.reduce((d, p) => gcd(d, p.units), 0)
  for (const option of options) option.units /= divisor
  const limit = Math.min(
    Math.floor(cents(MAX_LOAD - bar) / divisor),
    Math.ceil(goal / divisor) + (plateInventory ? options[options.length - 1].units : options[0].units),
    plateInventory ? options.reduce((sum, p) => sum + p.capacity * p.units, 0) : Infinity,
  )
  const solver = plateInventory ? limited : unlimited
  const platesPerSide = solver(options, limit, goal, divisor).sort((a,b) => b - a)
  const totalWeight = calculateTotalWeight(platesPerSide, bar, sides)
  return {platesPerSide, totalWeight, isExact: Math.abs(totalWeight - target) < 0.01}
}
