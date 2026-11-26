import { calculateStreak } from './streakCalculator'
import type { TrainingSource } from './analyticsCore'
import { canonicalMuscle, completedData, groupBy, localDay, muscleNames, positive, round1, shiftDays, volumeOf, weekStart } from './analyticsCore'

export function calculateMonthlyReport(source: TrainingSource, now = Date.now(), allowedRestDays = 2) {
  const today = new Date(now)
  const monthEdge = (offset: number) => +new Date(today.getFullYear(), today.getMonth() + offset, 1)
  const end = monthEdge(0), periodStart = monthEdge(-1), priorStart = monthEdge(-2)
  const monthLabel = new Date(periodStart).toLocaleDateString(undefined, {month:'long',year:'numeric'})
  const {workouts, sets, exercises} = completedData(source, now)
  const current = sets.filter(s => s.timestamp >= periodStart && s.timestamp < end)
  const previous = sets.filter(s => s.timestamp >= priorStart && s.timestamp < periodStart)
  const sessions = (ids: number[], start: number, stop: number) => [...new Set(ids)].map(id => workouts.get(id)!).filter(w => w.startTime >= start && w.startTime < stop)
  const monthWorkouts = sessions(current.map(s => s.workoutId),periodStart,end)
  const previousWorkoutCount = sessions(previous.map(s => s.workoutId),priorStart,periodStart).length
  const ratings = monthWorkouts.flatMap(w => w.rating !== null && w.rating >= 1 && w.rating <= 5 ? [w.rating] : [])
  const totalVolume = current.reduce((n,s) => n + volumeOf(s),0)
  const previousTotalVolume = previous.reduce((n,s) => n + volumeOf(s),0)
  const pct = (a:number,b:number) => b > 0 ? round1(100 * (a - b) / b) : null
  const counts = groupBy(current, s => canonicalMuscle(exercises.get(s.exerciseId)!.targetMuscle).toLowerCase())
  const countsByMuscle = muscleNames(source).map(muscle => ({muscle,setCount:counts.get(muscle.toLowerCase())?.length ?? 0}))
  countsByMuscle.sort((a,b) => b.setCount - a.setCount || a.muscle.localeCompare(b.muscle))
  const topMuscles = countsByMuscle.filter(m => m.setCount > 0).slice(0,5)
  const undertrainedMuscles = countsByMuscle.filter(m => m.setCount < 6).map(m => m.muscle)
  const historicalMax = new Map<number,number>()
  for (const s of sets) {
    if (s.timestamp < periodStart && positive(s.weight) && positive(s.reps))
      historicalMax.set(s.exerciseId,Math.max(historicalMax.get(s.exerciseId) ?? 0,s.weight))
  }
  const records = groupBy(current.filter(s => positive(s.weight) && positive(s.reps)), s => s.exerciseId)
  const newPRs = [...records].flatMap(([exerciseId,group]) => {
    const heaviest = group.reduce((a,b) => b.weight! > a.weight! || (b.weight === a.weight && b.reps! > a.reps!) ? b : a)
    return heaviest.weight! > (historicalMax.get(exerciseId) ?? 0) ?
      [{exerciseId,exerciseName:exercises.get(exerciseId)!.name,weight:heaviest.weight!,reps:heaviest.reps!}] : []
  }).sort((a,b) => b.weight - a.weight || b.reps - a.reps)
  const trainingDays = new Set(current.map(s => localDay(s.timestamp))).size
  const activeWeeks = new Set(current.map(s => weekStart(s.timestamp,0))).size
  let weeksInPeriod = 0
  for (let week = weekStart(periodStart,0); week < end; week = shiftDays(week,7)) weeksInPeriod++
  const endOfMonthStreakDays = calculateStreak(sets.filter(s => s.timestamp < end), allowedRestDays, end - 1).streakDays
  const count = monthWorkouts.length
  const volumeChangePercent = pct(totalVolume,previousTotalVolume)
  let narrative = count ? 'You completed ' + count + (count === 1 ? ' workout' : ' workouts') + ' across ' +
    trainingDays + (trainingDays === 1 ? ' training day.' : ' training days.') : 'No completed workouts in ' + monthLabel + '.'
  if (count && volumeChangePercent !== null) narrative += ' Volume ' + (volumeChangePercent === 0 ? 'held steady.' :
    (volumeChangePercent > 0 ? 'rose ' : 'fell ') + Math.abs(volumeChangePercent) + '% from the prior month.')
  if (newPRs.length) narrative += ' ' + newPRs[0].exerciseName + ' set a new weight record.'
  return {monthLabel, periodStart, periodEnd:end - 1, workoutCount:count, previousWorkoutCount, totalVolume, previousTotalVolume,
    totalWorkingSets:current.length,previousWorkingSets:previous.length,exerciseCount:new Set(current.map(s => s.exerciseId)).size,
    volumeChangePercent,workingSetChangePercent:pct(current.length,previous.length), trainingDays, activeWeeks, weeksInPeriod, endOfMonthStreakDays, topMuscles,
    undertrainedMuscles, newPRs, averageRating:ratings.length ? ratings.reduce((n,r) => n + r,0) / ratings.length : null, narrative}
}
