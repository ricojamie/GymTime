import { useRef, useState } from 'react'
import { Download, Upload } from 'lucide-react'
import { IronMascot } from '../components/mascot'
import { BottomSheet } from '../components/primitives'
import { readBackup, restoreBackup, useSaveBackup } from '../db/backup'
import type { Preferences } from '../db/types'
import { Group, Segments } from './SettingsUi'

type Backup = Awaited<ReturnType<typeof readBackup>>
type Report = Awaited<ReturnType<typeof restoreBackup>>

const labels: Record<keyof Backup['counts'], string> = {
  muscleGroups: 'Body parts',
  exercises: 'Exercises',
  routines: 'Routines',
  routineDays: 'Routine days',
  routineExercises: 'Planned exercises',
  workouts: 'Workouts',
  sets: 'Sets',
  workoutExerciseInstances: 'Workout exercises',
}

function Counts({ counts }: { counts: Backup['counts'] }) {
  return (
    <dl className="st-data-counts">
      {(Object.keys(labels) as Array<keyof Backup['counts']>).map((key) => (
        <div key={key}>
          <dt>{labels[key]}</dt>
          <dd>{counts[key].toLocaleString()}</dd>
        </div>
      ))}
    </dl>
  )
}

export default function BackupSettings({ prefs }: { prefs: Preferences }) {
  const { ready, save } = useSaveBackup()
  const file = useRef<HTMLInputElement>(null)
  const running = useRef(false)
  const [pending, setPending] = useState<{ backup: Backup; fileName: string } | null>(null)
  const [report, setReport] = useState<Report | null>(null)
  const [mode, setMode] = useState<'Merge' | 'Replace'>('Merge')
  const [status, setStatus] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function execute(action: () => Promise<void>) {
    if (running.current) return
    running.current = true
    setBusy(true)
    setError('')
    setStatus('')
    try {
      await action()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Backup operation failed.')
    } finally {
      running.current = false
      setBusy(false)
    }
  }

  return (
    <Group title="Backup & restore">
      <div className="st-row">
        <span>Last backup</span>
        <span className="st-meta">
          {prefs.lastBackupAtEpochMs
            ? new Date(prefs.lastBackupAtEpochMs).toLocaleString(undefined, {
                dateStyle: 'medium',
                timeStyle: 'short',
              })
            : 'Never'}
        </span>
      </div>
      <button
        type="button"
        className="st-row st-action"
        disabled={busy || !ready}
        onClick={() =>
          void execute(async () => {
            const result = await save()
            if (result !== 'cancelled') {
              setStatus(result === 'shared' ? 'Backup shared.' : 'Backup downloaded.')
            }
          })
        }
      >
        <span>Export IronLog backup</span>
        <Download size={18} />
      </button>
      <button
        type="button"
        className="st-row st-action"
        disabled={busy}
        onClick={() => file.current?.click()}
      >
        <span>Import IronLog backup</span>
        <Upload size={18} />
      </button>
      <input
        type="file"
        hidden
        ref={file}
        accept=".zip,application/zip,application/x-zip-compressed"
        onChange={(e) => {
          const selected = e.target.files?.[0]
          e.target.value = ''
          if (selected) {
            void execute(async () => {
              const backup = await readBackup(selected)
              setReport(null)
              setMode('Merge')
              setPending({ backup, fileName: selected.name })
            })
          }
        }}
      />
      {busy && (
        <p className="st-note" role="status">
          Working…
        </p>
      )}
      {status && (
        <p className="st-note st-status-ok" role="status">
          {status}
        </p>
      )}
      {error && !pending && (
        <p className="st-alert" role="alert">
          {error}
        </p>
      )}
      {pending && (
        <BottomSheet
          title="Restore backup"
          onClose={() => {
            if (!busy) setPending(null)
          }}
        >
          <div className="st-sheet">
            <strong>{pending.fileName}</strong>
            <p className="st-meta">
              Format v{pending.backup.version} · {pending.backup.source}
            </p>
            <Counts counts={pending.backup.counts} />
            {pending.backup.warnings.map((w, i) => (
              <p className="st-note" key={i}>
                {w}
              </p>
            ))}
            {pending.backup.canReplace && (
              <Segments
                label="Restore mode"
                items={['Merge', 'Replace'] as const}
                value={mode}
                onChange={setMode}
              />
            )}
            <p>
              {mode === 'Replace'
                ? 'Replaces all local training data with this backup.'
                : 'Adds missing records and keeps existing training data.'}
            </p>
            {error && (
              <p role="alert" className="st-alert">
                {error}
              </p>
            )}
            <button
              type="button"
              disabled={busy}
              className={mode === 'Replace' ? 'btn st-danger-fill' : 'btn primary'}
              onClick={() =>
                void execute(async () => {
                  const result = await restoreBackup(
                    pending.backup,
                    mode === 'Replace' ? 'replace' : 'merge',
                  )
                  setPending(null)
                  setReport(result)
                })
              }
            >
              {busy
                ? 'Restoring…'
                : mode === 'Replace'
                  ? 'Replace local data'
                  : 'Merge backup'}
            </button>
          </div>
        </BottomSheet>
      )}
      {report && (
        <BottomSheet title="Restore complete" onClose={() => setReport(null)}>
          <div className="st-sheet">
            <div className="st-sheet-gator">
              <IronMascot pose="badge" size={44} label="" />
              <span className="eyebrow">Training log restored</span>
            </div>
            <p>
              {report.inserted.toLocaleString()} inserted ·{' '}
              {report.updated.toLocaleString()} updated ·{' '}
              {report.skipped.toLocaleString()} skipped
            </p>
            <Counts counts={report.counts} />
            {report.warnings.map((w, i) => (
              <p className="st-note" key={i}>
                {w}
              </p>
            ))}
            <button type="button" className="btn primary" onClick={() => setReport(null)}>
              Done
            </button>
          </div>
        </BottomSheet>
      )}
    </Group>
  )
}
