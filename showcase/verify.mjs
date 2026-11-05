import { build } from 'esbuild'
import { writeFile, unlink } from 'node:fs/promises'
import { resolve } from 'node:path'
import { pathToFileURL } from 'node:url'

const path = resolve('.showcase-verification.mjs')
const bundled = await build({
  entryPoints: ['examples/before-arrival/verify.tsx'],
  bundle: true,
  write: false,
  platform: 'node',
  format: 'esm',
  target: 'node22',
  jsx: 'automatic',
  loader: { '.css': 'empty' },
  nodePaths: (process.env.SHOWCASE_MODULES || '').split(';').filter(Boolean),
  define: { 'process.env.NODE_ENV': '"production"' },
  banner: { js: 'import { createRequire } from "node:module"; const require = createRequire(import.meta.url);' }
})
await writeFile(path, bundled.outputFiles[0].contents, { flag: 'wx' })
try {
  await import(pathToFileURL(path))
} finally {
  await unlink(path)
}
