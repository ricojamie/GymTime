import { useState } from 'react'
import { Dumbbell, Layers3 } from 'lucide-react'
import { useSearchParams } from 'react-router-dom'
import { IronMascot } from '../components/mascot'
import ExerciseSelect from './ExerciseSelect'
import Routines from './Routines'
import './library.css'
import './routines.css'

export default function Library() {
  const [params, setParams] = useSearchParams()
  const routines = params.get('tab') === 'routines'
  const [prevTab, setPrevTab] = useState(routines)
  const [flyDir, setFlyDir] = useState<'none' | 'from-right' | 'from-left'>('none')

  if (routines !== prevTab) {
    setPrevTab(routines)
    setFlyDir(routines ? 'from-right' : 'from-left')
  }

  return (
    <main className="screen lib-page">
      <div className="lib-sticky-bar">
        <header className="lib-page-head">
          <h1>Library</h1>
          <IronMascot pose="badge" size={34} interactive className="lib-head-gator" />
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
      </div>
      <div key={routines ? 'routines' : 'exercises'} className="lib-tab-panel" data-fly={flyDir}>
        {routines ? <Routines embedded /> : <ExerciseSelect embedded />}
      </div>
    </main>
  )
}

