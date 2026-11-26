import { BookOpen, Dumbbell, Layers3 } from 'lucide-react'
import { useSearchParams } from 'react-router-dom'
import ExerciseSelect from './ExerciseSelect'
import Routines from './Routines'
import './library.css'

export default function Library() {
  const [params, setParams] = useSearchParams()
  const routines = params.get('tab') === 'routines'
  return <main className="screen lib-page">
    <header className="lib-page-head"><h1>Library</h1><BookOpen aria-hidden="true" size={24}/></header>
    <div className="lib-segment" role="group" aria-label="Library collection">
      <button aria-pressed={!routines} onClick={() => setParams({})}><Dumbbell size={17}/>Exercises</button>
      <button aria-pressed={routines} onClick={() => setParams({ tab: 'routines' })}><Layers3 size={17}/>Routines</button>
    </div>
    {routines ? <Routines embedded/> : <ExerciseSelect embedded/>}
  </main>
}
