import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { db } from '../db/database'
import { defaultPreferences } from '../db/types'
import { toLbs } from '../domain/units'
import EquipmentSettings from './EquipmentSettings'
import Settings from '../screens/Settings'
import { chooseIcon, iconUrls, useTheme } from './index'

beforeEach(async () => {
  await db.open()
  await db.preferences.put({ ...defaultPreferences })
})
afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  delete document.documentElement.dataset.motion
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
