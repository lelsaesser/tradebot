import { SSEProvider } from './context/SSEProvider'
import { ThemeProvider } from './context/ThemeContext'
import { Header } from './components/Header'
import { WatchlistPanel } from './components/WatchlistPanel'

function Dashboard() {
  return (
    <div className="min-h-screen bg-gray-950 text-gray-100">
      <Header />
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
