import { it, expect } from 'vitest'
import { accentPalette, contrast, deriveColors, hexToHsv, hsvToHex, UF_COLORS } from './colors'

it('keeps each built-in and extreme custom accent legible in both modes', () => {
  for (const darkMode of [true, false]) {
    for (const customThemeColor of [...Object.values(accentPalette), '#FFFFFF', '#090A0D', '#777777']) {
      const {accentInk,surface,accent,onAccent} = deriveColors({themeColor:'custom',customThemeColor,darkMode})
      expect(contrast(accentInk,surface)).toBeGreaterThanOrEqual(4.5)
      expect(contrast(accent,onAccent)).toBe(Math.max(contrast(accent,'#090A0D'),contrast(accent,'#FFFFFF')))
      expect(hsvToHex(...hexToHsv(customThemeColor))).toBe(customThemeColor)
    }
  }
})

it('derives legible UF Gators colors in both dark and light modes when swole_alligator is active', () => {
  const dark = deriveColors({ themeColor: 'lime', customThemeColor: null, darkMode: true, appIcon: 'swole_alligator' })
  expect(dark.accent).toBe(UF_COLORS.orange)
  expect(contrast(dark.accentInk, dark.surface)).toBeGreaterThanOrEqual(4.5)

  const light = deriveColors({ themeColor: 'lime', customThemeColor: null, darkMode: false, appIcon: 'swole_alligator' })
  expect(light.accent).toBe(UF_COLORS.blue)
  expect(contrast(light.accentInk, light.surface)).toBeGreaterThanOrEqual(4.5)
})
