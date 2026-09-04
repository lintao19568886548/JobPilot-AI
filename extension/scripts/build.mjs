import { build } from 'vite'
import { copyFile, mkdir } from 'node:fs/promises'
import { resolve } from 'node:path'

const root = resolve(import.meta.dirname, '..')
await build({ root, configFile: resolve(root, 'vite.config.ts') })
for (const [entry, fileName] of [
  ['src/service-worker.ts', 'service-worker.js'],
  ['src/content-script.ts', 'content-script.js']
]) {
  await build({
    root,
    configFile: false,
    build: {
      outDir: resolve(root, 'dist'), emptyOutDir: false,
      lib: { entry: resolve(root, entry), formats: ['iife'], name: 'JobPilotExtension', fileName: () => fileName },
      minify: true
    }
  })
}
await mkdir(resolve(root, 'dist'), { recursive: true })
await copyFile(resolve(root, 'manifest.json'), resolve(root, 'dist/manifest.json'))
