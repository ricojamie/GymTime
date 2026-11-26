import { useRef, useState } from 'react'
import { Check, ChevronDown, Minus, Plus } from 'lucide-react'
import { db } from '../db/database'
import type { Preferences } from '../db/types'
import { formatWeight, toLbs, weightLabel } from '../domain/units'
import { Group, Segments, ToggleRow, useSettingsWriter } from './SettingsUi'

const near = (a: number, b: number) => Math.abs(a - b) < 0.000001

function unique(numbers: number[]) {
  return numbers
    .filter((v, i, a) => a.findIndex((n) => near(n, v)) === i)
    .sort((a, b) => b - a)
}

function PlateCount({
  plate,
  prefs,
  save,
  adjust,
}: {
  plate: number
  prefs: Preferences
  save: (count: number) => void
  adjust: (delta: number) => void
}) {
  const key = Object.keys(prefs.plateInventory).find((k) => near(Number(k), plate))
  const count = key ? prefs.plateInventory[key] : 0
  const label = `${formatWeight(plate, prefs.preferredWeightUnit)} ${weightLabel(prefs.preferredWeightUnit)}`

  return (
    <div className="st-row">
      <span>{label}</span>
      <div className="st-stepper">
        <button
          type="button"
          className="st-step-btn"
          aria-label={`Decrease ${label} plate count`}
          disabled={count <= 0}
          onClick={() => adjust(-1)}
        >
          <Minus size={15} />
        </button>
        <input
          type="number"
          aria-label={`${label} plate count`}
          min={0}
          max={100}
          step={1}
          className="st-number"
          inputMode="numeric"
          key={count}
          defaultValue={count}
          onBlur={(e) => {
            const next = Math.min(100, Math.max(0, Math.trunc(Number(e.target.value))))
            if (Number.isFinite(next)) {
              e.target.value = String(next)
              if (next !== count) save(next)
            } else {
              e.target.value = String(count)
            }
          }}
          onKeyDown={(e) => {
            if (e.key === 'Enter') e.currentTarget.blur()
          }}
        />
        <button
          type="button"
          className="st-step-btn"
          aria-label={`Increase ${label} plate count`}
          disabled={count >= 100}
          onClick={() => adjust(1)}
        >
          <Plus size={15} />
        </button>
      </div>
    </div>
  )
}

export default function EquipmentSettings({ prefs }: { prefs: Preferences }) {
  const [open, setOpen] = useState(false)
  const { error, run, save } = useSettingsWriter()
  const queueRef = useRef<Promise<unknown>>(Promise.resolve())

  const unit = prefs.preferredWeightUnit
  const suffix = weightLabel(unit)
  const values =
    unit === 'kg' ? [25, 20, 15, 10, 5, 2.5, 1.25] : [55, 45, 35, 25, 15, 10, 5, 2.5, 1.25]
  const plates = unique([
    ...prefs.availablePlates,
    ...values.map((weight) => Math.round(toLbs(weight, unit) * 100) / 100),
  ])
  const bars = unique([
    prefs.barWeight,
    ...(unit === 'kg' ? [20, 15, 10, 0] : [45, 35, 25, 0]).map((w) => toLbs(w, unit)),
  ])
  const activePlates = prefs.availablePlates

  function enqueue(work: () => Promise<unknown>) {
    const next = queueRef.current.catch(() => {}).then(() => run(work))
    queueRef.current = next
    return next
  }

  function toggle(plate: number) {
    return enqueue(() =>
      db.transaction('rw', db.preferences, async () => {
        const current = await db.preferences.get('default')
        if (!current) return
        const selected = current.availablePlates.some((weight) => near(weight, plate))
        const availablePlates = selected
          ? current.availablePlates.filter((weight) => !near(weight, plate))
          : [...current.availablePlates, plate]
        await db.preferences.update('default', { availablePlates: unique(availablePlates) })
      }),
    )
  }

  function setCount(plate: number, count: number) {
    return enqueue(() =>
      db.transaction('rw', db.preferences, async () => {
        const current = await db.preferences.get('default')
        if (!current) return
        const inventory = { ...current.plateInventory }
        const key = Object.keys(inventory).find((k) => near(Number(k), plate)) ?? String(plate)
        inventory[key] = Math.min(100, Math.max(0, count))
        await db.preferences.update('default', { plateInventory: inventory })
      }),
    )
  }

  function adjustCount(plate: number, delta: number) {
    return enqueue(() =>
      db.transaction('rw', db.preferences, async () => {
        const current = await db.preferences.get('default')
        if (!current) return
        const inventory = { ...current.plateInventory }
        const key = Object.keys(inventory).find((k) => near(Number(k), plate)) ?? String(plate)
        const existing = inventory[key] ?? 0
        inventory[key] = Math.min(100, Math.max(0, existing + delta))
        await db.preferences.update('default', { plateInventory: inventory })
      }),
    )
  }

  return (
    <Group title="Equipment">
      <button
        type="button"
        className="st-row st-action"
        aria-expanded={open}
        onClick={() => setOpen(!open)}
      >
        <span>Plate calculator setup</span>
        <span className="st-trailing">
          {formatWeight(prefs.barWeight, unit)} {suffix} bar
          <ChevronDown
            className={open ? 'st-chevron st-chevron--open' : 'st-chevron'}
            size={16}
          />
        </span>
      </button>
      {open && (
        <div className="st-expand">
          <label className="st-row">
            <span>Bar weight</span>
            <select
              value={prefs.barWeight}
              onChange={(e) => void save({ barWeight: Number(e.target.value) })}
            >
              {bars.map((weight) => (
                <option key={weight} value={weight}>
                  {formatWeight(weight, unit)} {suffix}
                </option>
              ))}
            </select>
          </label>
          <div className="st-row">
            <span>Loading sides</span>
            <Segments
              label="Loading sides"
              items={[1, 2] as const}
              value={prefs.loadingSides}
              onChange={(v) => void save({ loadingSides: v })}
            />
          </div>
          <div className="st-block">
            <h3>Available plates</h3>
            <div className="st-plate-choices">
              {plates.map((plate) => {
                const active = activePlates.some((w) => near(w, plate))
                return (
                  <button
                    type="button"
                    aria-pressed={active}
                    key={plate}
                    onClick={() => void toggle(plate)}
                  >
                    {active && <Check size={14} />}
                    {formatWeight(plate, unit)} {suffix}
                  </button>
                )
              })}
            </div>
          </div>
          <ToggleRow
            label="Limit to my plates"
            checked={prefs.usePlateInventory}
            onChange={() => void save({ usePlateInventory: !prefs.usePlateInventory })}
          />
          {prefs.usePlateInventory && activePlates.length > 0 && (
            <div className="st-counts">
              <h3>Physical plate counts</h3>
              {unique(activePlates).map((weight) => (
                <PlateCount
                  key={weight}
                  plate={weight}
                  prefs={prefs}
                  save={(count) => void setCount(weight, count)}
                  adjust={(delta) => void adjustCount(weight, delta)}
                />
              ))}
            </div>
          )}
        </div>
      )}
      {error && (
        <p className="st-alert" role="alert">
          {error}
        </p>
      )}
    </Group>
  )
}
