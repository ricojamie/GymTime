import { useRef, useState } from 'react'
import { LockKeyhole, Pencil, Plus, Trash2 } from 'lucide-react'
import { useLiveQuery } from 'dexie-react-hooks'
import { BottomSheet } from '../components/primitives'
import { db } from '../db/database'
import { addMuscle, renameMuscle, deleteMuscle } from '../db/repositories'
import { WARMUP_MUSCLE_GROUP } from '../db/types'
import { Group, SettingsHeader } from '../theme/SettingsUi'

type Edit = {kind:'add'} | {kind:'rename'|'delete';name:string}
export default function MuscleGroups() {
  const groups = useLiveQuery(()=>db.muscle_groups.toArray(),[],[])
  const exercises = useLiveQuery(()=>db.exercises.toArray(),[],[])
  const [edit,setEdit] = useState<Edit|null>(null),[value,setValue] = useState(''),[error,setError] = useState('')
  const [busy,setBusy] = useState(false),pending=useRef(false)
  function open(next:Edit) {setValue(next.kind==='rename'?next.name:'');setError('');setEdit(next)}
  const affected = edit?.kind==='delete' ? exercises.filter(e=>e.targetMuscle===edit.name) : []
  const target = edit?.kind==='delete' ? edit.name : ''
  const history = useLiveQuery(async()=>{
    if (!target) return 0
    const ids=await db.exercises.where('targetMuscle').equals(target).primaryKeys()
    return ids.length?db.sets.where('exerciseId').anyOf(ids).count():0
  },[target])
  async function commit() {
    if (!edit || pending.current) return
    pending.current=true;setBusy(true);setError('')
    try {
      if (edit.kind==='add') await addMuscle(value.trim())
      else if (edit.kind==='rename') await renameMuscle(edit.name,value.trim())
      else await deleteMuscle(edit.name,affected.length?'Uncategorized':undefined)
      setEdit(null)
    } catch(e){setError(e instanceof Error?e.message:'Could not update this body part.')}
    finally{pending.current=false;setBusy(false)}
  }
  return <main className="screen st-screen"><SettingsHeader title="Body parts"/>
    <Group title={groups.length+' body parts'}>{[...groups].sort((a,b)=>a.name.localeCompare(b.name)).map(g=>{
      const count=exercises.filter(e=>e.targetMuscle===g.name).length,locked=g.name===WARMUP_MUSCLE_GROUP
      return <div className="st-row" key={g.name}><div className="st-label-stack"><span>{g.name}</span><small>{count} {count===1?'exercise':'exercises'}</small></div>
        {locked ? <LockKeyhole aria-label="Built-in body part" size={17} className="st-meta"/> :
          <div className="st-actions"><button className="icon-button" aria-label={'Rename '+g.name} onClick={()=>open({kind:'rename',name:g.name})}><Pencil size={17}/></button>
            <button className="icon-button" aria-label={'Delete '+g.name} onClick={()=>open({kind:'delete',name:g.name})}><Trash2 size={17}/></button></div>}</div>
    })}</Group><button className="btn primary st-add" onClick={()=>open({kind:'add'})}><Plus size={17}/> Add body part</button>
    {edit && <BottomSheet title={edit.kind==='add'?'Add body part':edit.kind==='rename'?'Rename body part':'Delete '+edit.name+'?'} onClose={()=>{if(!busy)setEdit(null)}}>
      <form className="st-sheet" onSubmit={e=>{e.preventDefault();void commit()}}>
        {edit.kind!=='delete'? <label className="st-label-stack">Name<input className="st-text" type="text" autoFocus required value={value} onChange={e=>setValue(e.target.value)}/></label>
          : <p>{history===undefined?'Checking workout history…':history>0?history+' logged sets use this body part. Keep it to preserve workout history.':affected.length?
            affected.length+' exercises will move to Uncategorized.':'This body part will be removed.'}</p>}
        {error && <p role="alert" className="st-alert">{error}</p>}
        <button className={edit.kind==='delete'?'btn st-danger-fill':'btn primary'} disabled={busy || (edit.kind==='delete' ? history===undefined || history>0 || (edit.name==='Uncategorized' && affected.length>0) : !value.trim())}>
          {edit.kind==='delete'?'Delete body part':edit.kind==='add'?'Add body part':'Save name'}</button>
      </form></BottomSheet>}
  </main>
}
