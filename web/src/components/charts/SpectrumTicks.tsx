import type { CSSProperties } from 'react'

export default function SpectrumTicks({ value, max }: { value: number; max: number }) {
  const lit = max > 0 && value > 0 ? Math.min(6, Math.max(1, Math.round(6 * value / max))) : 0
  return <span aria-hidden="true" className="il-spectrum">
    {Array.from({ length: 6 }, (_, i) => (
      <span
        key={i}
        className="il-spectrum-tick"
        data-lit={i < lit ? 'true' : 'false'}
        style={{ '--tick-i': i } as CSSProperties}
      />
    ))}
  </span>
}
