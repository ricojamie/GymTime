import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { configure, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { db } from '../db/database'
import { groupWorkoutExercises, startWorkout, updatePreferences } from '../db/repositories'
import Logger from './Logger'

beforeAll(() => {
  configure({asyncUtilTimeout: 5000, getElementError: (message, container) => new Error((message ?? '') + '\n' + container.textContent)})
  if (!HTMLDialogElement.prototype.showModal) HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', '') }
  if (!HTMLDialogElement.prototype.close) HTMLDialogElement.prototype.close = function () { this.removeAttribute('open') }
  if (!HTMLElement.prototype.scrollIntoView) HTMLElement.prototype.scrollIntoView = vi.fn()
})
beforeEach(async () => {
  await db.open()
  await updatePreferences({timerAutoStart:false, preferredWeightUnit:'kg'})
})
afterEach(async () => { cleanup(); await db.delete() })

async function launch() {
  const exercise = await db.exercises.where('name').equals('Barbell Bench Press').first()
  const wid = await startWorkout({exerciseIds:[exercise!.id]})
  render(<MemoryRouter initialEntries={['/log/' + exercise!.id + '?workoutId=' + wid]}><Routes><Route path="/log/:exerciseId" element={<Logger />} /></Routes></MemoryRouter>)
  await screen.findByRole('heading', {name:'Barbell Bench Press'})
  return wid
}
function enter(field: string, keys: string, close = true) {
  fireEvent.click(screen.getByRole('button', {name: new RegExp('^Enter ' + field + ',')}))
  const sheet = within(screen.getByRole('dialog'))
  for (const char of keys) fireEvent.click(sheet.getByRole('button', {name:char}))
  if (close) fireEvent.click(sheet.getByRole('button', {name:'Close'}))
  return sheet
}

describe('logger', {timeout: 30000}, () => {
  it('logs from the keypad, retains core metrics and restores a draft after editing a previous set', async () => {
    const wid = await launch()
    enter('weight','20')
    const sheet = enter('reps','8',false)
    fireEvent.click(sheet.getByRole('button',{name:'Log set'}))
    await waitFor(async () => { const rows = await db.sets.where('workoutId').equals(wid).toArray(); expect(rows.length, document.body.textContent ?? '').toBeGreaterThan(0) })
    await screen.findByRole('button',{name:/^Edit set 1,/})
    const set = await db.sets.where('workoutId').equals(wid).first()
    expect(set?.weight).toBeCloseTo(44.0924,6)
    expect(set?.reps).toBe(8)
    expect(screen.getByRole('button',{name:/^Enter weight, 20 kg/})).toBeInTheDocument()
    enter('weight','22.5')
    fireEvent.click(screen.getByRole('button',{name:'RPE / Note'}))
    fireEvent.change(screen.getByLabelText('Set note'),{target:{value:'Next set draft'}})
    fireEvent.click(screen.getByRole('button',{name:/^Edit set 1,/}))
    enter('reps','9')
    fireEvent.click(screen.getByRole('button',{name:'Save changes'}))
    await waitFor(() => expect(screen.getByRole('button',{name:/^Enter weight, 22.5 kg/})).toBeInTheDocument())
    expect((await db.sets.get(set!.id))?.reps).toBe(9)
    fireEvent.click(screen.getByRole('button',{name:'RPE / Note'}))
    expect(screen.getByLabelText('Set note')).toHaveValue('Next set draft')
    fireEvent.click(screen.getByRole('button',{name:'Log set'}))
    await screen.findByRole('button',{name:/^Edit set 2,/})
    expect(screen.getByLabelText('Set note')).toHaveValue('')
    expect(await db.sets.where('workoutId').equals(wid).count()).toBe(2)
  })
  it('rotates a superset after logging and permits exiting without changing the saved plan', async () => {
    const first = (await db.exercises.where('name').equals('Push-Ups').first())!
    const second = (await db.exercises.where('name').equals('Pull-Ups').first())!
    const wid = await startWorkout({exerciseIds:[first.id,second.id]})
    await groupWorkoutExercises(wid,[first.id,second.id])
    render(<MemoryRouter initialEntries={['/log/' + first.id + '?workoutId=' + wid]}><Routes><Route path="/log/:exerciseId" element={<Logger />} /></Routes></MemoryRouter>)
    await screen.findByRole('heading',{name:'Push-Ups'})
    enter('reps','8')
    fireEvent.click(screen.getByRole('button',{name:'Log set'}))
    await screen.findByRole('heading',{name:'Pull-Ups'})
    fireEvent.click(screen.getByRole('button',{name:'Exit superset'}))
    expect(screen.getByRole('button',{name:'Resume superset'})).toBeInTheDocument()
    const members = await db.workout_exercise_instances.where('workoutId').equals(wid).toArray()
    expect(members.every(p => !!p.supersetGroupId && p.supersetGroupId === members[0].supersetGroupId)).toBe(true)
    enter('reps','6')
    fireEvent.click(screen.getByRole('button',{name:'Log set'}))
    await waitFor(async () => { const rows = await db.sets.where('workoutId').equals(wid).toArray(); expect(rows.length, document.body.textContent ?? '').toBeGreaterThan(0) })
    await screen.findByRole('button',{name:/^Edit set 1,/})
    expect(screen.getByRole('heading',{name:'Pull-Ups'})).toBeInTheDocument()
    const s = await db.sets.where('exerciseId').equals(second.id).first()
    expect(s?.supersetGroupId).toBeNull()
  })
})
