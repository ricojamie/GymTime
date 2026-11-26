import { useEffect, useRef, useState, type CSSProperties } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useLiveQuery } from 'dexie-react-hooks'
import { ArrowDown, ArrowLeft, ArrowUp, Check, ChevronDown, Layers3, Link2, Plus, Search, Trash2, Unlink2 } from 'lucide-react'
import { db } from '../db/database'
import type { Exercise } from '../db/types'
import { BottomSheet } from '../components/primitives'
import { newPlanRow, planBlocks, saveDay, type DayExerciseDraft } from '../routines/mutations'
import { useAdjacentReorder } from '../components/useAdjacentReorder'
import './routines.css'

type Draft = DayExerciseDraft & { key: string }
type NumberField = 'targetSets' | 'targetRepsMin' | 'targetRepsMax' | 'targetRestSeconds'

function parsePositiveId(value: string | undefined): number | null {
  if (value === undefined) return null
  const n = Number(value)
  return Number.isSafeInteger(n) && n > 0 ? n : null
}

function Editor({
  routineId,
  dayId,
  initialName,
  initialRows,
  exercises,
}: {
  routineId: number
  dayId: number | null
  initialName: string
  initialRows: DayExerciseDraft[]
  exercises: Exercise[]
}) {
  const navigate = useNavigate()
  const [name, setName] = useState(initialName)
  const [rows, setRows] = useState<Draft[]>(() => initialRows.map(row => ({ ...row, key: crypto.randomUUID() })))
  const [picker, setPicker] = useState(false)
  const [selection, setSelection] = useState<number[]>([])
  const [query, setQuery] = useState('')
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const [supersetPop, setSupersetPop] = useState<Record<string, 'link' | 'unlink'>>({})
  const popTimer = useRef<number | null>(null)
  const names = new Map(exercises.map(e => [e.id, e]))
  const blocks = planBlocks(rows)
  const captureReorder = useAdjacentReorder()

  useEffect(() => {
    return () => {
      if (popTimer.current !== null) window.clearTimeout(popTimer.current)
    }
  }, [])

  function triggerSupersetPop(keys: string[], kind: 'link' | 'unlink') {
    if (popTimer.current !== null) window.clearTimeout(popTimer.current)
    const next: Record<string, 'link' | 'unlink'> = {}
    for (const key of keys) next[key] = kind
    setSupersetPop(next)
    popTimer.current = window.setTimeout(() => {
      setSupersetPop({})
      popTimer.current = null
    }, 480)
  }

  function patch(key: string, changes: Partial<DayExerciseDraft>) {
    setRows(previous => previous.map(row => (row.key === key ? { ...row, ...changes } : row)))
  }

  function unlink(group: string) {
    const affected = rows.filter(row => row.supersetGroupId === group).map(row => row.key)
    setRows(previous =>
      previous.map(row =>
        row.supersetGroupId === group ? { ...row, supersetGroupId: null, supersetOrderIndex: 0 } : row,
      ),
    )
    if (affected.length) triggerSupersetPop(affected, 'unlink')
  }

  function remove(key: string) {
    setRows(previous => {
      const group = previous.find(r => r.key === key)?.supersetGroupId
      const remaining = previous.filter(r => r.key !== key)
      if (!group) return remaining
      const members = remaining.filter(r => r.supersetGroupId === group)
      return remaining.map(row =>
        row.supersetGroupId === group
          ? { ...row, supersetGroupId: members.length > 1 ? group : null, supersetOrderIndex: members.indexOf(row) }
          : row,
      )
    })
  }

  function pair(row: Draft, next: Draft) {
    if (row.supersetGroupId || next.supersetGroupId) return
    const group = crypto.randomUUID()
    setRows(previous =>
      previous.map(r =>
        r.key === row.key
          ? { ...r, supersetGroupId: group, supersetOrderIndex: 0 }
          : r.key === next.key
            ? { ...r, supersetGroupId: group, supersetOrderIndex: 1 }
            : r,
      ),
    )
    triggerSupersetPop([row.key], 'link')
  }

  function move(block: number, direction: -1 | 1, button: HTMLButtonElement) {
    const reordered = [...blocks]
    const target = block + direction
    if (target < 0 || target >= blocks.length) return
    captureReorder(button, direction)
    ;[reordered[block], reordered[target]] = [reordered[target], reordered[block]]
    setRows(reordered.flat())
  }

  function numberField(row: Draft, field: NumberField, label: string, placeholder?: string) {
    return (
      <label>
        {label}
        <input
          type="number"
          inputMode="numeric"
          min={field === 'targetRestSeconds' ? 0 : 1}
          step="1"
          placeholder={placeholder ?? '—'}
          value={row[field] ?? ''}
          onChange={event => {
            const raw = event.target.value
            patch(row.key, { [field]: raw === '' ? (field === 'targetSets' ? 0 : null) : Number(raw) })
          }}
        />
      </label>
    )
  }

  const base = '/routines/' + routineId

  return (
    <main className="screen rt">
      <header className="rt-sticky-head">
        <Link className="rt-back" to={base}>
          <ArrowLeft size={18} aria-hidden="true" /> Workout days
        </Link>
        <h1>{dayId === null ? 'New workout day' : 'Edit workout day'}</h1>
      </header>
      <form
        className="rt-form"
        onSubmit={async event => {
          event.preventDefault()
          if (saving) return
          setError('')
          setSaving(true)
          try {
            await saveDay(routineId, dayId, name, rows)
            navigate(base, { replace: true })
          } catch (failure) {
            setError((failure as Error).message)
            setSaving(false)
          }
        }}
      >
        <label>
          Day name
          <input value={name} required onChange={e => setName(e.target.value)} placeholder="e.g. Upper A" />
        </label>
        <div className="rt-head">
          <h2>Exercises</h2>
          <span className="rt-muted">{rows.length}</span>
        </div>
        {blocks.map((block, blockIndex) => (
          <section
            key={block[0].key}
            className="surface rt-plan stagger-in"
            style={{ '--stagger-i': Math.min(blockIndex, 8) } as CSSProperties}
            data-superset={block.length > 1 ? 'true' : undefined}
            data-superset-pop={supersetPop[block[0].key]}
            data-order-item
          >
            <header className="rt-head">
              <span className="eyebrow">
                {block.length > 1 ? 'Superset' : 'Exercise'} {String(blockIndex + 1).padStart(2, '0')}
              </span>
              <div className="rt-tools">
                <button
                  type="button"
                  className="icon-button rt-move-btn rt-move-up"
                  disabled={blockIndex === 0}
                  aria-label={'Move ' + (names.get(block[0].exerciseId)?.name ?? 'exercise') + ' up'}
                  onClick={e => move(blockIndex, -1, e.currentTarget)}
                >
                  <ArrowUp size={16} />
                </button>
                <button
                  type="button"
                  className="icon-button rt-move-btn rt-move-down"
                  disabled={blockIndex === blocks.length - 1}
                  aria-label={'Move ' + (names.get(block[0].exerciseId)?.name ?? 'exercise') + ' down'}
                  onClick={e => move(blockIndex, 1, e.currentTarget)}
                >
                  <ArrowDown size={16} />
                </button>
              </div>
            </header>
            {block.map(row => (
              <div key={row.key} className="rt-plan-row">
                <div className="rt-head">
                  <h3>{names.get(row.exerciseId)?.name ?? 'Deleted exercise'}</h3>
                  <button
                    type="button"
                    className="icon-button rt-remove-btn"
                    aria-label={'Remove ' + (names.get(row.exerciseId)?.name ?? 'exercise')}
                    onClick={() => remove(row.key)}
                  >
                    <Trash2 size={16} />
                  </button>
                </div>
                <div className="rt-number-grid">
                  {numberField(row, 'targetSets', 'Sets')}
                  {numberField(row, 'targetRepsMin', 'Min reps')}
                  {numberField(row, 'targetRepsMax', 'Max reps')}
                </div>
                <details className="rt-options">
                  <summary>
                    Rest & notes
                    <ChevronDown size={16} aria-hidden="true" />
                  </summary>
                  {numberField(
                    row,
                    'targetRestSeconds',
                    'Rest (seconds)',
                    String(names.get(row.exerciseId)?.defaultRestSeconds ?? 90),
                  )}
                  <label>
                    Plan notes
                    <textarea value={row.notes ?? ''} onChange={e => patch(row.key, { notes: e.target.value || null })} />
                  </label>
                </details>
              </div>
            ))}
            {block[0].supersetGroupId ? (
              <button className="rt-pair" type="button" onClick={() => unlink(block[0].supersetGroupId!)}>
                <Unlink2 size={16} aria-hidden="true" />
                Unlink
              </button>
            ) : (
              blocks[blockIndex + 1]?.length === 1 &&
              !blocks[blockIndex + 1][0].supersetGroupId && (
                <button className="rt-pair" type="button" onClick={() => pair(block[0], blocks[blockIndex + 1][0])}>
                  <Link2 size={16} aria-hidden="true" /> Pair with next
                </button>
              )
            )}
          </section>
        ))}
        <button
          type="button"
          className="btn rt-outline rt-new-btn"
          onClick={() => {
            setSelection([])
            setQuery('')
            setPicker(true)
          }}
        >
          <Plus size={17} aria-hidden="true" /> Add exercises
        </button>
        {error && (
          <p role="alert" className="rt-error">
            {error}
          </p>
        )}
        <button className="btn primary rt-cta-start" disabled={saving || !name.trim() || !rows.length}>
          <Check size={17} aria-hidden="true" />
          {saving ? 'Saving…' : 'Save day'}
        </button>
      </form>
      {picker && (
        <BottomSheet title="Add exercises" onClose={() => setPicker(false)}>
          <div className="rt rt-picker">
            <label className="rt-search">
              <Search size={18} aria-hidden="true" />
              <input
                value={query}
                onChange={e => setQuery(e.target.value)}
                aria-label="Search exercises"
                placeholder="Search exercises"
              />
            </label>
            <div className="rt-picker-list">
              {exercises
                .filter(ex =>
                  query
                    .trim()
                    .toLowerCase()
                    .split(/\s+/)
                    .every(word => (ex.name + ' ' + ex.targetMuscle).toLowerCase().includes(word)),
                )
                .map(ex => {
                  const existing = rows.some(row => row.exerciseId === ex.id)
                  const selected = selection.includes(ex.id)
                  return (
                    <button
                      key={ex.id}
                      type="button"
                      aria-pressed={existing || selected}
                      className="rt-choice"
                      disabled={existing}
                      onClick={() =>
                        setSelection(selected ? selection.filter(id => id !== ex.id) : [...selection, ex.id])
                      }
                    >
                      <span>
                        <strong>{ex.name}</strong>
                        <small>{ex.targetMuscle}</small>
                      </span>
                      <span className="rt-check">{(existing || selected) && <Check size={16} aria-hidden="true" />}</span>
                    </button>
                  )
                })}
            </div>
            <button
              type="button"
              className="btn primary"
              disabled={!selection.length}
              onClick={() => {
                setRows(previous => [
                  ...previous,
                  ...selection.map(id => ({ ...newPlanRow(id), key: crypto.randomUUID() })),
                ])
                setPicker(false)
              }}
            >
              Add {selection.length || ''} {selection.length === 1 ? 'exercise' : 'exercises'}
            </button>
          </div>
        </BottomSheet>
      )}
    </main>
  )
}

export default function RoutineDay() {
  const params = useParams()
  const validRoutineId = parsePositiveId(params.id)
  const hasDayParam = params.dayId !== undefined
  const validDayId = hasDayParam ? parsePositiveId(params.dayId) : null
  const invalidParams = validRoutineId === null || (hasDayParam && validDayId === null)

  const data = useLiveQuery(async () => {
    if (invalidParams || validRoutineId === null) return null
    const [routine, day, rows, exercises] = await Promise.all([
      db.routines.get(validRoutineId),
      validDayId !== null ? db.routine_days.get(validDayId) : null,
      validDayId !== null ? db.routine_exercises.where('routineDayId').equals(validDayId).sortBy('orderIndex') : [],
      db.exercises.orderBy('name').toArray(),
    ])
    return { routine, day, rows, exercises }
  }, [invalidParams, validRoutineId, validDayId])

  if (invalidParams) {
    return (
      <main className="screen rt">
        <section className="surface rt-empty">
          <Layers3 size={30} aria-hidden="true" />
          <h1>Day unavailable</h1>
          <p className="rt-muted">This workout day link is invalid or no longer exists.</p>
          <Link className="btn rt-outline" to="/routines">
            View routines
          </Link>
        </section>
      </main>
    )
  }

  if (!data) return <main className="screen rt" role="status">Loading day…</main>

  if (!data.routine || (validDayId !== null && data.day?.routineId !== validRoutineId)) {
    return (
      <main className="screen rt">
        <section className="surface rt-empty">
          <Layers3 size={30} aria-hidden="true" />
          <h1>Day unavailable</h1>
          <p className="rt-muted">This workout day may have been deleted.</p>
          <Link className="btn rt-outline" to="/routines">
            View routines
          </Link>
        </section>
      </main>
    )
  }

  return (
    <Editor
      key={validRoutineId + '/' + validDayId}
      routineId={validRoutineId}
      dayId={validDayId}
      initialName={data.day?.name ?? ''}
      initialRows={data.rows}
      exercises={data.exercises}
    />
  )
}

