import { useEffect, useRef, useState } from 'react'
import { useSSE } from '../context/SSEProvider'
import { API_BASE } from '../api/config'

interface WatchlistRow {
  ticker: string
  displayName: string
  exchange: string
  currentPrice: number | null
  buyTarget: number | null
  sellTarget: number | null
}

interface WatchlistResponse {
  symbols: WatchlistRow[]
}

type AlertSide = 'BUY' | 'SELL'

interface PriceAlertPayload {
  ticker: string
  side: AlertSide
  currentPrice: number
  target: number
}

interface PriceSwingPayload {
  ticker: string
  changePercent: number
}

function ExchangeBadge({ exchange }: { exchange: string }) {
  const colours: Record<string, string> = {
    US: 'bg-blue-900 text-blue-200',
    XETRA: 'bg-green-900 text-green-200',
    KRX: 'bg-purple-900 text-purple-200',
    JPX: 'bg-pink-900 text-pink-200',
    STO: 'bg-cyan-900 text-cyan-200',
    PAR: 'bg-indigo-900 text-indigo-200',
    ETF: 'bg-yellow-900 text-yellow-200',
  }
  const cls = colours[exchange] ?? 'bg-gray-700 text-gray-200'
  return (
    <span className={`px-1.5 py-0.5 rounded text-xs font-mono ${cls}`}>
      {exchange}
    </span>
  )
}

function fmt(n: number | null): string {
  if (n === null || n === undefined) return '—'
  return n.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

export function WatchlistPanel() {
  const { subscribe } = useSSE()
  const [rows, setRows] = useState<WatchlistRow[]>([])
  const [highlighted, setHighlighted] = useState<Record<string, AlertSide>>({})
  const [swings, setSwings] = useState<Record<string, number>>({})
  const [addTicker, setAddTicker] = useState('')
  const [addName, setAddName] = useState('')
  const [addError, setAddError] = useState<string | null>(null)
  const [mutationError, setMutationError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const highlightTimers = useRef<Record<string, ReturnType<typeof setTimeout>>>({})

  async function loadWatchlist() {
    try {
      const res = await fetch(`${API_BASE}/watchlist`)
      if (!res.ok) throw new Error('Failed to load watchlist')
      const data: WatchlistResponse = await res.json()
      setRows(data.symbols)
    } catch {
      // keep stale data
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadWatchlist()

    const unsubAlert = subscribe('price-alert', (data: unknown) => {
      const p = data as PriceAlertPayload
      setHighlighted(prev => ({ ...prev, [p.ticker]: p.side }))
      if (highlightTimers.current[p.ticker]) clearTimeout(highlightTimers.current[p.ticker])
      highlightTimers.current[p.ticker] = setTimeout(() => {
        setHighlighted(prev => {
          const next = { ...prev }
          delete next[p.ticker]
          return next
        })
      }, 10_000)
    })

    const unsubSwing = subscribe('price-swing', (data: unknown) => {
      const p = data as PriceSwingPayload
      setSwings(prev => ({ ...prev, [p.ticker]: p.changePercent }))
    })

    return () => {
      unsubAlert()
      unsubSwing()
    }
  }, [subscribe])

  async function handleAdd(e: React.FormEvent) {
    e.preventDefault()
    setAddError(null)
    const res = await fetch(`${API_BASE}/symbols`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ticker: addTicker.toUpperCase(), displayName: addName }),
    })
    if (!res.ok) {
      setAddError(await res.text())
      return
    }
    setAddTicker('')
    setAddName('')
    await loadWatchlist()
  }

  async function handleRemove(ticker: string) {
    setMutationError(null)
    const res = await fetch(`${API_BASE}/symbols/${ticker}`, { method: 'DELETE' })
    if (!res.ok) {
      setMutationError(`Failed to remove ${ticker}.`)
      return
    }
    setRows(prev => prev.filter(r => r.ticker !== ticker))
  }

  async function handleSetTarget(ticker: string, side: AlertSide, price: number) {
    setMutationError(null)
    const res = await fetch(`${API_BASE}/targets`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ticker, side, price }),
    })
    if (!res.ok) {
      setMutationError(`Failed to set ${side.toLowerCase()} target for ${ticker}.`)
      return
    }
    const newTarget = price === 0 ? null : price
    setRows(prev =>
      prev.map(r => {
        if (r.ticker !== ticker) return r
        return side === 'BUY' ? { ...r, buyTarget: newTarget } : { ...r, sellTarget: newTarget }
      }),
    )
  }

  return (
    <section className="mt-6">
      <h2 className="text-lg font-semibold mb-3 text-gray-100">Watchlist</h2>

      {/* Add symbol form */}
      <form onSubmit={handleAdd} className="flex gap-2 mb-4 flex-wrap">
        <input
          className="bg-gray-800 border border-gray-700 rounded px-2 py-1 text-sm text-gray-100 w-28"
          placeholder="Ticker"
          value={addTicker}
          onChange={e => setAddTicker(e.target.value)}
          required
        />
        <input
          className="bg-gray-800 border border-gray-700 rounded px-2 py-1 text-sm text-gray-100 flex-1 min-w-40"
          placeholder="Display name"
          value={addName}
          onChange={e => setAddName(e.target.value)}
          required
        />
        <button
          type="submit"
          className="bg-blue-700 hover:bg-blue-600 text-white text-sm px-3 py-1 rounded"
        >
          Add
        </button>
        {addError && <p className="w-full text-red-400 text-xs mt-1">{addError}</p>}
      </form>

      {mutationError && (
        <p className="text-red-400 text-xs mb-2">{mutationError}</p>
      )}

      {/* Table */}
      {loading ? (
        <p className="text-gray-500 text-sm">Loading…</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm text-left">
            <thead>
              <tr className="text-gray-400 border-b border-gray-800">
                <th className="pb-2 pr-4">Symbol</th>
                <th className="pb-2 pr-4">Name</th>
                <th className="pb-2 pr-4">Exchange</th>
                <th className="pb-2 pr-4 text-right">Price</th>
                <th className="pb-2 pr-4 text-right">Buy target</th>
                <th className="pb-2 pr-4 text-right">Sell target</th>
                <th className="pb-2" />
              </tr>
            </thead>
            <tbody>
              {rows.map(row => {
                const alertSide = highlighted[row.ticker]
                const swing = swings[row.ticker]
                const rowBg =
                  alertSide === 'BUY'
                    ? 'bg-green-900/30'
                    : alertSide === 'SELL'
                      ? 'bg-red-900/30'
                      : ''
                return (
                  <tr key={row.ticker} className={`border-b border-gray-800/60 ${rowBg} transition-colors`}>
                    <td className="py-2 pr-4 font-mono text-gray-100">
                      {row.ticker}
                      {swing !== undefined && (
                        <span
                          className={`ml-1 text-xs ${swing > 0 ? 'text-green-400' : 'text-red-400'}`}
                        >
                          {swing > 0 ? '▲' : '▼'}{Math.abs(swing).toFixed(1)}%
                        </span>
                      )}
                    </td>
                    <td className="py-2 pr-4 text-gray-300">{row.displayName}</td>
                    <td className="py-2 pr-4">
                      <ExchangeBadge exchange={row.exchange} />
                    </td>
                    <td className="py-2 pr-4 text-right font-mono text-gray-100">
                      {fmt(row.currentPrice)}
                    </td>
                    <td className="py-2 pr-4 text-right">
                      <TargetCell
                        value={row.buyTarget}
                        onSet={price => handleSetTarget(row.ticker, 'BUY', price)}
                      />
                    </td>
                    <td className="py-2 pr-4 text-right">
                      <TargetCell
                        value={row.sellTarget}
                        onSet={price => handleSetTarget(row.ticker, 'SELL', price)}
                      />
                    </td>
                    <td className="py-2">
                      <button
                        onClick={() => handleRemove(row.ticker)}
                        className="text-gray-600 hover:text-red-400 text-xs"
                        title="Remove"
                      >
                        ✕
                      </button>
                    </td>
                  </tr>
                )
              })}
              {rows.length === 0 && (
                <tr>
                  <td colSpan={7} className="py-4 text-gray-500 text-center">
                    No symbols yet.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

function TargetCell({
  value,
  onSet,
}: {
  value: number | null
  onSet: (price: number) => void
}) {
  const [editing, setEditing] = useState(false)
  const [input, setInput] = useState('')

  function commit() {
    const parsed = parseFloat(input)
    if (!isNaN(parsed) && parsed >= 0) onSet(parsed)
    setEditing(false)
    setInput('')
  }

  if (editing) {
    return (
      <input
        autoFocus
        className="bg-gray-800 border border-gray-600 rounded px-1 py-0.5 text-xs font-mono w-24 text-right"
        value={input}
        onChange={e => setInput(e.target.value)}
        onBlur={commit}
        onKeyDown={e => {
          if (e.key === 'Enter') commit()
          if (e.key === 'Escape') setEditing(false)
        }}
      />
    )
  }

  return (
    <button
      onClick={() => { setEditing(true); setInput(value?.toString() ?? '') }}
      className="font-mono text-gray-300 hover:text-white hover:underline"
      title="Click to edit"
    >
      {fmt(value)}
    </button>
  )
}
