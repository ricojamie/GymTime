import { useEffect, useLayoutEffect } from 'react'
import { useLiveQuery } from 'dexie-react-hooks'
import { db } from '../db/database'
import { defaultPreferences, type Preferences } from '../db/types'
import { updatePreferences } from '../db/repositories'
import { deriveColors, fontNames } from './colors'

export function iconUrls(icon: Preferences['appIcon']) {
  const gator = icon === 'swole_alligator'
  const stamp = gator ? '/icons/swole-alligator-180.png' : '/icons/apple-touch-icon-180.png'
  return {
    stamp, touch: stamp, icon: gator ? '/icons/swole-alligator-192.png' : '/icons/classic-192.png',
    manifest: gator ? '/manifest-alligator.webmanifest' : '/manifest.webmanifest',
  }
}

export function useSettings() {
  return useLiveQuery(() => db.preferences.get('default'), [], defaultPreferences) ?? defaultPreferences
}

export async function chooseIcon(icon: Preferences['appIcon']) {
  await updatePreferences({appIcon:icon})
  const url = new URL(window.location.href)
  if (icon === 'swole_alligator') url.searchParams.set('icon','swole-alligator')
  else url.searchParams.delete('icon')
  window.history.replaceState(window.history.state, '', url)
}

export function useTheme() {
  const prefs = useSettings()
  useEffect(() => {
    const icon = new URL(window.location.href).searchParams.get('icon')
    if (icon === 'swole-alligator') void updatePreferences({appIcon:'swole_alligator'}).catch(() => {})
  }, [])
  useLayoutEffect(() => {
    const root = document.documentElement
    const colors = deriveColors(prefs)
    root.dataset.theme = prefs.darkMode ? 'dark' : 'light'
    root.style.colorScheme = root.dataset.theme
    root.style.setProperty('--accent', colors.accent)
    root.style.setProperty('--accent-ink', colors.accentInk)
    root.style.setProperty('--on-accent', colors.onAccent)
    const bg = prefs.darkMode ? '#090A0D' : '#F2F3F5'
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content',bg)
  }, [prefs.themeColor, prefs.darkMode, prefs.customThemeColor])
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
