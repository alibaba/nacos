import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import path from 'path';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  define: {
    // Polyfill Node.js `process` global for browser-incompatible libs (e.g. swagger2openapi)
    'process.env': {},
    'process.version': JSON.stringify(''),
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 8000,
    proxy: {
      // Auth/admin endpoints → 8848 (keep /nacos prefix, admin server has contextPath=/nacos)
      '/nacos/v1/auth': {
        target: 'http://localhost:8848',
        changeOrigin: true,
      },
      '/nacos/v3/auth': {
        target: 'http://localhost:8848',
        changeOrigin: true,
      },
      // Console endpoints → 8080 (strip /nacos prefix, console server has empty contextPath)
      '/nacos/v1': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/nacos/, ''),
      },
      '/nacos/v2': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/nacos/, ''),
      },
      '/nacos/v3': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/nacos/, ''),
      },
    },
  },
  build: {
    outDir: 'dist',
    rollupOptions: {
      output: {
        entryFileNames: 'js/main.js',
        chunkFileNames: 'js/[name]-[hash].js',
        assetFileNames: (info) => {
          if (info.name?.endsWith('.css')) return 'css/main.css';
          return 'assets/[name]-[hash][extname]';
        },
      },
    },
  },
});
