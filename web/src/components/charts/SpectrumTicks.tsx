export default function SpectrumTicks({ value, max }: { value: number; max: number }) {
  const lit = max && value ? Math.max(1, Math.round(6 * value / max)) : 0
  return <span aria-hidden="true" className="il-spectrum">{Array.from({ length: 6 }, (_, i) => <i key={i} className={i < lit ? 'is-lit' : undefined} />)}</span>
}
