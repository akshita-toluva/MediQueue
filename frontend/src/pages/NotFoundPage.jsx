import { Link } from 'react-router'

export default function NotFoundPage() {
  return (
    <div className="min-h-screen flex flex-col items-center justify-center bg-slate-50">
      <h1 className="text-3xl font-bold text-slate-800">Page not found</h1>
      <Link to="/" className="mt-4 text-blue-600 hover:underline">Go home</Link>
    </div>
  )
}