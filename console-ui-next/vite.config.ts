/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import path from 'path';

const isTest = !!process.env.VITEST;
const objectHasOwnPolyfillBanner =
  "if(typeof Object.hasOwn!=='function'){Object.defineProperty(Object,'hasOwn',{configurable:true,writable:true,value:function(o,p){return Object.prototype.hasOwnProperty.call(Object(o),p);}});}";

export default defineConfig(({ command }) => ({
  base: command === 'build' ? './' : '/',
  plugins: [react(), tailwindcss()],
  test: {
    globals: true,
    environment: 'node',
  },
  define: isTest
    ? {}
    : {
        // Polyfill Node.js `process` global for browser-incompatible libs (e.g. swagger2openapi)
        'process.env': {},
        'process.version': JSON.stringify(''),
      },
  resolve: {
    alias: [
      { find: '@', replacement: path.resolve(__dirname, './src') },
      {
        // Monaco embeds DOMPurify, so the npm override alone does not patch its runtime.
        // Route its sanitizer to the patched package (GHSA-55q2-fjhq-7xh7).
        find: './dompurify/dompurify.js',
        replacement: path.resolve(__dirname, './node_modules/dompurify/dist/purify.es.mjs'),
      },
    ],
  },
  server: {
    port: 8000,
    proxy: {
      // Auth/admin endpoints → 8848 (server has contextPath=/nacos, so add prefix)
      '/v1/auth': {
        target: 'http://localhost:8848',
        changeOrigin: true,
        rewrite: (path) => `/nacos${path}`,
      },
      '/v3/auth': {
        target: 'http://localhost:8848',
        changeOrigin: true,
        rewrite: (path) => `/nacos${path}`,
      },
      // Console endpoints → 8080 (console has empty contextPath, no rewrite needed)
      '/v1': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/v2': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/v3': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  worker: {
    rolldownOptions: {
      output: {
        banner: objectHasOwnPolyfillBanner,
      },
    },
  },
  build: {
    // Lower Chrome to 90 for #15899, but keep Vite's other browser targets so
    // CSS still emits Safari -webkit-* prefixes (cssTarget inherits build.target).
    target: ['chrome90', 'edge111', 'firefox114', 'safari16.4', 'ios16.4'],
    cssTarget: ['chrome90', 'edge111', 'firefox114', 'safari16.4', 'ios16.4'],
    outDir: 'dist',
    rollupOptions: {
      output: {
        // Workers use their own build options and receive the same banner above.
        banner: objectHasOwnPolyfillBanner,
        entryFileNames: 'js/main.js',
        chunkFileNames: 'js/[name].js',
        assetFileNames: (info) => {
          if (info.name?.endsWith('.css')) return 'css/main.css';
          return 'assets/[name]-[hash][extname]';
        },
        manualChunks(id) {
          // Merge all lucide-react icons into one chunk
          if (id.includes('lucide-react')) {
            return 'vendor-icons';
          }
          // Merge Monaco Editor core + languages into one chunk
          if (id.includes('monaco-editor')) {
            return 'vendor-monaco';
          }
          // Merge major vendor libs
          if (id.includes('node_modules')) {
            if (id.includes('react-dom')) return 'vendor-react';
            if (id.includes('/react/') || id.includes('react-router') || id.includes('react-i18next') || id.includes('i18next')) return 'vendor-react';
            if (id.includes('@radix-ui') || id.includes('class-variance-authority') || id.includes('clsx') || id.includes('tailwind-merge')) return 'vendor-ui';
            if (id.includes('react-markdown') || id.includes('remark') || id.includes('rehype') || id.includes('unified') || id.includes('mdast') || id.includes('hast') || id.includes('micromark') || id.includes('@uiw/react-md-editor')) return 'vendor-markdown';
          }
        },
      },
    },
  },
}));
