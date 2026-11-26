import { useEffect, useLayoutEffect, useRef } from 'react'
import { useLiveQuery } from 'dexie-react-hooks'
import { db } from '../db/database'
import { defaultPreferences, type Preferences } from '../db/types'
import { updatePreferences } from '../db/repositories'
import { deriveColors, fontNames, UF_COLORS } from './colors'

export function iconUrls(icon: Preferences['appIcon']) {
  const gator = icon === 'swole_alligator'
  const stamp = gator ? '/icons/swole-alligator-180.png' : '/icons/apple-touch-icon-180.png'
  return {
    stamp, touch: stamp, icon: gator ? '/icons/swole-alligator-192.png' : '/icons/classic-192.png',
    manifest: gator ? '/manifest-alligator.webmanifest' : '/manifest.webmanifest',
  }
}

function useStoredSettings() {
  return useLiveQuery(async () => (await db.preferences.get('default')) ?? defaultPreferences, [])
}

export function useSettings() {
  return useStoredSettings() ?? defaultPreferences
}

export function useIsGatorMode() {
  return useSettings().appIcon === 'swole_alligator'
}

export async function chooseIcon(icon: Preferences['appIcon']) {
  await updatePreferences({appIcon:icon})
  const url = new URL(window.location.href)
  if (icon === 'swole_alligator') url.searchParams.set('icon','swole-alligator')
  else url.searchParams.delete('icon')
  window.history.replaceState(window.history.state, '', url)
}

export function useTheme() {
  const stored = useStoredSettings()
  const prefs = stored ?? defaultPreferences
  const prevBrand = useRef<Preferences['appIcon'] | null>(null)
  // Keep first paint still until IndexedDB resolves the user's motion preference.
  const reduceMotion = stored === undefined || stored.reduceMotion === true
  useLayoutEffect(() => {
    const media = window.matchMedia?.('(prefers-reduced-motion: reduce)')
    const sync = () => {
      document.documentElement.dataset.motion = reduceMotion || media?.matches ? 'reduced' : 'full'
    }
    sync()
    media?.addEventListener('change', sync)
    return () => media?.removeEventListener('change', sync)
  }, [reduceMotion])
  useEffect(() => {
    const icon = new URL(window.location.href).searchParams.get('icon')
    if (icon === 'swole-alligator') void updatePreferences({appIcon:'swole_alligator'}).catch(() => {})
  }, [])
  useLayoutEffect(() => {
    const root = document.documentElement
    const gator = prefs.appIcon === 'swole_alligator'
    const colors = deriveColors(prefs)
    root.dataset.theme = prefs.darkMode ? 'dark' : 'light'
    root.dataset.brand = gator ? 'gator' : 'classic'
    root.style.colorScheme = root.dataset.theme
    root.style.setProperty('--accent', colors.accent)
    root.style.setProperty('--accent-ink', colors.accentInk)
    root.style.setProperty('--on-accent', colors.onAccent)
    const bg = gator
      ? (prefs.darkMode ? UF_COLORS.swampDarkCanvas : UF_COLORS.stadiumLightCanvas)
      : (prefs.darkMode ? '#090A0D' : '#F2F3F5')
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content',bg)

    if (prevBrand.current !== null && prevBrand.current !== prefs.appIcon && root.dataset.motion === 'full') {
      root.dataset.brandMorph = 'true'
      const timer = window.setTimeout(() => { delete root.dataset.brandMorph }, 380)
      prevBrand.current = prefs.appIcon
      return () => window.clearTimeout(timer)
    }
    prevBrand.current = prefs.appIcon
  }, [prefs.themeColor, prefs.darkMode, prefs.customThemeColor, prefs.appIcon])
  useLayoutEffect(() => {
    const urls = iconUrls(prefs.appIcon)
    for (const [rel, val] of [['icon',urls.icon],['apple-touch-icon',urls.touch],['manifest',urls.manifest]]) {
      document.querySelector('link[rel="'+rel+'"]')?.setAttribute('href',val)
    }
  }, [prefs.appIcon])
  useEffect(() => {
    const root = document.documentElement
    const font = fontNames[prefs.themeFont] ?? fontNames.bebas_neue
    root.style.setProperty('--font-display','"'+font+'", sans-serif')
    if (prefs.themeFont !== 'custom' || !prefs.customFontBlob || typeof FontFace === 'undefined') return
    let live = true
    const face = new FontFace('IronLog Local', prefs.customFontBlob)
    void face.load().then(() => {
      if (live) {
        document.fonts.add(face)
        root.style.setProperty('--font-display','"IronLog Local", sans-serif')
      }
    }).catch(() => {})
    return () => { live = false; document.fonts.delete(face) }
  },[prefs.themeFont, prefs.customFontBlob])
}
