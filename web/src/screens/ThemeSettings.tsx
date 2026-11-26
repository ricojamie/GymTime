import { useEffect, useRef, useState } from 'react'
import { Check, Upload } from 'lucide-react'
import { Link } from 'react-router-dom'
import { SwoleGator } from '../components/mascot'
import { useIsGatorMode, useSettings } from '../theme'
import {
  accents,
  accentPalette,
  deriveColors,
  fontNames,
  hexToHsv,
  hsvToHex,
  isHex,
} from '../theme/colors'
import { Group, SettingsHeader, ToggleRow, useSettingsWriter } from '../theme/SettingsUi'

function CustomAccent({
  initial,
  apply,
}: {
  initial: string
  apply: (color: string) => Promise<boolean>
}) {
  const [hsv, setHsv] = useState(() => hexToHsv(initial))
  const [input, setInput] = useState(initial)
  const [invalid, setInvalid] = useState(false)

  useEffect(() => {
    setHsv(hexToHsv(initial))
    setInput(initial)
    setInvalid(false)
  }, [initial])

  function slide(i: number, value: number) {
    const next: [number, number, number] = [...hsv]
    next[i] = value
    setHsv(next)
    setInput(hsvToHex(...next))
    setInvalid(false)
  }

  function typeHex(value: string) {
    const color = value.startsWith('#') ? value : `#${value}`
    setInput(color)
    setInvalid(!isHex(color))
    if (isHex(color)) setHsv(hexToHsv(color))
  }

  return (
    <div className="st-color-panel">
      <label className="st-hex">
        <span className="st-preview-chip" style={{ backgroundColor: hsvToHex(...hsv) }} />
        Hex color
        <input
          type="text"
          value={input}
          onChange={(e) => typeHex(e.target.value)}
          spellCheck={false}
          maxLength={7}
          aria-invalid={invalid}
          autoCapitalize="characters"
        />
      </label>
      {['Hue', 'Saturation', 'Brightness'].map((label, i) => (
        <label key={label} className="st-color-slider">
          <span>{label}</span>
          <input
            type="range"
            min="0"
            max={i === 0 ? 359 : 100}
            step="1"
            value={Math.round(hsv[i])}
            onChange={(e) => slide(i, Number(e.target.value))}
          />
          <output>
            {Math.round(hsv[i])}
            {i === 0 ? '°' : '%'}
          </output>
        </label>
      ))}
      {invalid && (
        <p role="alert" className="st-alert">
          Enter six hex digits, such as #A3E635.
        </p>
      )}
      <button
        type="button"
        className="btn primary"
        disabled={invalid}
        onClick={() => void apply(input.toUpperCase())}
      >
        Apply color
      </button>
    </div>
  )
}

export default function ThemeSettings() {
  const prefs = useSettings()
  const gatorMode = useIsGatorMode()
  const { error, save, run } = useSettingsWriter()
  const file = useRef<HTMLInputElement>(null)
  const [editingCustom, setEditingCustom] = useState(false)
  const [previewPop, setPreviewPop] = useState<'idle' | 'a' | 'b'>('idle')
  const [poppedAccent, setPoppedAccent] = useState<string | null>(null)
  const [poppedFont, setPoppedFont] = useState<string | null>(null)
  const previewTimer = useRef<number | null>(null)
  const accentTimer = useRef<number | null>(null)
  const fontTimer = useRef<number | null>(null)

  useEffect(() => {
    if (gatorMode) setEditingCustom(false)
  }, [gatorMode])

  useEffect(() => {
    return () => {
      if (previewTimer.current !== null) window.clearTimeout(previewTimer.current)
      if (accentTimer.current !== null) window.clearTimeout(accentTimer.current)
      if (fontTimer.current !== null) window.clearTimeout(fontTimer.current)
    }
  }, [])

  function triggerPreviewPop() {
    if (previewTimer.current !== null) window.clearTimeout(previewTimer.current)
    setPreviewPop((prev) => (prev === 'a' ? 'b' : 'a'))
    previewTimer.current = window.setTimeout(() => {
      setPreviewPop('idle')
      previewTimer.current = null
    }, 460)
  }

  function triggerAccentPop(key: string) {
    if (accentTimer.current !== null) window.clearTimeout(accentTimer.current)
    setPoppedAccent(key)
    accentTimer.current = window.setTimeout(() => {
      setPoppedAccent(null)
      accentTimer.current = null
    }, 440)
    triggerPreviewPop()
  }

  function triggerFontPop(key: string) {
    if (fontTimer.current !== null) window.clearTimeout(fontTimer.current)
    setPoppedFont(key)
    fontTimer.current = window.setTimeout(() => {
      setPoppedFont(null)
      fontTimer.current = null
    }, 440)
    triggerPreviewPop()
  }

  const color = deriveColors(prefs).accent
  const customColor =
    prefs.customThemeColor && isHex(prefs.customThemeColor) ? prefs.customThemeColor : color

  async function upload(fontFile: File) {
    if (!/\.(ttf|otf)$/i.test(fontFile.name)) {
      throw new Error('Choose a .ttf or .otf font.')
    }
    const bytes = await fontFile.arrayBuffer()
    try {
      await new FontFace('IronLog Local Test', bytes).load()
    } catch {
      throw new Error('This file could not be read as a font.')
    }
    const { updatePreferences } = await import('../db/repositories')
    await updatePreferences({
      customFontBlob: bytes,
      customFontName: fontFile.name,
      themeFont: 'custom',
    })
    triggerFontPop('custom')
  }

  const surfaceLabel = gatorMode
    ? prefs.darkMode
      ? 'UF Swamp Dark'
      : 'UF Stadium Light'
    : prefs.darkMode
      ? 'Obsidian'
      : 'Ceramic'

  const fontLabel =
    prefs.themeFont === 'custom'
      ? prefs.customFontName
      : fontNames[prefs.themeFont] ?? 'Bebas Neue'

  return (
    <main className="screen st-screen">
      <SettingsHeader title="Theme & fonts" />

      <div
        className="st-theme-preview stagger-in"
        data-pop={previewPop !== 'idle' ? previewPop : undefined}
      >
        <div className="st-theme-preview-head">
          <div className="st-theme-preview-copy">
            <span className="eyebrow">Live preview</span>
            <div aria-label="IronLog" className="st-wordmark">
              IRON<span>LOG</span>
            </div>
          </div>
          {gatorMode && (
            <div className="st-theme-preview-badge">
              <SwoleGator pose="badge" size={48} interactive />
            </div>
          )}
        </div>
        <span className="st-meta">
          {surfaceLabel} · {fontLabel}
        </span>
      </div>

      {error && (
        <p role="alert" className="st-alert">
          {error}
        </p>
      )}

      <Group title="Appearance">
        <ToggleRow
          label="Dark mode"
          checked={prefs.darkMode}
          onChange={() => {
            triggerPreviewPop()
            void save({ darkMode: !prefs.darkMode })
          }}
        />
      </Group>

      <Group title="Accent">
        {gatorMode && (
          <p className="st-note st-gator-lock-note">
            UF Swole Gator theme controls the accent palette.{' '}
            <Link className="st-inline-link" to="/settings">
              Switch back to Classic in Settings
            </Link>
            .
          </p>
        )}
        <div className="st-swatches">
          {accents.map(([key, label]) => (
            <button
              type="button"
              className="st-swatch"
              key={key}
              disabled={gatorMode}
              aria-pressed={!gatorMode && prefs.themeColor === key}
              data-pop={poppedAccent === key ? 'true' : undefined}
              onClick={() => {
                setEditingCustom(false)
                triggerAccentPop(key)
                void save({ themeColor: key })
              }}
            >
              <i style={{ background: accentPalette[key] }} aria-hidden="true" />
              <span>{label}</span>
              {!gatorMode && prefs.themeColor === key && <Check size={17} />}
            </button>
          ))}
        </div>
        <button
          type="button"
          className="st-action st-row"
          disabled={gatorMode}
          aria-expanded={!gatorMode && editingCustom}
          onClick={() => setEditingCustom(!editingCustom)}
        >
          <span>Custom color</span>
          <span className="st-trailing">
            <i className="st-small-chip" style={{ background: customColor }} aria-hidden="true" />
            {customColor.toUpperCase()}
          </span>
        </button>
        {!gatorMode && editingCustom && (
          <CustomAccent
            initial={customColor}
            apply={async (value) => {
              const ok = await save({ themeColor: 'custom', customThemeColor: value })
              if (ok) {
                triggerPreviewPop()
                setEditingCustom(false)
              }
              return ok
            }}
          />
        )}
      </Group>

      <Group title="Display font">
        {Object.entries(fontNames).map(([key, label]) => (
          <button
            type="button"
            key={key}
            className="st-row st-action st-font-row"
            aria-pressed={prefs.themeFont === key}
            data-pop={poppedFont === key ? 'true' : undefined}
            onClick={() => {
              triggerFontPop(key)
              void save({ themeFont: key })
            }}
          >
            <span style={{ fontFamily: `"${label}", sans-serif`, fontSize: 21 }}>{label}</span>
            {prefs.themeFont === key && <Check className="st-selected" size={18} />}
          </button>
        ))}
        {prefs.customFontBlob && (
          <button
            type="button"
            className="st-row st-action st-font-row"
            aria-pressed={prefs.themeFont === 'custom'}
            data-pop={poppedFont === 'custom' ? 'true' : undefined}
            onClick={() => {
              triggerFontPop('custom')
              void save({ themeFont: 'custom' })
            }}
          >
            <span>{prefs.customFontName || 'Uploaded font'}</span>
            {prefs.themeFont === 'custom' && <Check className="st-selected" size={18} />}
          </button>
        )}
        <button
          type="button"
          className="st-action st-row"
          onClick={() => file.current?.click()}
        >
          <span>Upload font</span>
          <Upload size={17} />
        </button>
        <input
          ref={file}
          type="file"
          hidden
          accept=".ttf,.otf"
          onChange={(e) => {
            const font = e.currentTarget.files?.[0]
            e.currentTarget.value = ''
            if (font) void run(() => upload(font))
          }}
        />
      </Group>
    </main>
  )
}
