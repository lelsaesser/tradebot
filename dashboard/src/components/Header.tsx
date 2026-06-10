import { useSSE } from '../context/SSEProvider'

export function Header() {
  const { isLive } = useSSE()
  return (
    <header className="flex items-center justify-between px-6 py-4 bg-gray-900 border-b border-gray-700">
      <span className="text-white font-semibold text-lg">Tradebot</span>
      {isLive
        ? <span className="text-green-400 text-sm font-medium">● Live</span>
        : <span className="text-red-400 text-sm font-medium">● Disconnected</span>
      }
    </header>
  )
}
