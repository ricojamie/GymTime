import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, expect, it } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { db } from '../db/database'
import RoutineDay from '../screens/RoutineDay'
import { createRoutine, newPlanRow, saveDay } from './mutations'

beforeEach(async () => { await db.delete(); await db.open() })
afterEach(async () => { cleanup(); await db.delete() })

it('moves a linked pair as a unit and persists it after editing', async () => {
  const routineId = await createRoutine('Base')
  const dayId = await saveDay(routineId,null,'Upper A',[newPlanRow(1),newPlanRow(2),newPlanRow(3)])
  render(<MemoryRouter initialEntries={['/routines/' + routineId + '/days/' + dayId + '/edit']}>
    <Routes><Route path="/routines/:id/days/:dayId/edit" element={<RoutineDay/>}/>
      <Route path="/routines/:id" element={<h1>Saved routine</h1>}/></Routes>
  </MemoryRouter>)
  await screen.findByDisplayValue('Upper A')
  fireEvent.click(screen.getAllByRole('button',{name:'Pair with next'})[0])
  const pair = screen.getByText('Superset 01').closest('section')!
  expect(within(pair).getByText('Barbell Bench Press')).toBeInTheDocument()
  expect(within(pair).getByText('Incline Dumbbell Press')).toBeInTheDocument()
  const moveDown = within(pair).getByRole('button', { name: 'Move Barbell Bench Press down' })
  moveDown.focus()
  fireEvent.click(moveDown)
  expect(within(pair).getByRole('button', { name: 'Move Barbell Bench Press up' })).toHaveFocus()
  fireEvent.click(screen.getByRole('button',{name:'Save day'}))
  await waitFor(() => expect(screen.getByText('Saved routine')).toBeInTheDocument())
  const rows = await db.routine_exercises.where('routineDayId').equals(dayId).sortBy('orderIndex')
  expect(rows.map(row => row.exerciseId)).toEqual([3,1,2])
  expect(rows[0].supersetGroupId).toBeNull()
  expect(rows[1].supersetGroupId).toBeTruthy()
  expect(rows[1].supersetGroupId).toBe(rows[2].supersetGroupId)
})
