import { useId, useState, type ReactNode } from 'react'
import { ArrowLeft, ChevronRight } from 'lucide-react'
import { Link } from 'react-router-dom'
import { updatePreferences } from '../db/repositories'
import type { Preferences } from '../db/types'
import '../screens/settings.css'

export function SettingsHeader({title,back='/settings'}:{title:string; back?:string}) {
  return <header className="st-header"><Link className="icon-button" aria-label={back==='/'?'Back to Home':'Back to settings'} to={back}><ArrowLeft size={21}/></Link><h1>{title}</h1></header>
}
export function Group({title,children}:{title:string; children:ReactNode}) {
  const id = useId()
  return <section className="st-group" aria-labelledby={id}><h2 className="eyebrow" id={id}>{title}</h2><div className="st-surface">{children}</div></section>
}
export function SettingLink({to,children,meta}:{to:string; children:ReactNode; meta?:string}) {
  return <Link className="st-row st-action" to={to}><span>{children}</span><span className="st-trailing">{meta}<ChevronRight size={16}/></span></Link>
}
export function ToggleRow({label,checked,onChange}:{label:string;checked:boolean;onChange:()=>void}) {
  return <button className="st-row st-action" role="switch" aria-label={label} aria-checked={checked} onClick={onChange}><span>{label}</span><span className="st-switch" aria-hidden="true"/></button>
}
export function Segments<T extends string|number>({label,items,value,onChange}:{label:string; items:readonly T[]; value:T; onChange:(next:T)=>void}) {
  return <div className="st-segments" role="group" aria-label={label}>{items.map(item=><button type="button" key={item} aria-pressed={value===item} onClick={()=>onChange(item)}>{item}</button>)}</div>
}
export function useSettingsWriter() {
  const [error,setError] = useState('')
  async function run(work:()=>Promise<unknown>) {
    setError('')
    try { await work(); return true }
    catch (e) { setError(e instanceof Error ? e.message : 'Could not save this change.'); return false }
  }
  return {error,setError,run,save:(patch:Partial<Preferences>)=>run(()=>updatePreferences(patch))}
}
