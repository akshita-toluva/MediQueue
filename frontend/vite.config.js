import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    // Browser only talks to :5173. Vite forwards these paths to Spring Boot on :8080,
    // so the browser sees ONE origin -> no CORS config needed in the backend.
    proxy: {
      '/api': 'http://localhost:8080',
      // ws: true also forwards the WebSocket upgrade, needed for live queue (session 8)
      '/ws': { target: 'http://localhost:8080', ws: true },
    },
  },
})