/**
 * ============================================================
 *  LearnSpark · SQLite 数据库连接管理
 *
 *  使用 @capacitor-community/sqlite 插件
 *  在 Android 上创建本地 SQLite 数据库，所有数据存手机本地
 *
 *  使用方式：
 *    import { getDatabase } from '@/db/database'
 *    const db = await getDatabase()
 *    const result = await db.query('SELECT * FROM projects')
 * ============================================================
 */

import { SQLiteConnection, SQLiteDBConnection } from '@capacitor-community/sqlite'
import { Capacitor } from '@capacitor/core'
import { initializeSchema } from './schema'

const DB_NAME = 'learnspark.db'
const DB_VERSION = 1

let sqlite: SQLiteConnection | null = null
let dbConnection: SQLiteDBConnection | null = null
let initPromise: Promise<SQLiteDBConnection> | null = null

/**
 * 获取数据库连接（单例）
 * 首次调用时初始化数据库 + 建表 + 种子数据
 */
export async function getDatabase(): Promise<SQLiteDBConnection> {
  if (dbConnection) {
    return dbConnection
  }
  if (initPromise) {
    return initPromise
  }
  initPromise = initDatabase()
  return initPromise
}

/**
 * 初始化数据库
 */
async function initDatabase(): Promise<SQLiteDBConnection> {
  try {
    // 在 Web 环境（开发调试）用 Web 实现
    const platform = Capacitor.getPlatform()
    
    if (platform === 'web') {
      // Web 环境需要导入 sqlite-utility 的 web store
      await initWebStore()
    }

    sqlite = new SQLiteConnection()
    
    // 检查是否需要加密（离线版不需要）
    const db = await sqlite.createConnection(DB_NAME, false, 'no-encryption', DB_VERSION, false)
    
    // 打开连接
    await db.open()
    
    // 开启外键约束
    await db.execute('PRAGMA foreign_keys = ON')
    
    // 检查是否已初始化（通过检查 users 表是否存在）
    const tableCheck = await db.query(
      `SELECT name FROM sqlite_master WHERE type='table' AND name='users'`
    )
    
    if (!tableCheck.values || tableCheck.values.length === 0) {
      console.log('[DB] 首次启动，初始化 schema...')
      await initializeSchema(db)
      console.log('[DB] schema 初始化完成')
    } else {
      console.log('[DB] 数据库已存在，跳过初始化')
    }
    
    dbConnection = db
    return db
  } catch (error) {
    console.error('[DB] 初始化失败:', error)
    initPromise = null
    throw error
  }
}

/**
 * Web 环境初始化（开发调试用）
 * 在浏览器里 SQLite 插件需要 wa-sqlite wasm 后端
 * 如果不可用，静默降级（开发时数据不持久，真机上正常）
 */
async function initWebStore(): Promise<void> {
  // Web 环境下 SQLite 插件需要额外的 wasm 文件
  // 这里不做特殊处理，让插件自己在 Web 模式下降级
  // 真机（Android）上不需要这个函数
  console.warn('[DB] Web 环境检测到，SQLite 可能需要 wasm 后端。真机上不受影响。')
}

/**
 * 关闭数据库连接（通常不需要手动调用）
 */
export async function closeDatabase(): Promise<void> {
  if (sqlite && dbConnection) {
    await sqlite.closeConnection(DB_NAME, false)
    dbConnection = null
    initPromise = null
  }
}

/**
 * 生成 UUID（替代 MySQL 的 UUID() 函数）
 */
export function uuid(): string {
  return crypto.randomUUID()
}

/**
 * 获取当前 ISO 时间戳（替代 MySQL 的 CURRENT_TIMESTAMP）
 * 格式：YYYY-MM-DD HH:mm:ss（与 SQLite datetime 兼容）
 */
export function now(): string {
  return new Date().toISOString().replace('T', ' ').substring(0, 19)
}

/**
 * 获取当前日期（YYYY-MM-DD）
 */
export function today(): string {
  return new Date().toISOString().substring(0, 10)
}

export type { SQLiteDBConnection }
