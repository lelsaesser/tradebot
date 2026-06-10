import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { Header } from './Header'
import { SSEContext } from '../context/SSEProvider'

function renderHeader(isLive: boolean) {
  render(
    <SSEContext.Provider value={{ isLive, subscribe: () => () => {} }}>
      <Header />
    </SSEContext.Provider>
  )
}

describe('Header', () => {
  it('renders Live indicator when connected', () => {
    renderHeader(true)
    expect(screen.getByText(/Live/)).toBeInTheDocument()
    expect(screen.queryByText(/Disconnected/)).not.toBeInTheDocument()
  })

  it('renders Disconnected indicator when not connected', () => {
    renderHeader(false)
    expect(screen.getByText(/Disconnected/)).toBeInTheDocument()
    expect(screen.queryByText(/Live/)).not.toBeInTheDocument()
  })
})
