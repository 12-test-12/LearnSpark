/**
 * ============================================================
 *  LearnSpark · SQLite 数据库连接管理
 *
 *  双平台策略：
 *  - Android：@capacitor-community/sqlite 原生插件（数据存手机本地）
 *  - Web（开发调试）：sql.js（SQLite WASM 编译版，数据存 IndexedDB）
 *
 *  使用方式：
 *    import { getDatabase } from '@/db/database'
 *    const db = await getDatabase()
 *    const result = await db.query('SELECT * FROM projects')
 * ============================================================
 */

import { Capacitor } from '@capacitor/core'
import { initializeSchema } from './schema'
import { getWebDatabase, type WebDBConnection } from './web-database'

const DB_NAME = 'learnspark'
const DB_VERSION = 1

// 统一的数据库连接类型（兼容原生和 Web）
type DBConnection = WebDBConnection

let dbConnection: DBConnection | null = null
let initPromise: Promise<DBConnection> | null = null

/**
 * 获取数据库连接（单例）
 * 首次调用时初始化数据库 + 建表 + 种子数据
 */
export async function getDatabase(): Promise<DBConnection> {
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
 * 根据平台选择不同的实现
 */
async function initDatabase(): Promise<DBConnection> {
  try {
    const platform = Capacitor.getPlatform()
    console.log(`[DB] 当前平台: ${platform}`)

    let db: DBConnection

    if (platform === 'web') {
      // Web 环境：用 sql.js（SQLite WASM）
      console.log('[DB] 使用 sql.js (WASM) 后端')
      db = await getWebDatabase()
    } else {
      // Android/iOS 环境：用 @capacitor-community/sqlite 原生插件
      console.log('[DB] 使用 @capacitor-community/sqlite 原生插件')
      db = await getNativeDatabase()
    }

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
 * 原生平台（Android/iOS）数据库初始化
 * 使用 @capacitor-community/sqlite 插件
 */
async function getNativeDatabase(): Promise<DBConnection> {
  // 动态导入，避免 Web 环境加载不必要的原生代码
  const { SQLiteConnection, CapacitorSQLite } = await import('@capacitor-community/sqlite')

  const sqliteConnection = new SQLiteConnection(CapacitorSQLite)

  // 创建数据库连接
  const nativeDb = await sqliteConnection.createConnection(
    DB_NAME,
    false,           // 不加密
    'no-encryption', // 加密模式
    DB_VERSION,
    false            // 非只读
  )

  // 打开连接
  await nativeDb.open()
  console.log('[DB] 原生数据库连接已打开')

  // 适配为统一的接口
  return {
    async open() { /* 已打开 */ },
    async close() { await nativeDb.close() },
    async query(sql: string, params: unknown[] = []) {
      const result = await nativeDb.query(sql, params)
      return { values: result.values ?? [] }
    },
    async run(sql: string, params: unknown[] = []) {
      await nativeDb.run(sql, params)
      return { changes: { changes: 0, lastId: 0 } }
    },
    async execute(sql: string) {
      await nativeDb.execute(sql)
      return { changes: { changes: 0, lastId: 0 } }
    },
  }
}

/**
 * 关闭数据库连接（通常不需要手动调用）
 */
export async function closeDatabase(): Promise<void> {
  if (dbConnection) {
    try {
      await dbConnection.close()
    } catch (e) {
      console.warn('[DB] 关闭连接时警告:', e)
    }
    dbConnection = null
    initPromise = null
  }
}

/**
 * 生成 UUID（替代 MySQL 的 UUID() 函数）
 */
export function uuid(): string {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID()
  }
  // 降级方案（老环境）
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}

/**
 * 获取当前时间戳（替代 MySQL 的 CURRENT_TIMESTAMP）
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
