import { useLiveQuery } from 'dexie-react-hooks'
import { prepareBackup, saveBackup } from './exporter'

export function useSaveBackup(enabled = true) {
  const result = useLiveQuery(async () => {
    if (!enabled) return null
    try {
      return { file: await prepareBackup(), error: null }
    } catch (err) {
      return { file: null, error: err instanceof Error ? err : new Error('Could not prepare backup') }
    }
  }, [enabled])
  return {
    ready: !!result?.file || !!result?.error,
    save: () => {
      if (result?.file) return saveBackup(result.file)
      return Promise.reject(result?.error ?? new Error('The backup is being prepared'))
    },
  }
}
