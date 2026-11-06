import { useEffect, useState } from 'react'
import api from './api/client'

export default function App() {
  const [health, setHealth] = useState('checking...')

  useEffect(() => {
    // Relative URL: goes to :5173, which the Vite proxy forwards to :8080
    api.get('/api/health')
      .then(res => setHealth(JSON.stringify(res.data)))
      .catch(err => setHealth('Backend not reachable: ' + err.message))
  }, [])

  return (
    <div className="min-h-screen bg-slate-50 flex items-center justify-center">
      <div className="bg-white shadow rounded-xl p-8">
        <h1 className="text-2xl font-bold text-blue-700">MediQueue</h1>
        <p className="mt-2 text-slate-600">Backend health: {health}</p>
      </div>
    </div>
  )
}