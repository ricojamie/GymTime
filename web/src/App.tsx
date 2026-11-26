import { Suspense, lazy, useEffect, useState } from 'react'
import { createBrowserRouter, Link, NavLink, Outlet, RouterProvider, useLocation } from 'react-router-dom'
import { Activity, ArrowRight, BookOpen, Clock3, House, X } from 'lucide-react'
import { useLiveQuery } from 'dexie-react-hooks'
import { useRegisterSW } from 'virtual:pwa-register/react'
import { getActiveWorkout } from './db/repositories'
import type { Workout } from './db/types'
import { useRestTimerLifecycle } from './store/restTimer'
import { useTheme } from './theme'
import Home from './screens/Home'
const Logger = lazy(() => import('./screens/Logger'))
const Builder = lazy(() => import('./screens/Builder'))
const WorkoutOverview = lazy(() => import('./screens/Workout'))
const Summary = lazy(() => import('./screens/Summary'))
const Library = lazy(() => import('./screens/Library'))
const ExerciseSelect = lazy(() => import('./screens/ExerciseSelect'))
const ExerciseForm = lazy(() => import('./screens/ExerciseForm'))
const Routines = lazy(() => import('./screens/Routines'))
const RoutineDetail = lazy(() => import('./screens/RoutineDetail'))
const RoutineDay = lazy(() => import('./screens/RoutineDay'))
const RoutineStart = lazy(() => import('./screens/RoutineStart'))
const History = lazy(() => import('./screens/History'))
const Insights = lazy(() => import('./screens/Insights'))
const MonthlyReport = lazy(() => import('./screens/MonthlyReport'))
const Settings = lazy(() => import('./screens/Settings'))
const ThemeSettings = lazy(() => import('./screens/ThemeSettings'))
const MuscleGroups = lazy(() => import('./screens/MuscleGroups'))
const tabs = [
  { path:'/', name:'Home', icon:House }, { path:'/history', name:'History', icon:Clock3 },
  { path:'/library', name:'Library', icon:BookOpen }, { path:'/insights', name:'Insights', icon:Activity },
]
function SessionDock({workout}:{workout:Workout}) {
  const [now,setNow] = useState(Date.now)
  useEffect(() => {
    let id: number | null = null
    const start = () => {
      if (id === null && document.visibilityState !== 'hidden') {
        id = window.setInterval(() => setNow(Date.now()), 1000)
      }
    }
    const stop = () => {
      if (id !== null) { window.clearInterval(id); id = null }
    }
    const onVisibility = () => {
      if (document.visibilityState === 'hidden') stop()
      else { setNow(Date.now()); start() }
    }
    start()
    document.addEventListener('visibilitychange', onVisibility)
    return () => { stop(); document.removeEventListener('visibilitychange', onVisibility) }
  },[])
  const elapsed = Math.max(0,Math.floor((now-workout.startTime)/1000))
  const clock = [Math.floor(elapsed/3600),Math.floor(elapsed%3600/60),elapsed%60].map(n=>String(n).padStart(2,'0')).join(':')
  return <Link className="session-dock hover-lift" to="/workout/active"><span className="session-led" aria-hidden="true"/><strong>{workout.name || workout.routineDayNameSnapshot || 'Active workout'}</strong><time>{clock}</time><span>Resume</span><ArrowRight size={16}/></Link>
}
function ConnectionUpdate() {
  const {needRefresh:[refresh,setRefresh],updateServiceWorker} = useRegisterSW()
  if (!refresh) return null
  return <aside className="app-update" aria-label="App update">Update ready<button type="button" onClick={()=>void updateServiceWorker(true)}>Restart</button><button className="icon-button" aria-label="Later" onClick={()=>setRefresh(false)}><X size={16}/></button></aside>
}
function Shell() {
  const {pathname} = useLocation()
  const active = useLiveQuery(async() => (await getActiveWorkout()) ?? null, [])
  useTheme()
  useRestTimerLifecycle(active === undefined ? undefined : Boolean(active))
  const activeTab = tabs.findIndex(tab => tab.path === pathname)
  const showTabs = activeTab >= 0
  useEffect(() => { window.scrollTo(0,0) },[pathname])
  return <><div className={'app-main' + (showTabs ? ' has-tabs' : '') + (showTabs && active ? ' has-session':'')}>
    <Suspense fallback={<div className="screen" role="status">Opening…</div>}><div key={pathname} className="app-route-stage"><Outlet/></div></Suspense>
  </div>{showTabs && <div className="app-chrome">{active && <SessionDock workout={active}/>}<nav className="app-tabs" aria-label="Main navigation">
    <span className="app-tab-track" aria-hidden="true"><span style={{transform: `translateX(${activeTab * 100}%)`}}/></span>
    {tabs.map(({path,name,icon:Icon})=><NavLink key={path} end to={path}><Icon size={19}/><span>{name}</span></NavLink>)}
  </nav></div>}<ConnectionUpdate/></>
}
function MissingRoute() {return <main className="app-error"><h1>Page unavailable</h1><Link className="btn" to="/">Return home</Link></main>}
function RouteError(){return <main className="app-error" role="alert"><h1>This screen couldn’t open</h1><p>Reload IronLog to try again.</p><a href="/" className="btn primary">Reload IronLog</a></main>}
const router = createBrowserRouter([{element:<Shell/>,errorElement:<RouteError/>,children:[
  {index:true,element:<Home/>},{path:'history',element:<History/>},{path:'library',element:<Library/>},{path:'insights',element:<Insights/>},
  {path:'log/:exerciseId',element:<Logger/>},{path:'workout/:workoutId/exercise/:exerciseId',element:<Logger/>},
  {path:'workout/active',element:<WorkoutOverview/>},{path:'workout/builder',element:<Builder/>},{path:'summary/:workoutId',element:<Summary/>},
  {path:'exercises/select',element:<ExerciseSelect/>},{path:'exercises/new',element:<ExerciseForm/>},{path:'exercises/:id/edit',element:<ExerciseForm/>},
  {path:'routines',element:<Routines/>},{path:'routines/new',element:<Routines/>},{path:'routines/:id/edit',element:<Routines/>},{path:'routines/:id',element:<RoutineDetail/>},
  {path:'routines/:id/days/new',element:<RoutineDay/>},{path:'routines/:id/days/:dayId/edit',element:<RoutineDay/>},{path:'routines/:id/start',element:<RoutineStart/>},
  {path:'report/monthly',element:<MonthlyReport/>},{path:'settings',element:<Settings/>},{path:'settings/theme',element:<ThemeSettings/>},{path:'settings/muscles',element:<MuscleGroups/>},
  {path:'*',element:<MissingRoute/>}
]}])
export default function App(){return <RouterProvider router={router}/>}
