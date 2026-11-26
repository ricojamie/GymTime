import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig({
  base: '/',
  plugins: [
    react(),
    tailwindcss(),
    VitePWA({
      registerType: 'prompt',
      injectRegister: null,
      manifest: false,
      workbox: {
        cacheId: 'ironlog',
        clientsClaim: true,
        cleanupOutdatedCaches: true,
        navigateFallback: '/index.html',
        navigateFallbackDenylist: [/^\/assets\//, /^\/icons\//, /\.webmanifest$/],
        globPatterns: ['**/*.{html,js,css,woff2,png,svg,webmanifest}'],
      },
    }),
  ],
  build: {
    target: ['es2022', 'safari16.4'],
    rollupOptions: {
      output: {
        manualChunks: {
          framework: ['react', 'react-dom', 'react-router-dom'],
          database: ['dexie', 'dexie-react-hooks'],
        },
      },
    },
  },
})
