export default function SpectrumTicks({ value, max }: { value: number; max: number }) {
  const lit = max > 0 && value > 0 ? Math.min(6, Math.max(1, Math.round(6 * value / max))) : 0
  return <span aria-hidden="true" className="il-spectrum">
    {lit > 0 && <i style={{ width: `calc(${lit} * var(--il-tick-step) - var(--il-tick-gap))` }}/>}
  </span>
}
