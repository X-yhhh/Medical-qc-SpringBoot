import { spawn } from 'node:child_process'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'

/**
 * 将 Windows 扩展路径转换为普通磁盘路径，避免 Vite 在依赖预构建时
 * 使用带 `\\?\` 前缀的 cwd 生成非法缓存目录。
 *
 * @param {string} rawPath 原始路径
 * @returns {string} 规范化后的路径
 */
const normalizeWindowsFsPath = (rawPath) => {
  if (typeof rawPath !== 'string' || rawPath.length === 0) {
    return rawPath
  }

  if (rawPath.startsWith('\\\\?\\')) {
    return rawPath.slice(4)
  }

  if (rawPath.startsWith('//?/')) {
    return rawPath.slice(4)
  }

  return rawPath
}

const scriptDir = path.dirname(fileURLToPath(import.meta.url))
const projectRoot = normalizeWindowsFsPath(path.resolve(scriptDir, '..'))
const viteBin = path.join(projectRoot, 'node_modules', 'vite', 'bin', 'vite.js')
const viteArgs = process.argv.slice(2)

process.chdir(projectRoot)

const child = spawn(process.execPath, [viteBin, ...viteArgs], {
  cwd: projectRoot,
  stdio: 'inherit',
  env: {
    ...process.env,
    INIT_CWD: projectRoot,
    PWD: projectRoot,
  },
})

const forwardSignal = (signal) => {
  if (!child.killed) {
    child.kill(signal)
  }
}

process.on('SIGINT', () => forwardSignal('SIGINT'))
process.on('SIGTERM', () => forwardSignal('SIGTERM'))

child.on('exit', (code, signal) => {
  if (signal) {
    process.kill(process.pid, signal)
    return
  }

  process.exit(code ?? 0)
})

child.on('error', (error) => {
  console.error('启动 Vite 失败:', error)
  process.exit(1)
})
