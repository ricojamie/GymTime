import { useMemo, useState } from 'react'
import { useLiveQuery } from 'dexie-react-hooks'
import { ArrowRight, ChevronDown, Minus, Plus, RotateCcw } from 'lucide-react'
import { db, defaultPreferences } from '../../db/database'
import type { Preferences } from '../../db/types'
import { calculatePlates, calculateTotalWeight, plateColor } from '../../domain/plateCalculator'
import { formatWeight, fromLbs, toLbs, weightLabel } from '../../domain/units'
import { BottomSheet } from '../primitives'
import './tools.css'

type Props = { initialWeightLb: number; onUse: (weightLb: number) => void; onClose: () => void }

function Barbell({ plates, sides }: { plates: number[]; sides: number }) {
  const visible = plates.slice(0, 14)
  const thickness = Math.min(11, 84 / Math.max(visible.length, 1))
  const twoSides = sides === 2
  function sleeve(sign: number) {
    const origin = twoSides ? 160 + sign * 54 : 116
    return <g key={sign}>
      <line className="plate-bar-sleeve" x1={origin} x2={origin + sign * (twoSides ? 96 : 120)} y1={55} y2={55} />
      <line className="plate-bar-stop" x1={origin} x2={origin} y1={42} y2={68} />
      {visible.map((weight, index) => {
        const height = Math.min(78, Math.max(21, 17 + Math.sqrt(weight) * 8))
        return <rect key={`${sign}-${index}-${weight}`} className="plate-bar-disc"
          style={{ '--plate-dir': sign, '--plate-i': index } as React.CSSProperties}
          x={origin + sign * (6 + index * thickness) - (sign < 0 ? thickness : 0)} y={55 - height / 2}
          width={Math.max(2, thickness - 1.5)} height={height} rx={2} fill={plateColor(weight)} stroke="var(--bg-well)" strokeWidth={0.8} />
      })}
    </g>
  }
  return <svg className="plate-barbell" viewBox="0 0 320 105" role="img" aria-label={plates.length + ' plates on each of ' + sides + ' loading sides'}>
    <line className="plate-bar-axis" x1={8} x2={312} y1={55} y2={55} />
    <line className="plate-bar-shaft" x1={twoSides ? 95 : 24} x2={twoSides ? 225 : 119} y1={55} y2={55} />
    {[0,1,2,3,4,5,6,7].map(i => <line key={i} className="plate-bar-knurl" x1={(twoSides ? 134 : 50) + i * 4} x2={(twoSides ? 137 : 53) + i * 4} y1="58" y2="52" />)}
    {twoSides && sleeve(-1)}{sleeve(1)}
  </svg>
}

export function PlateCalculatorSheet({ initialWeightLb, onUse, onClose }: Props) {
  const prefs = useLiveQuery(() => db.preferences.get('default'), [])
  if (!prefs) return <BottomSheet title="Plate calculator" onClose={onClose}><p aria-live="polite">Loading equipment…</p></BottomSheet>
  return <PlateControls {...{ initialWeightLb, onUse, onClose, prefs }} />
}

function PlateControls({ initialWeightLb, onUse, onClose, prefs }: Props & {prefs: Preferences}) {
  const { preferredWeightUnit: unit, barWeight: bar, loadingSides: sides, usePlateInventory, plateInventory } = prefs
  const label = weightLabel(unit)
  // The solver uses hundredths of a pound, including metric plate equivalents.
  const choices = useMemo(() => [...new Set(prefs.availablePlates
    .filter(p => Number.isFinite(p) && p >= 0.01 && p <= 1000)
    .map(p => Math.round(p * 100) / 100))].sort((a, b) => b - a), [prefs.availablePlates])
  const canonicalInventory = useMemo(() => {
    const counts: Record<string, number> = {}
    for (const [raw, count] of Object.entries(plateInventory)) {
      const weight = Math.round(Number(raw) * 100) / 100
      counts[weight] = Math.min(counts[weight] ?? count, count)
    }
    return counts
  }, [plateInventory])
  const [mode, setMode] = useState<'find' | 'build'>('find')
  const [target, setTarget] = useState(() => formatWeight(Math.max(bar, initialWeightLb || 0), unit))
  const [manualPlates, setManualPlates] = useState<number[]>([])
  const [error, setError] = useState('')
  const targetLb = toLbs(Number(target), unit)
  const valid = target.trim() !== '' && Number.isFinite(targetLb) && targetLb >= 0 && targetLb <= 10000
  const solution = useMemo(() => calculatePlates(valid ? targetLb : bar, choices, bar, sides, usePlateInventory ? canonicalInventory : null),
    [valid, targetLb, choices, bar, sides, usePlateInventory, canonicalInventory])
  const maxPerSide = (plate: number) => usePlateInventory ? Math.floor((canonicalInventory[plate] ?? 0) / sides) : Infinity
  const manual = choices.flatMap(p => Array<number>(Math.min(maxPerSide(p), manualPlates.filter(existing => existing === p).length)).fill(p))
  const plates = mode === 'find' ? solution.platesPerSide : manual
  const loaded = mode === 'find' ? solution.totalWeight : calculateTotalWeight(plates, bar, sides)
  const available = choices.filter(p => maxPerSide(p) > 0)
  const increment = available.length ? Math.min(...available) * sides : 0
  const summary = choices.map(p => ({plate:p, count:plates.filter(value => value === p).length})).filter(row => row.count)
  const usable = mode === 'build' || valid
  function step(sign: number) {
    const value = Math.max(0, (valid ? targetLb : bar) + sign * increment)
    setTarget(String(Number(fromLbs(Math.min(10000, value), unit).toFixed(4))))
  }
  function updateInventory(mutator: (p: Preferences) => void) {
    void db.transaction('rw', db.preferences, async () => {
      const value = await db.preferences.get('default') ?? {...defaultPreferences}
      mutator(value)
      await db.preferences.put(value)
    }).catch(() => setError('Could not save plate inventory. Try again.'))
  }
  function addPlate(weight:number) {
    if (manual.filter(p => p === weight).length < maxPerSide(weight)) setManualPlates([...manual, weight])
  }
  function removePlate(weight:number) {
    const index = manual.indexOf(weight)
    setManualPlates(manual.filter((_, i) => index !== i))
  }
  return <BottomSheet title="Plate calculator" onClose={onClose} footer={
    <button className="btn primary tool-action" disabled={!usable} onClick={() => onUse(loaded)}>
      Use {formatWeight(loaded, unit)} {label} <ArrowRight size={18} />
    </button>
  }>
    <div className="logger-tool">
      <div className="tool-tabs" role="group" aria-label="Calculator mode">
        <button aria-pressed={mode === 'find'} onClick={() => setMode('find')}>Find plates</button>
        <button aria-pressed={mode === 'build'} onClick={() => { if (mode === 'find') setManualPlates(solution.platesPerSide); setMode('build') }}>Build weight</button>
      </div>
      {mode === 'find' ? <div className="plate-target">
        <button className="icon-button" aria-label="Decrease target weight" disabled={!increment} onClick={() => step(-1)}><Minus size={20} /></button>
        <label><span className="eyebrow">Target weight</span><span className="plate-value"><input aria-label="Target weight" type="text" inputMode="decimal" maxLength={9} value={target} onChange={event => setTarget(event.target.value.replace(',', '.'))}/><small>{label}</small></span></label>
        <button className="icon-button" aria-label="Increase target weight" disabled={!increment} onClick={() => step(1)}><Plus size={20} /></button>
      </div> : <div className="plate-target plate-target-manual">
        <div><span className="eyebrow">Loaded weight</span><div className="plate-value">{formatWeight(loaded, unit)} <small>{label}</small></div></div>
        <button className="icon-button" aria-label="Unload bar" disabled={!plates.length} onClick={() => setManualPlates([])}><RotateCcw size={18} /></button>
      </div>}
      <figure className="plate-diagram">
        <Barbell plates={plates} sides={sides} />
        <figcaption><span>{formatWeight(bar, unit)} {label} bar · {sides} {sides === 1 ? 'side' : 'sides'}</span><span>{plates.length} / side</span></figcaption>
      </figure>
      {mode === 'find' && (!valid || !solution.isExact) && <p className="plate-status" role="status">{valid ? 'Closest load · ' + formatWeight(loaded, unit) + ' ' + label : 'Enter a target between 0 and ' + formatWeight(10000, unit) + ' ' + label + '.'}</p>}
      {mode === 'build' && <section><h3 className="eyebrow">Add per side</h3>
        {choices.length ? <div className="manual-plates">{choices.map(plate => <button key={plate}
          aria-label={'Add ' + formatWeight(plate, unit) + ' ' + label + ' per side'}
          className="manual-plate" style={{'--plate-color': plateColor(plate)} as React.CSSProperties}
          disabled={manual.filter(p => p === plate).length >= maxPerSide(plate)} onClick={() => addPlate(plate)}>
          {formatWeight(plate, unit)}
        </button>)}</div> : <p className="tool-empty">No plates selected in equipment settings.</p>}
      </section>}
      <section>
        <div className="tool-section-heading"><h3 className="eyebrow">Per side</h3><span>{formatWeight((loaded - bar) / sides, unit)} {label}</span></div>
        {summary.length ? summary.map(({plate, count}) => <div className="plate-breakdown" key={plate}>
          <span className="plate-key" style={{backgroundColor:plateColor(plate)}} /><strong>{formatWeight(plate, unit)} {label}</strong>
          <span>× {count}</span>
          {mode === 'build' && <button className="icon-button" aria-label={'Remove one ' + formatWeight(plate, unit) + ' plate per side'} onClick={() => removePlate(plate)}><Minus size={16} /></button>}
        </div>) : <p className="tool-empty">Empty bar</p>}
      </section>
      <details className="tool-disclosure"><summary>My plate inventory <ChevronDown size={16} /></summary>
        <label className="tool-check">Limit to my plates<input type="checkbox" checked={usePlateInventory} onChange={event => {const checked=event.target.checked; updateInventory(p => {p.usePlateInventory=checked})}} /></label>
        {choices.map(plate => <label className="plate-inventory-row" key={plate}><span>{formatWeight(plate,unit)} {label}</span>
          <input aria-label={formatWeight(plate,unit) + ' ' + label + ' physical plate count'} type="number" inputMode="numeric" min={0} max={100} step={1} value={canonicalInventory[plate] ?? 0}
            onChange={event => {
              const count = Math.min(100, Math.max(0, Math.trunc(Number(event.target.value) || 0)))
              updateInventory(p => {
                const counts = {...p.plateInventory}
                for (const key of Object.keys(counts)) {
                  if (Math.round(Number(key) * 100) / 100 === plate) delete counts[key]
                }
                const original = p.availablePlates.find(value => Math.round(value * 100) / 100 === plate) ?? plate
                counts[String(original)] = count
                p.plateInventory = counts
              })
            }} />
        </label>)}
      </details>
      {error && <p className="tool-error" role="alert">{error}</p>}
    </div>
  </BottomSheet>
}

export default PlateCalculatorSheet
