import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { db } from '../db/database'
import { defaultPreferences } from '../db/types'
import { toLbs } from '../domain/units'
import EquipmentSettings from './EquipmentSettings'
import Settings from '../screens/Settings'
import { UF_COLORS } from './colors'
import { chooseIcon, iconUrls, useTheme } from './index'

beforeEach(async () => {
  await db.open()
  await db.preferences.put({ ...defaultPreferences })
})
afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  delete document.documentElement.dataset.motion
  delete document.documentElement.dataset.brand
  delete document.documentElement.dataset.brandMorph
})

it('respects system changes and persists a local reduction on older installs', async () => {
  await db.preferences.update('default', {reduceMotion: undefined})
  const media = Object.assign(new EventTarget(), {matches: false})
  vi.stubGlobal('matchMedia', () => media)
  function ScreenWithTheme() {
    useTheme()
    return <MemoryRouter><Settings/></MemoryRouter>
  }
  const root = document.documentElement
  const page = render(<ScreenWithTheme/>)
  expect(root.dataset.motion).toBe('reduced')
  await waitFor(() => expect(root.dataset.motion).toBe('full'))

  const motion = within(screen.getByRole('group', {name:'Motion'}))
  expect(motion.getByRole('button', {name:'System'})).toHaveAttribute('aria-pressed','true')
  act(() => { media.matches = true; media.dispatchEvent(new Event('change')) })
  expect(root.dataset.motion).toBe('reduced')
  fireEvent.click(motion.getByRole('button', {name:'Reduced'}))
  await waitFor(async () => expect((await db.preferences.get('default'))?.reduceMotion).toBe(true))
  await waitFor(() => expect(motion.getByRole('button',{name:'Reduced'})).toHaveAttribute('aria-pressed','true'))
  act(() => { media.matches = false; media.dispatchEvent(new Event('change')) })
  expect(root.dataset.motion).toBe('reduced')

  page.unmount()
  render(<ScreenWithTheme/>)
  await waitFor(() => expect(within(screen.getByRole('group', {name:'Motion'})).getByRole('button',{name:'Reduced'})).toHaveAttribute('aria-pressed','true'))
  expect(root.dataset.motion).toBe('reduced')
  fireEvent.click(within(screen.getByRole('group', {name:'Motion'})).getByRole('button', {name:'System'}))
  await waitFor(() => expect(root.dataset.motion).toBe('full'))
})

it('edits metric bar and plate presets without changing canonical historical values', async () => {
  const prefs = { ...defaultPreferences, preferredWeightUnit: 'kg' as const }
  await db.preferences.put(prefs)
  render(<EquipmentSettings prefs={prefs}/>)
  fireEvent.click(screen.getByRole('button', {name:/plate calculator setup/i}))
  fireEvent.change(screen.getByRole('combobox', {name:/bar weight/i}), {target:{value:String(toLbs(20,'kg'))}})
  await waitFor(async () => expect((await db.preferences.get('default'))?.barWeight).toBe(toLbs(20,'kg')))
  fireEvent.click(screen.getByRole('button', {name:'25 kg'}))
  await waitFor(async () => {
    const row = await db.preferences.get('default')
    expect(row?.availablePlates).toContain(55.12)
    expect(row?.availablePlates).toContain(45)
  })
})

it('stores the selected home-screen icon and preserves the router path', async () => {
  history.replaceState({}, '', '/settings?test=1')
  await chooseIcon('swole_alligator')
  expect((await db.preferences.get('default'))?.appIcon).toBe('swole_alligator')
  expect(new URL(location.href).searchParams.get('icon')).toBe('swole-alligator')
  expect(iconUrls('swole_alligator').manifest).toBe('/manifest-alligator.webmanifest')
  await chooseIcon('classic')
  expect(location.pathname + location.search).toBe('/settings?test=1')
})

it('morphs the entire theme to UF Swole Gator and cleanly restores Classic', async () => {
  const meta = document.createElement('meta')
  meta.name = 'theme-color'
  document.head.appendChild(meta)
  function ScreenWithTheme() {
    useTheme()
    return <MemoryRouter><Settings/></MemoryRouter>
  }
  render(<ScreenWithTheme/>)
  const root = document.documentElement
  await waitFor(() => expect(root.dataset.brand).toBe('classic'))

  fireEvent.click(screen.getByRole('button', { name: /Swole Gator/i }))
  await waitFor(() => expect(root.dataset.brand).toBe('gator'))
  expect(root.style.getPropertyValue('--accent')).toBe(UF_COLORS.orange)
  expect(meta.getAttribute('content')).toBe(UF_COLORS.swampDarkCanvas)

  fireEvent.click(screen.getByRole('button', { name: /Classic/i }))
  await waitFor(() => expect(root.dataset.brand).toBe('classic'))
  expect(root.style.getPropertyValue('--accent')).toBe('#A3E635')
  expect(meta.getAttribute('content')).toBe('#090A0D')
  meta.remove()
})

it('dynamically swaps both SVG and PNG favicons when switching between Classic and Swole Gator', async () => {
  const svgLink = document.createElement('link')
  svgLink.rel = 'icon'
  svgLink.type = 'image/svg+xml'
  svgLink.href = '/icons/classic.svg'
  const pngLink = document.createElement('link')
  pngLink.rel = 'icon'
  pngLink.type = 'image/png'
  pngLink.href = '/icons/classic-192.png'
  document.head.append(svgLink, pngLink)

  function Probe() {
    useTheme()
    return null
  }

  render(<Probe />)
  await waitFor(() => {
    expect(svgLink.getAttribute('href')).toBe('/icons/classic.svg')
    expect(pngLink.getAttribute('href')).toBe('/icons/classic-192.png')
  })

  await db.preferences.update('default', { appIcon: 'swole_alligator' })
  await waitFor(() => {
    expect(svgLink.getAttribute('href')).toBe('/icons/swole-alligator.svg')
    expect(pngLink.getAttribute('href')).toBe('/icons/swole-alligator-192.png')
  })

  svgLink.remove()
  pngLink.remove()
})

it('shows Swole Gator badge in ThemeSettings preview and locks classic accents with a link to /settings', async () => {
  const { default: ThemeSettings } = await import('../screens/ThemeSettings')
  await db.preferences.put({ ...defaultPreferences, appIcon: 'swole_alligator' })
  const { container } = render(
    <MemoryRouter>
      <ThemeSettings />
    </MemoryRouter>,
  )

  await waitFor(() =>
    expect(
      container.querySelector('[data-mascot="gator"][data-pose="badge"]'),
    ).toBeInTheDocument(),
  )
  const customBtn = screen.getByRole('button', { name: /custom color/i })
  expect(customBtn).toBeDisabled()
  const settingsLink = screen.getByRole('link', { name: /switch back to classic in settings/i })
  expect(settingsLink).toHaveAttribute('href', '/settings')
})

it('shows Swole Chimp badge in ThemeSettings normal mode and both Swole Chimp and Swole Gator options in Settings', async () => {
  const { default: ThemeSettings } = await import('../screens/ThemeSettings')
  await db.preferences.put({ ...defaultPreferences, appIcon: 'classic' })
  const themeView = render(
    <MemoryRouter>
      <ThemeSettings />
    </MemoryRouter>,
  )

  await waitFor(() =>
    expect(
      themeView.container.querySelector('[data-mascot="chimp"][data-pose="badge"]'),
    ).toBeInTheDocument(),
  )
  expect(screen.getByText(/Swole Chimp Dark/i)).toBeInTheDocument()
  expect(screen.getByRole('button', { name: /custom color/i })).not.toBeDisabled()
  themeView.unmount()

  const settingsView = render(
    <MemoryRouter>
      <Settings />
    </MemoryRouter>,
  )
  const chimpOption = screen.getByRole('button', { name: /Swole Chimp/i })
  const gatorOption = screen.getByRole('button', { name: /Swole Gator/i })
  expect(chimpOption).toBeInTheDocument()
  expect(chimpOption).toHaveTextContent(/Classic · Custom accents/i)
  expect(gatorOption).toBeInTheDocument()
  expect(gatorOption).toHaveTextContent(/UF Blue & Orange/i)
  expect(chimpOption.querySelector('[data-mascot="chimp"][data-pose="badge"]')).toBeInTheDocument()
  expect(gatorOption.querySelector('[data-mascot="gator"][data-pose="badge"]')).toBeInTheDocument()
  expect(settingsView.container.querySelector('.st-brand-pill')).toHaveTextContent('Swole Chimp')
})


