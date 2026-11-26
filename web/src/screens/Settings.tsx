import { useEffect, useState } from 'react'
import { Check } from 'lucide-react'
import { chooseIcon, iconUrls, useSettings } from '../theme'
import { Group, Segments, SettingLink, SettingsHeader, ToggleRow, useSettingsWriter } from '../theme/SettingsUi'
import BackupSettings from '../theme/BackupSettings'
import EquipmentSettings from '../theme/EquipmentSettings'
import type { Preferences } from '../db/types'

const trainOptions = [
  ['timerAutoStart','Auto-start rest timer'], ['timerAudioEnabled','Timer sound'],
  ['timerVibrateEnabled','Timer vibration'], ['keepScreenOn','Keep screen awake'],
] as const
const appIcons = [['classic','Classic'],['swole_alligator','Swole Alligator']] as const
function isStandaloneIOS() {
  return !!(navigator as Navigator & {standalone?:boolean}).standalone
}
export default function Settings() {
  const prefs = useSettings(), {save,run,error} = useSettingsWriter()
  const [name,setName] = useState(prefs.userName)
  useEffect(()=>setName(prefs.userName),[prefs.userName])
  const iconChange = (next:Preferences['appIcon'])=>run(()=>chooseIcon(next))
  return <main className="screen st-screen"><SettingsHeader title="Settings" back="/"/>
    {error && <p className="st-alert" role="alert">{error}</p>}
    <Group title="Profile"><label className="st-row"><span>Name</span>
      <input type="text" className="st-name" aria-label="Name" value={name} autoComplete="given-name" onChange={e=>setName(e.target.value)} onBlur={()=>{
        const normalized=name.trim()||'Athlete';setName(normalized);if(normalized!==prefs.userName) void save({userName:normalized})
      }} onKeyDown={e=>{if(e.key==='Enter')e.currentTarget.blur()}}/></label>
      <div className="st-row"><span>Weight units</span><Segments label="Weight display unit" items={['lbs','kg'] as const} value={prefs.preferredWeightUnit}
        onChange={preferredWeightUnit=>void save({preferredWeightUnit})}/></div></Group>
    <Group title="Look & feel"><SettingLink to="/settings/theme">Theme & fonts</SettingLink>
      <ToggleRow label="Dark mode" checked={prefs.darkMode} onChange={()=>void save({darkMode:!prefs.darkMode})}/>
      <div className="st-block"><h3>App icon</h3><div className="st-icon-options">{appIcons.map(([key,label])=>
        <button type="button" key={key} aria-pressed={prefs.appIcon===key} onClick={()=>void iconChange(key)}><img width={38} height={38} src={iconUrls(key).touch} alt=""/>
          <span>{label}</span>{prefs.appIcon===key && <Check size={15}/>}</button>)}</div>
        {isStandaloneIOS() && <p className="st-note">Open in Safari and Add to Home Screen to update the iPhone icon.</p>}
      </div></Group>
    <Group title="While you train">{trainOptions.map(([key,label])=><ToggleRow key={key} label={label} checked={prefs[key]} onChange={()=>void save({[key]:!prefs[key]})}/>)}</Group>
    <Group title="Consistency"><label className="st-row"><span>Rest days per week</span><select value={prefs.restDaysPerWeek} onChange={e=>void save({restDaysPerWeek:Number(e.target.value)})}>
      {[0,1,2,3,4,5,6,7].map(n=><option key={n} value={n}>{n}</option>)}</select></label></Group>
    <Group title="Reports & body parts"><ToggleRow label="Monthly reports" checked={prefs.monthlyReportEnabled} onChange={()=>void save({monthlyReportEnabled:!prefs.monthlyReportEnabled})}/>
      <SettingLink to="/report/monthly">Last month’s report</SettingLink><SettingLink to="/settings/muscles">Manage body parts</SettingLink></Group>
    <EquipmentSettings prefs={prefs}/><BackupSettings prefs={prefs}/>
    <footer className="st-footer"><img width={28} height={28} alt="" src={iconUrls(prefs.appIcon).stamp}/><span>IronLog</span><span>1.0.0</span></footer>
  </main>
}
