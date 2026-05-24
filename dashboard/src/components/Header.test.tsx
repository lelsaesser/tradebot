import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { Header } from './Header'

describe('Header', () => {
  it('renders Live indicator when connected', () => {
    render(<Header isLive={true} />)
    expect(screen.getByText(/Live/)).toBeInTheDocument()
    expect(screen.queryByText(/Disconnected/)).not.toBeInTheDocument()
  })

  it('renders Disconnected indicator when not connected', () => {
    render(<Header isLive={false} />)
    expect(screen.getByText(/Disconnected/)).toBeInTheDocument()
    expect(screen.queryByText(/Live/)).not.toBeInTheDocument()
  })
})
