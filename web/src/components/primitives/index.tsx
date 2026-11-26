import { useEffect, useId, useLayoutEffect, useRef, useState, type CSSProperties, type HTMLAttributes, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { X } from 'lucide-react'
import './primitives.css'

type SheetProps = { title: string; onClose: () => void; children: ReactNode; footer?: ReactNode; className?: string }
let openSheets = 0
let previousOverflow = ''

function isOutside(dialog: HTMLDialogElement, x: number, y: number) {
  const bounds = dialog.getBoundingClientRect()
  return x < bounds.left || x > bounds.right || y < bounds.top || y > bounds.bottom
}

export function BottomSheet({ title, onClose, children, footer, className = '' }: SheetProps) {
  const ref = useRef<HTMLDialogElement>(null)
  const close = useRef(onClose)
  const pressedBackdrop = useRef(false)
  const label = useId()
  useLayoutEffect(() => { close.current = onClose }, [onClose])
  useLayoutEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    const trigger = document.activeElement instanceof HTMLElement ? document.activeElement : null
    if (openSheets++ === 0) {
      previousOverflow = document.body.style.overflow
      document.body.style.overflow = 'hidden'
    }
    if (!dialog.open) dialog.showModal()
    return () => {
      dialog.close()
      if (--openSheets === 0) document.body.style.overflow = previousOverflow
      if (trigger?.isConnected) trigger.focus({ preventScroll: true })
    }
  }, [])
  return createPortal(<dialog ref={ref} className={'bottom-sheet ' + className} aria-labelledby={label}
    onCancel={event => { event.preventDefault(); event.stopPropagation(); close.current() }}
    onPointerDown={event => {
      pressedBackdrop.current = event.target === event.currentTarget && isOutside(event.currentTarget, event.clientX, event.clientY)
    }}
    onPointerCancel={() => { pressedBackdrop.current = false }}
    onClick={event => {
      const dismiss = pressedBackdrop.current && event.target === event.currentTarget && isOutside(event.currentTarget, event.clientX, event.clientY)
      pressedBackdrop.current = false
      if (dismiss) close.current()
    }}>
    <div className="sheet-grip" aria-hidden="true" />
    <header className="sheet-heading"><h2 id={label}>{title}</h2><button type="button" className="icon-button" aria-label="Close" onClick={onClose}><X size={20}/></button></header>
    <div className="sheet-scroller">{children}</div>
    {footer && <footer className="sheet-footer">{footer}</footer>}
  </dialog>, document.body)
}

export const Dialog = BottomSheet
export function Surface({ className = '', ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={'surface ' + className} {...props}/>
}
export function HairlineList({ className = '', ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={'hairline-list ' + className} {...props}/>
}
export function Toggle({checked,onChange,label,disabled=false}:{checked:boolean;onChange:(v:boolean)=>void;label:string;disabled?:boolean}) {
  return <button type="button" role="switch" aria-checked={checked} disabled={disabled} className="toggle-row" onClick={()=>onChange(!checked)}><span>{label}</span><span className="switch-track" aria-hidden="true"><i/></span></button>
}
export function SegmentedControl<T extends string | number>({ value, options, onChange, label, className = '' }: {
  value: T
  options: readonly { value: T; label: string }[]
  onChange: (value: T) => void
  label?: string
  className?: string
}) {
  const selected = options.findIndex(option => option.value === value)
  const style = { '--segment-count': options.length || 1, '--segment-index': selected } as CSSProperties
  return <div role="group" aria-label={label} className={'segmented ' + className} style={style}>
    {selected >= 0 && <span className="segment-rail" aria-hidden="true"><i /></span>}
    {options.map(item => <button type="button" key={item.value} aria-pressed={value === item.value}
      onClick={() => onChange(item.value)}>{item.label}</button>)}
  </div>
}
export function CalibrationBar({value,max=1,ticks=10,label}:{value:number;max?:number;ticks?:number;label?:string}) {
  return <div className="calibration-bar" role="meter" aria-label={label} aria-valuemin={0} aria-valuemax={max} aria-valuenow={Math.min(max,Math.max(0,value))}>{Array.from({length:ticks},(_,i)=><i key={i} data-lit={max>0 && i<Math.round(value/max*ticks)}/>)}</div>
}

function prefersReducedMotion() {
  if (typeof document !== 'undefined' && document.documentElement.dataset.motion === 'reduced') return true
  if (typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return true
  return false
}

export function AnimatedNumber({
  value,
  format = (n: number) => String(Math.round(n)),
  duration = 520,
  className = '',
}: {
  value: number
  format?: (value: number) => string
  duration?: number
  className?: string
}) {
  const [display, setDisplay] = useState(value)
  const currentRef = useRef(value)
  useEffect(() => {
    const from = currentRef.current
    if (!Number.isFinite(value) || from === value || duration <= 0 || prefersReducedMotion()) {
      currentRef.current = value
      setDisplay(value)
      return
    }
    let raf = 0
    const start = performance.now()
    const tick = (now: number) => {
      const progress = Math.min(1, (now - start) / duration)
      const eased = 1 - Math.pow(1 - progress, 3)
      const next = from + (value - from) * eased
      currentRef.current = next
      setDisplay(next)
      if (progress < 1) raf = requestAnimationFrame(tick)
      else {
        currentRef.current = value
        setDisplay(value)
      }
    }
    raf = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(raf)
  }, [value, duration])
  return <span className={'animated-number ' + className}>{format(display)}</span>
}
