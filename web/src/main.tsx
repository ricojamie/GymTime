import { createRoot } from 'react-dom/client'
import App from './App'
import { initializeDatabase } from './db/database'
import './styles/fonts.css'
import './styles/global.css'

const root = createRoot(document.getElementById('root')!)
navigator.storage?.persist?.().catch(() => {})
initializeDatabase().then(() => root.render(<App/>)).catch(() => root.render(
  <main className="app-error" role="alert"><h1>Local storage couldn’t open</h1><p>Check that browser storage is available, then reload IronLog.</p><button className="btn primary" onClick={()=>window.location.reload()}>Try again</button></main>
))
