import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { SSE_ENDPOINT } from '../api/config'

const HEARTBEAT_TIMEOUT_MS = 60_000
const HEARTBEAT_CHECK_INTERVAL_MS = 10_000

type EventHandler = (data: unknown) => void

interface SSEContextValue {
  isLive: boolean
  subscribe: (eventType: string, handler: EventHandler) => () => void
}

export const SSEContext = createContext<SSEContextValue | null>(null)

export function SSEProvider({ children }: { children: React.ReactNode }) {
  const [isLive, setIsLive] = useState(false)
  const lastPingRef = useRef<number | null>(null)
  const listenersRef = useRef<Map<string, Set<EventHandler>>>(new Map())
  const boundListenersRef = useRef<Map<string, (e: MessageEvent) => void>>(new Map())
  const esRef = useRef<EventSource | null>(null)

  useEffect(() => {
    const es = new EventSource(SSE_ENDPOINT)
    esRef.current = es

    es.onopen = () => {
      setIsLive(true)
      lastPingRef.current = Date.now()
    }

    es.addEventListener('ping', () => {
      lastPingRef.current = Date.now()
      setIsLive(true)
    })

    es.onerror = () => {
      setIsLive(false)
      lastPingRef.current = null
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

  const subscribe = useCallback((eventType: string, handler: EventHandler): () => void => {
    if (!listenersRef.current.has(eventType)) {
      listenersRef.current.set(eventType, new Set())
      const boundListener = (e: MessageEvent) => {
        try {
          const event = JSON.parse(e.data)
          listenersRef.current.get(eventType)?.forEach(h => h(event.payload))
        } catch {
          // ignore unparseable messages
        }
      }
      boundListenersRef.current.set(eventType, boundListener)
      esRef.current?.addEventListener(eventType, boundListener)
    }
    listenersRef.current.get(eventType)!.add(handler)
    return () => {
      const handlers = listenersRef.current.get(eventType)
      handlers?.delete(handler)
      if (handlers?.size === 0) {
        const bound = boundListenersRef.current.get(eventType)
        if (bound) {
          esRef.current?.removeEventListener(eventType, bound)
          boundListenersRef.current.delete(eventType)
          listenersRef.current.delete(eventType)
        }
      }
    }
  }, [])

  const value = useMemo(() => ({ isLive, subscribe }), [isLive, subscribe])

  return (
    <SSEContext.Provider value={value}>
      {children}
    </SSEContext.Provider>
  )
}

export function useSSE(): SSEContextValue {
  const ctx = useContext(SSEContext)
  if (!ctx) throw new Error('useSSE must be used inside SSEProvider')
  return ctx
}
