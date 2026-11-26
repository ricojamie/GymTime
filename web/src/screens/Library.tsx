import { BookOpen, Dumbbell, Layers3 } from 'lucide-react'
import { useSearchParams } from 'react-router-dom'
import { SwoleGator, useIsGatorMode } from '../components/mascot'
import ExerciseSelect from './ExerciseSelect'
import Routines from './Routines'
import './library.css'

export default function Library() {
  const [params, setParams] = useSearchParams()
  const gatorMode = useIsGatorMode()
  const routines = params.get('tab') === 'routines'

  return (
    <main className="screen lib-page">
      <header className="lib-page-head">
        <h1>Library</h1>
        {gatorMode ? (
          <SwoleGator pose="badge" size={34} interactive className="lib-head-gator" />
        ) : (
          <BookOpen aria-hidden="true" size={22} />
        )}
      </header>
      <div
        className="lib-segment"
        role="group"
        aria-label="Library collection"
        data-tab={routines ? 'routines' : 'exercises'}
      >
        <button type="button" aria-pressed={!routines} onClick={() => setParams({})}>
          <Dumbbell size={17} aria-hidden="true" />
          Exercises
        </button>
        <button type="button" aria-pressed={routines} onClick={() => setParams({ tab: 'routines' })}>
          <Layers3 size={17} aria-hidden="true" />
          Routines
        </button>
      </div>
      {routines ? <Routines embedded /> : <ExerciseSelect embedded />}
    </main>
  )
}

