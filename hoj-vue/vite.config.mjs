import { readFileSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath, URL } from 'node:url'
import { gzipSync } from 'node:zlib'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

function staticAssetsPlugin() {
  return {
    name: 'hoj-favicon',
    generateBundle() {
      this.emitFile({
        type: 'asset',
        fileName: 'favicon.ico',
        source: readFileSync(fileURLToPath(new URL('./public/favicon.ico', import.meta.url)))
      })
    }
  }
}

function gzipAssetsPlugin() {
  return {
    name: 'hoj-gzip-assets',
    apply: 'build',
    writeBundle(outputOptions, bundle) {
      const outputDirectory = resolve(outputOptions.dir || 'dist')
      for (const output of Object.values(bundle)) {
        if (!/\.(js|css|html|svg|json)$/i.test(output.fileName)) {
          continue
        }
        const outputPath = resolve(outputDirectory, output.fileName)
        const source = readFileSync(outputPath)
        if (source.length >= 10240) {
          writeFileSync(`${outputPath}.gz`, gzipSync(source, { level: 9 }))
        }
      }
    }
  }
}

export default defineConfig({
  plugins: [
    vue(),
    staticAssetsPlugin(),
    gzipAssetsPlugin()
  ],
  publicDir: false,
  resolve: {
    extensions: ['.mjs', '.js', '.mts', '.ts', '.jsx', '.tsx', '.json', '.vue'],
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    host: '0.0.0.0',
    proxy: {
      '/api': {
        target: 'http://localhost:6688',
        changeOrigin: true
      }
    }
  },
  preview: {
    host: '0.0.0.0'
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    chunkSizeWarningLimit: 1500,
    rollupOptions: {
      output: {
        onlyExplicitManualChunks: true,
        manualChunks(id) {
          if (!id.includes('node_modules')) {
            return undefined
          }
          if (id.includes('element-plus')) {
            return 'vendor-element-plus'
          }
          if (id.includes('vxe-table') || id.includes('vxe-pc-ui')) {
            return 'vendor-vxe'
          }
          if (id.includes('echarts') || id.includes('zrender')) {
            return 'vendor-echarts'
          }
          if (id.includes('highlight.js')) {
            return 'vendor-highlight'
          }
          if (id.includes('/vue/')
              || id.includes('vue-router')
              || id.includes('vuex')
              || id.includes('@vue/')) {
            return 'vendor-vue'
          }
          return undefined
        }
      }
    }
  }
})
