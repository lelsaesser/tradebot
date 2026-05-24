import { createContext, useContext, useEffect, useRef, useState } from 'react'

const HEARTBEAT_TIMEOUT_MS = 90_000
const HEARTBEAT_CHECK_INTERVAL_MS = 10_000

type EventHandler = (data: unknown) => void

interface SSEContextValue {
  isLive: boolean
  subscribe: (eventType: string, handler: EventHandler) => () => void
}

const SSEContext = createContext<SSEContextValue | null>(null)

export function SSEProvider({ children }: { children: React.ReactNode }) {
  const [isLive, setIsLive] = useState(false)
  const lastPingRef = useRef<number | null>(null)
  const listenersRef = useRef<Map<string, Set<EventHandler>>>(new Map())
  const esRef = useRef<EventSource | null>(null)

  useEffect(() => {
    const es = new EventSource('/api/events')
    esRef.current = es

    es.addEventListener('ping', () => {
      lastPingRef.current = Date.now()
      setIsLive(true)
    })

    es.onerror = () => {
      setIsLive(false)
      lastPingRef.current = null
    }

    es.onmessage = (e) => {
      try {
        const event = JSON.parse(e.data)
        const handlers = listenersRef.current.get(event.type)
        handlers?.forEach(h => h(event.payload))
      } catch {
        // ignore unparseable messages
      }
    }

    const checkInterval = setInterval(() => {
      if (lastPingRef.current !== null && Date.now() - lastPingRef.current > HEARTBEAT_TIMEOUT_MS) {
        setIsLive(false)
        lastPingRef.current = null
      }
    }, HEARTBEAT_CHECK_INTERVAL_MS)

    return () => {
      es.close()
      clearInterval(checkInterval)
    }
  }, [])

  function subscribe(eventType: string, handler: EventHandler): () => void {
    if (!listenersRef.current.has(eventType)) {
      listenersRef.current.set(eventType, new Set())
      esRef.current?.addEventListener(eventType, (e: MessageEvent) => {
        try {
          const event = JSON.parse(e.data)
          listenersRef.current.get(eventType)?.forEach(h => h(event.payload))
        } catch {
          // ignore
        }
      })
    }
    listenersRef.current.get(eventType)!.add(handler)
    return () => {
      listenersRef.current.get(eventType)?.delete(handler)
    }
  }

  return (
    <SSEContext.Provider value={{ isLive, subscribe }}>
      {children}
    </SSEContext.Provider>
  )
}

export function useSSE(): SSEContextValue {
  const ctx = useContext(SSEContext)
  if (!ctx) throw new Error('useSSE must be used inside SSEProvider')
  return ctx
}
