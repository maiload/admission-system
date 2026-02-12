import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 3000,
    proxy: {
      '/gate': {
        target: 'http://localhost:8010',
        changeOrigin: true,
      },
      '/api': {
        target: 'http://localhost:8030',
        changeOrigin: true,
      },
    },
  },
})
