import { useEffect, useRef, useState } from 'react'
import { Check, Upload } from 'lucide-react'
import { useIsGatorMode, useSettings } from '../theme'
import { accents, accentPalette, deriveColors, fontNames, hsvToHex, hexToHsv, isHex } from '../theme/colors'
import { Group, SettingsHeader, ToggleRow, useSettingsWriter } from '../theme/SettingsUi'

function CustomAccent({initial, apply}:{initial:string; apply:(color:string)=>Promise<boolean>}) {
  const [hsv,setHsv] = useState(() => hexToHsv(initial))
  const [input,setInput] = useState(initial)
  const [invalid,setInvalid] = useState(false)
  useEffect(()=>{ setHsv(hexToHsv(initial));setInput(initial);setInvalid(false) },[initial])
  function slide(i:number,value:number) {
    const next:[number,number,number] = [...hsv]
    next[i] = value
    setHsv(next); setInput(hsvToHex(...next)); setInvalid(false)
  }
  function typeHex(value:string) {
    const color = value.startsWith('#') ? value : '#'+value
    setInput(color)
    setInvalid(!isHex(color))
    if (isHex(color)) setHsv(hexToHsv(color))
  }
  return <div className="st-color-panel">
    <label className="st-hex"><span className="st-preview-chip" style={{backgroundColor:hsvToHex(...hsv)}}/>Hex color<input type="text" value={input} onChange={e=>typeHex(e.target.value)}
      spellCheck={false} maxLength={7} aria-invalid={invalid} autoCapitalize="characters"/></label>
    {['Hue','Saturation','Brightness'].map((label,i)=><label key={label} className="st-color-slider">
      <span>{label}</span><input type="range" min="0" max={i === 0 ? 359 : 100} step="1" value={Math.round(hsv[i])} onChange={e=>slide(i,Number(e.target.value))}/>
      <output>{Math.round(hsv[i])}{i === 0 ? '°':'%'}</output></label>)}
    {invalid && <p role="alert" className="st-alert">Enter six hex digits, such as #A3E635.</p>}
    <button type="button" className="btn primary" disabled={invalid} onClick={()=>void apply(input.toUpperCase())}>Apply color</button>
  </div>
}

export default function ThemeSettings() {
  const prefs = useSettings()
  const gatorMode = useIsGatorMode()
  const {error,save,run} = useSettingsWriter()
  const file = useRef<HTMLInputElement>(null)
  const [editingCustom,setEditingCustom] = useState(false)
  const color = deriveColors(prefs).accent
  const customColor = prefs.customThemeColor && isHex(prefs.customThemeColor) ? prefs.customThemeColor : color
  async function upload(fontFile:File) {
    if (!/\.(ttf|otf)$/i.test(fontFile.name)) throw new Error('Choose a .ttf or .otf font.')
    const bytes = await fontFile.arrayBuffer()
    try { await new FontFace('IronLog Local Test',bytes).load() }
    catch { throw new Error('This file could not be read as a font.') }
    const { updatePreferences } = await import('../db/repositories')
    await updatePreferences({customFontBlob:bytes,customFontName:fontFile.name,themeFont:'custom'})
  }
  const surfaceLabel = gatorMode
    ? (prefs.darkMode ? 'UF Swamp Dark' : 'UF Stadium Light')
    : (prefs.darkMode ? 'Obsidian' : 'Ceramic')
  return <main className="screen st-screen">
    <SettingsHeader title="Theme & fonts"/>
    <div className="st-theme-preview"><span className="eyebrow">Live preview</span>
      <div aria-label="IronLog" className="st-wordmark">IRON<span>LOG</span></div>
      <span className="st-meta">{surfaceLabel} · {prefs.themeFont === 'custom' ? prefs.customFontName : fontNames[prefs.themeFont] ?? 'Bebas Neue'}</span></div>
    {error && <p role="alert" className="st-alert">{error}</p>}
    <Group title="Appearance"><ToggleRow label="Dark mode" checked={prefs.darkMode} onChange={()=>void save({darkMode:!prefs.darkMode})}/></Group>
    <Group title="Accent">
      {gatorMode && <p className="st-note">UF Swole Gator theme is active. Switch back to Classic in Settings to use custom accent colors.</p>}
      <div className="st-swatches">{accents.map(([key,label])=><button className="st-swatch" key={key}
        aria-pressed={!gatorMode && prefs.themeColor===key} onClick={()=>{setEditingCustom(false);void save({themeColor:key})}}>
        <i style={{background:accentPalette[key]}} aria-hidden="true"/><span>{label}</span>{!gatorMode && prefs.themeColor===key && <Check size={17}/>}</button>)}</div>
      <button className="st-action st-row" aria-expanded={editingCustom} onClick={()=>setEditingCustom(!editingCustom)}>
        <span>Custom color</span><span className="st-trailing"><i className="st-small-chip" style={{background:customColor}}/>{customColor.toUpperCase()}</span></button>
      {editingCustom && <CustomAccent initial={customColor} apply={async value => {
        const ok = await save({themeColor:'custom',customThemeColor:value}); if (ok) setEditingCustom(false); return ok
      }}/>}
    </Group>
    <Group title="Display font">{Object.entries(fontNames).map(([key,label])=><button key={key} className="st-row st-action"
      aria-pressed={prefs.themeFont===key} onClick={()=>void save({themeFont:key})}>
      <span style={{fontFamily:'"'+label+'",sans-serif',fontSize:21}}>{label}</span>{prefs.themeFont===key && <Check className="st-selected" size={18}/>}</button>)}
      {prefs.customFontBlob && <button className="st-row st-action" aria-pressed={prefs.themeFont==='custom'} onClick={()=>void save({themeFont:'custom'})}>
        <span>{prefs.customFontName || 'Uploaded font'}</span>{prefs.themeFont==='custom' && <Check size={18}/>}</button>}
      <button type="button" className="st-action st-row" onClick={()=>file.current?.click()}><span>Upload font</span><Upload size={17}/></button>
      <input ref={file} type="file" hidden accept=".ttf,.otf" onChange={e=>{const font=e.currentTarget.files?.[0];e.currentTarget.value='';if(font) void run(()=>upload(font))}}/>
    </Group>
  </main>
}
