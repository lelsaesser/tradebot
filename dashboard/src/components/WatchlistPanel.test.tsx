import { render, screen, fireEvent, waitFor, act } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { WatchlistPanel } from './WatchlistPanel'
import { SSEContext } from '../context/SSEProvider'

type SseHandler = (data: unknown) => void

interface MockSSE {
  isLive: boolean
  subscribe: (event: string, handler: SseHandler) => () => void
  emit: (event: string, data: unknown) => void
}

function createMockSSE(): MockSSE {
  const handlers: Record<string, SseHandler[]> = {}
  return {
    isLive: true,
    subscribe(event, handler) {
      handlers[event] = handlers[event] ?? []
      handlers[event].push(handler)
      return () => {
        handlers[event] = (handlers[event] ?? []).filter(h => h !== handler)
      }
    },
    emit(event, data) {
      ;(handlers[event] ?? []).forEach(h => h(data))
    },
  }
}

function renderPanel(sse: MockSSE = createMockSSE()) {
  return render(
    <SSEContext.Provider value={{ isLive: sse.isLive, subscribe: sse.subscribe }}>
      <WatchlistPanel />
    </SSEContext.Provider>,
  )
}

const sampleResponse = {
  symbols: [
    {
      ticker: 'AAPL',
      displayName: 'Apple Inc',
      exchange: 'US',
      currentPrice: 182.5,
      buyTarget: 170,
      sellTarget: 200,
    },
    {
      ticker: 'SAP.DE',
      displayName: 'SAP SE',
      exchange: 'XETRA',
      currentPrice: 175,
      buyTarget: null,
      sellTarget: null,
    },
  ],
}

function mockFetchSequence(...responses: Array<{ ok: boolean; body?: unknown; text?: string }>) {
  const fn = vi.fn()
  responses.forEach(r => {
    fn.mockResolvedValueOnce({
      ok: r.ok,
      json: async () => r.body,
      text: async () => r.text ?? '',
    })
  })
  globalThis.fetch = fn as unknown as typeof fetch
  return fn
}

describe('WatchlistPanel', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('renders rows after initial load', async () => {
    mockFetchSequence({ ok: true, body: sampleResponse })
    renderPanel()

    expect(await screen.findByText('AAPL')).toBeInTheDocument()
    expect(screen.getByText('Apple Inc')).toBeInTheDocument()
    expect(screen.getByText('SAP.DE')).toBeInTheDocument()
    expect(screen.getByText('US')).toBeInTheDocument()
    expect(screen.getByText('XETRA')).toBeInTheDocument()
  })

  it('renders em dash for missing targets', async () => {
    mockFetchSequence({ ok: true, body: sampleResponse })
    renderPanel()

    await screen.findByText('SAP.DE')
    // SAP has null buy and sell targets — at least two em dashes should be present
    const dashes = screen.getAllByText('—')
    expect(dashes.length).toBeGreaterThanOrEqual(2)
  })

  it('shows empty state when no symbols', async () => {
    mockFetchSequence({ ok: true, body: { symbols: [] } })
    renderPanel()

    expect(await screen.findByText('No symbols yet.')).toBeInTheDocument()
  })

  it('adds a symbol and reloads on success', async () => {
    const fetchMock = mockFetchSequence(
      { ok: true, body: { symbols: [] } },
      { ok: true, text: 'Added' },
      { ok: true, body: sampleResponse },
    )

    renderPanel()
    await screen.findByText('No symbols yet.')

    fireEvent.change(screen.getByPlaceholderText('Ticker'), { target: { value: 'aapl' } })
    fireEvent.change(screen.getByPlaceholderText('Display name'), { target: { value: 'Apple Inc' } })
    fireEvent.click(screen.getByRole('button', { name: 'Add' }))

    await screen.findByText('AAPL')
    // The POST body should have uppercased the ticker.
    const postCall = fetchMock.mock.calls[1]
    expect(postCall[0]).toBe('/api/v1/symbols')
    expect(JSON.parse(postCall[1].body)).toEqual({ ticker: 'AAPL', displayName: 'Apple Inc' })
  })

  it('shows error text when add fails', async () => {
    mockFetchSequence(
      { ok: true, body: { symbols: [] } },
      { ok: false, text: 'Invalid ticker: XX.' },
    )

    renderPanel()
    await screen.findByText('No symbols yet.')

    fireEvent.change(screen.getByPlaceholderText('Ticker'), { target: { value: 'XX' } })
    fireEvent.change(screen.getByPlaceholderText('Display name'), { target: { value: 'Bad' } })
    fireEvent.click(screen.getByRole('button', { name: 'Add' }))

    expect(await screen.findByText('Invalid ticker: XX.')).toBeInTheDocument()
  })

  it('removes a row optimistically on success', async () => {
    mockFetchSequence(
      { ok: true, body: sampleResponse },
      { ok: true },
    )

    renderPanel()
    await screen.findByText('AAPL')

    fireEvent.click(screen.getAllByTitle('Remove')[0])

    await waitFor(() => expect(screen.queryByText('AAPL')).not.toBeInTheDocument())
    expect(screen.getByText('SAP.DE')).toBeInTheDocument()
  })

  it('surfaces error when remove fails', async () => {
    mockFetchSequence(
      { ok: true, body: sampleResponse },
      { ok: false },
    )

    renderPanel()
    await screen.findByText('AAPL')

    fireEvent.click(screen.getAllByTitle('Remove')[0])

    expect(await screen.findByText(/Failed to remove AAPL/)).toBeInTheDocument()
    // Row still present because the DELETE failed.
    expect(screen.getByText('AAPL')).toBeInTheDocument()
  })

  it('edits a buy target and updates optimistically', async () => {
    const fetchMock = mockFetchSequence(
      { ok: true, body: sampleResponse },
      { ok: true },
    )

    renderPanel()
    await screen.findByText('AAPL')

    // Find the buy target button (shows '170.00')
    const buyButton = screen.getByText('170.00')
    fireEvent.click(buyButton)

    const input = screen.getByDisplayValue('170')
    fireEvent.change(input, { target: { value: '165' } })
    fireEvent.keyDown(input, { key: 'Enter' })

    const postCall = fetchMock.mock.calls[1]
    expect(postCall[0]).toBe('/api/v1/targets')
    expect(JSON.parse(postCall[1].body)).toEqual({ ticker: 'AAPL', side: 'BUY', price: 165 })

    expect(await screen.findByText('165.00')).toBeInTheDocument()
  })

  it('clears a target when 0 is entered (renders em dash)', async () => {
    const fetchMock = mockFetchSequence(
      { ok: true, body: sampleResponse },
      { ok: true },
    )

    renderPanel()
    await screen.findByText('AAPL')

    const buyButton = screen.getByText('170.00')
    fireEvent.click(buyButton)
    const input = screen.getByDisplayValue('170')
    fireEvent.change(input, { target: { value: '0' } })
    fireEvent.keyDown(input, { key: 'Enter' })

    const postCall = fetchMock.mock.calls[1]
    expect(JSON.parse(postCall[1].body)).toEqual({ ticker: 'AAPL', side: 'BUY', price: 0 })

    // Old "170.00" should be gone; em dash present.
    await waitFor(() => expect(screen.queryByText('170.00')).not.toBeInTheDocument())
  })

  it('rejects negative target input (no fetch)', async () => {
    const fetchMock = mockFetchSequence({ ok: true, body: sampleResponse })

    renderPanel()
    await screen.findByText('AAPL')

    const buyButton = screen.getByText('170.00')
    fireEvent.click(buyButton)
    const input = screen.getByDisplayValue('170')
    fireEvent.change(input, { target: { value: '-5' } })
    fireEvent.keyDown(input, { key: 'Enter' })

    // Only the initial GET was made.
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('cancels target edit on Escape', async () => {
    mockFetchSequence({ ok: true, body: sampleResponse })

    renderPanel()
    await screen.findByText('AAPL')

    const buyButton = screen.getByText('170.00')
    fireEvent.click(buyButton)
    const input = screen.getByDisplayValue('170')
    fireEvent.keyDown(input, { key: 'Escape' })

    expect(screen.queryByDisplayValue('170')).not.toBeInTheDocument()
    expect(screen.getByText('170.00')).toBeInTheDocument()
  })

  it('surfaces error when set-target fails', async () => {
    mockFetchSequence(
      { ok: true, body: sampleResponse },
      { ok: false },
    )

    renderPanel()
    await screen.findByText('AAPL')

    const buyButton = screen.getByText('170.00')
    fireEvent.click(buyButton)
    const input = screen.getByDisplayValue('170')
    fireEvent.change(input, { target: { value: '165' } })
    fireEvent.keyDown(input, { key: 'Enter' })

    expect(await screen.findByText(/Failed to set buy target for AAPL/)).toBeInTheDocument()
    // Original target still shown — optimistic update was skipped.
    expect(screen.getByText('170.00')).toBeInTheDocument()
  })

  it('highlights row on incoming price-alert SSE event', async () => {
    mockFetchSequence({ ok: true, body: sampleResponse })
    const sse = createMockSSE()
    renderPanel(sse)

    await screen.findByText('AAPL')

    act(() => {
      sse.emit('price-alert', {
        ticker: 'AAPL',
        side: 'SELL',
        currentPrice: 205,
        target: 200,
      })
    })

    // The row should have the SELL highlight class applied.
    const row = screen.getByText('AAPL').closest('tr')
    expect(row?.className).toContain('bg-red-900/30')
  })

  it('renders price swing indicator on price-swing event', async () => {
    mockFetchSequence({ ok: true, body: sampleResponse })
    const sse = createMockSSE()
    renderPanel(sse)

    await screen.findByText('AAPL')

    act(() => {
      sse.emit('price-swing', { ticker: 'AAPL', changePercent: 6.4 })
    })

    expect(await screen.findByText(/▲6\.4%/)).toBeInTheDocument()
  })
})
