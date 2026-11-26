import type { Preferences } from '../db/types'

export const accentPalette: Record<string, string> = {
  lime: '#A3E635', summer_shred: '#FF6B35', ocean_drive: '#2EC4FF', graphite_punch: '#D4AF37',
  blue: '#3B82F6', purple: '#A855F7', pink: '#EC4899', gold: '#F59E0B', red: '#EF4444',
  orange: '#F97316', mint: '#10B981', slate: '#64748B', lavender: '#8B5CF6',
}
export const accents = [
  ['lime', 'Lime'], ['summer_shred', 'Summer Shred'], ['ocean_drive', 'Ocean Drive'], ['graphite_punch', 'Graphite Punch'],
] as const
export const fontNames: Record<string, string> = {
  bebas_neue: 'Bebas Neue', oswald: 'Oswald', space_grotesk: 'Space Grotesk', raleway: 'Raleway', pacifico: 'Pacifico',
}

type Channels = [number, number, number]
const hex = ([r,g,b]: Channels) => '#' + [r,g,b].map(c => Math.round(c).toString(16).padStart(2, '0')).join('').toUpperCase()
const rgb = (color: string): Channels => [1,3,5].map(i => parseInt(color.slice(i,i+2),16)) as Channels
const mix = (a: Channels, b: Channels, ratio: number): Channels => a.map((c,i) => c*(1-ratio) + b[i]*ratio) as Channels
export const isHex = (input: string) => /^#[0-9a-f]{6}$/i.test(input)
export function contrast(a: string, b: string) {
  const luminance = (c: string) => rgb(c).map(x => x/255).map(x => x <= 0.04045 ? x/12.92 : ((x+0.055)/1.055)**2.4)
    .reduce((total,x,i) => total + x * [0.2126,0.7152,0.0722][i],0)
  const x = luminance(a), y = luminance(b)
  return (Math.max(x,y)+0.05) / (Math.min(x,y)+0.05)
}
export function hexToHsv(color: string): Channels {
  const [r,g,b] = rgb(color).map(c => c/255)
  const max = Math.max(r,g,b), min = Math.min(r,g,b), delta = max-min
  const hue = !delta ? 0 : max === r ? (g-b)/delta : max === g ? (b-r)/delta+2 : (r-g)/delta+4
  return [(hue*60+360)%360, max ? delta/max*100 : 0, max*100]
}
export function hsvToHex(h: number, s: number, v: number) {
  const c = v/100*s/100, x = c*(1-Math.abs((h/60)%2-1)), m = v/100-c
  const sector = Math.floor(h/60)%6
  const base = [[c,x,0],[x,c,0],[0,c,x],[0,x,c],[x,0,c],[c,0,x]][sector]
  return hex(base.map(n => (n+m)*255) as Channels)
}
export function deriveColors(prefs: Pick<Preferences,'themeColor'|'customThemeColor'|'darkMode'>) {
  const accent = prefs.themeColor === 'custom' && prefs.customThemeColor && isHex(prefs.customThemeColor)
    ? prefs.customThemeColor : accentPalette[prefs.themeColor] ?? accentPalette.lime
  const surface = hex(mix(rgb(prefs.darkMode ? '#12141A' : '#FFFFFF'), rgb(accent), 0.025))
  const onAccent = contrast(accent,'#090A0D') > contrast(accent,'#FFFFFF') ? '#090A0D' : '#FFFFFF'
  let accentInk = accent
  if (contrast(accent,surface) < 4.5) {
    // Adjust HSL lightness while preserving the selected hue and saturation.
    const [hue, saturationPercent, valuePercent] = hexToHsv(accent)
    const value = valuePercent / 100
    const lightness = value * (1 - saturationPercent / 200)
    const saturation = lightness === 0 || lightness === 1
      ? 0 : (value - lightness) / Math.min(lightness, 1 - lightness)
    const textColor = (amount: number) => {
      const light = lightness + ((prefs.darkMode ? 1 : 0) - lightness) * amount
      const nextValue = light + saturation * Math.min(light, 1 - light)
      const nextSaturation = nextValue === 0 ? 0 : 2 * (1 - light / nextValue)
      return hsvToHex(hue, nextSaturation * 100, nextValue * 100)
    }
    let low = 0, high = 1
    for (let i = 0; i < 12; i++) {
      const mid = (low + high) / 2
      if (contrast(textColor(mid), surface) >= 4.5) high = mid
      else low = mid
    }
    accentInk = textColor(high)
  }
  return {accent, accentInk, onAccent, surface}
}
