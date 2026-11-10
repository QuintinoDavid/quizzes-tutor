/// <reference types="vitest" />
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import vuetify from 'vite-plugin-vuetify';
import { fileURLToPath, URL } from 'node:url';

export default defineConfig({
  plugins: [
    vue(),
    vuetify({ autoImport: true }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
      '@assets': fileURLToPath(new URL('./src/assets', import.meta.url)),
      '@models': fileURLToPath(new URL('./src/models', import.meta.url)),
      '@plugins': fileURLToPath(new URL('./src/plugins', import.meta.url)),
      '@services': fileURLToPath(new URL('./src/services', import.meta.url)),
      '@components': fileURLToPath(new URL('./src/components', import.meta.url)),
      '@css': fileURLToPath(new URL('./src/css', import.meta.url)),
      '@views': fileURLToPath(new URL('./src/views', import.meta.url))
    }
  },
  server: {
    port: 8081,
    host: '0.0.0.0'
  },
  envPrefix: 'VUE_APP_',
  // atg reads the source maps to measure the lines of the browser code that the tests run.
  // Rollup writes the sources relative to the folder of each map, so they start with ../, and
  // atg does not count such paths. The transform removes the leading ../ segments.
  build: {
    sourcemap: true,
    rollupOptions: {
      output: {
        sourcemapPathTransform: relativeSourcePath => relativeSourcePath.replace(/^(\.\.\/)+/, ''),
      },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    server: {
      deps: {
        inline: ['vuetify'],
      },
    },
  },
});
