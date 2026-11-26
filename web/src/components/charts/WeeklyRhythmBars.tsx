type Week = { startDate: number; workouts: number; isCurrentWeek: boolean; isPartialWeek: boolean }
const date = new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric' })

export default function WeeklyRhythmBars({ weeks }: { weeks: Week[] }) {
  if (!weeks.length) return null
  const peak = Math.max(1, ...weeks.map(w => w.workouts))
  const width = 480
  const step = width / weeks.length
  const gap = Math.min(12, step * 0.28)
  // One highlight for the entire series. The bars underneath keep their actual heights.
  const crests = weeks.flatMap((week, i) => week.workouts
    ? [`M${i * step + gap / 2} ${80 - 72 * week.workouts / peak}h${step - gap}`]
    : []).join(' ')
  return <figure className="il-rhythm-figure">
    <svg viewBox="0 0 480 88" preserveAspectRatio="none" role="img" aria-label={'Weekly workouts: ' + weeks.map(w => date.format(w.startDate) + ', ' + w.workouts + (w.isPartialWeek ? ', partial week' : '')).join('. ')}>
      {[8, 44, 80].map(y => <line key={y} x1="0" x2={width} y1={y} y2={y} className="il-chart-grid" />)}
      {weeks.map((week, i) => {
        const height = 72 * week.workouts / peak
        const x = i * step + gap / 2
        return <g key={week.startDate} className={week.isCurrentWeek ? 'il-bar-current' : 'il-bar-past'}>
          {height > 0 && <rect x={x} y={80 - height} width={step-gap} height={height} />}
          <line x1={x} x2={x+step-gap} y1={83} y2={83} className={week.isPartialWeek ? 'il-partial' : 'il-full'} />
        </g>
      })}
      {crests && <path key={weeks[0].startDate} aria-hidden="true" className="il-rhythm-crests" d={crests} />}
    </svg>
    <figcaption className="il-chart-ends"><span>{date.format(weeks[0].startDate)}</span><span>{date.format(weeks[weeks.length-1].startDate)}</span></figcaption>
  </figure>
}
