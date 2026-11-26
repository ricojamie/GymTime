import { useEffect, useId, useRef, useState, type ReactNode } from 'react'
import { ArrowLeft, ChevronRight } from 'lucide-react'
import { Link } from 'react-router-dom'
import { SegmentedControl } from '../components/primitives'
import { updatePreferences } from '../db/repositories'
import type { Preferences } from '../db/types'
import '../screens/settings.css'

export function SettingsHeader({
  title,
  back = '/settings',
  badge,
}: {
  title: string
  back?: string
  badge?: ReactNode
}) {
  return (
    <header className="st-header">
      <Link
        className="icon-button st-back-btn"
        aria-label={back === '/' ? 'Back to Home' : 'Back to settings'}
        to={back}
      >
        <ArrowLeft size={21} />
      </Link>
      <h1>{title}</h1>
      {badge && <span className="st-header-badge">{badge}</span>}
    </header>
  )
}

export function Group({ title, children }: { title: string; children: ReactNode }) {
  const id = useId()
  return (
    <section className="st-group stagger-in" aria-labelledby={id}>
      <h2 className="eyebrow" id={id}>
        {title}
      </h2>
      <div className="st-surface">{children}</div>
    </section>
  )
}

export function SettingLink({
  to,
  children,
  meta,
}: {
  to: string
  children: ReactNode
  meta?: string
}) {
  return (
    <Link className="st-row st-action" to={to}>
      <span>{children}</span>
      <span className="st-trailing">
        {meta}
        <ChevronRight className="st-link-chevron" size={16} />
      </span>
    </Link>
  )
}

export function ToggleRow({
  label,
  checked,
  onChange,
}: {
  label: string
  checked: boolean
  onChange: () => void
}) {
  const [bounce, setBounce] = useState<'idle' | 'on' | 'off'>('idle')
  const timerRef = useRef<number | null>(null)

  useEffect(() => {
    return () => {
      if (timerRef.current !== null) window.clearTimeout(timerRef.current)
    }
  }, [])

  function handleClick() {
    if (timerRef.current !== null) window.clearTimeout(timerRef.current)
    setBounce(checked ? 'off' : 'on')
    timerRef.current = window.setTimeout(() => {
      setBounce('idle')
      timerRef.current = null
    }, 460)
    onChange()
  }

  return (
    <button
      type="button"
      className="st-row st-action st-toggle-row"
      role="switch"
      aria-label={label}
      aria-checked={checked}
      onClick={handleClick}
    >
      <span>{label}</span>
      <span
        className="st-switch"
        data-bounce={bounce !== 'idle' ? bounce : undefined}
        aria-hidden="true"
      >
        <span className="st-switch-thumb" />
      </span>
    </button>
  )
}

export function Segments<T extends string | number>({
  label,
  items,
  value,
  onChange,
}: {
  label: string
  items: readonly T[]
  value: T
  onChange: (next: T) => void
}) {
  return (
    <SegmentedControl
      className="st-segments"
      label={label}
      value={value}
      onChange={onChange}
      options={items.map((item) => ({ value: item, label: String(item) }))}
    />
  )
}

export function useSettingsWriter() {
  const [error, setError] = useState('')
  async function run(work: () => Promise<unknown>) {
    setError('')
    try {
      await work()
      return true
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save this change.')
      return false
    }
  }
  return {
    error,
    setError,
    run,
    save: (patch: Partial<Preferences>) => run(() => updatePreferences(patch)),
  }
}
