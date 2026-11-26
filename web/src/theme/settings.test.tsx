import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, expect, it } from 'vitest'
import { db } from '../db/database'
import { defaultPreferences } from '../db/types'
import { toLbs } from '../domain/units'
import EquipmentSettings from './EquipmentSettings'
import { chooseIcon, iconUrls } from './index'

beforeEach(async () => {
  await db.open()
  await db.preferences.put({ ...defaultPreferences })
})
afterEach(cleanup)

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
