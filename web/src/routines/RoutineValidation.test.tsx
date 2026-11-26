import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { db } from '../db/database'
import RoutineDetail from '../screens/RoutineDetail'
import RoutineDay from '../screens/RoutineDay'
import RoutineStart from '../screens/RoutineStart'
import Routines from '../screens/Routines'

beforeEach(async () => {
  await db.open()
})

afterEach(() => {
  cleanup()
})

describe('Invalid routine ID validation', () => {
  it('handles non-numeric and non-positive IDs on RoutineDetail without throwing', async () => {
    for (const badId of ['abc', '0', '-4']) {
      render(
        <MemoryRouter initialEntries={[`/routines/${badId}`]}>
          <Routes>
            <Route path="/routines/:id" element={<RoutineDetail />} />
          </Routes>
        </MemoryRouter>,
      )
      expect(await screen.findByText(/routine unavailable|not found/i)).toBeInTheDocument()
      cleanup()
    }
  })

  it('handles invalid routine/day IDs on RoutineDay without throwing', async () => {
    render(
      <MemoryRouter initialEntries={['/routines/not-a-number/days/0']}>
        <Routes>
          <Route path="/routines/:id/days/:dayId" element={<RoutineDay />} />
        </Routes>
      </MemoryRouter>,
    )
    expect(await screen.findByText(/unavailable|not found/i)).toBeInTheDocument()
  })

  it('handles invalid routine IDs on RoutineStart without throwing', async () => {
    render(
      <MemoryRouter initialEntries={['/routines/-9/start']}>
        <Routes>
          <Route path="/routines/:id/start" element={<RoutineStart />} />
        </Routes>
      </MemoryRouter>,
    )
    expect(await screen.findByText(/unavailable|not found/i)).toBeInTheDocument()
  })

  it('handles invalid edit routine ID on Routines edit route without throwing', async () => {
    render(
      <MemoryRouter initialEntries={['/routines/NaN/edit']}>
        <Routes>
          <Route path="/routines/:id/edit" element={<Routines />} />
        </Routes>
      </MemoryRouter>,
    )
    expect(await screen.findByText(/unavailable|not found/i)).toBeInTheDocument()
  })
})
