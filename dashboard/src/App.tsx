import { SSEProvider, useSSE } from './context/SSEProvider'
import { ThemeProvider } from './context/ThemeContext'
import { Header } from './components/Header'
import { WatchlistPanel } from './components/WatchlistPanel'

function Dashboard() {
  const { isLive } = useSSE()
  return (
    <div className="min-h-screen bg-gray-950 text-gray-100">
      <Header isLive={isLive} />
      <main className="p-6">
        <WatchlistPanel />
      </main>
    </div>
  )
}

export function App() {
  return (
    <ThemeProvider>
      <SSEProvider>
        <Dashboard />
      </SSEProvider>
    </ThemeProvider>
  )
}
