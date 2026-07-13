/**
 * ============================================================
 *  LearnSpark · Web 环境数据库适配器
 *
 *  在浏览器开发环境下直接用 sql.js（SQLite 的 WASM 编译版）
 *  不依赖 jeep-sqlite Web Component（避免 Vite 环境下的 wasm 兼容问题）
 *
 *  数据持久化：每次写入后自动序列化到 IndexedDB
 *
 *  Android 真机上不使用此文件，直接用 @capacitor-community/sqlite 原生插件
 * ============================================================
 */

import initSqlJs, { type Database, type SqlJsStatic } from 'sql.js'

const DB_NAME = 'learnspark'
const IDB_NAME = 'learnspark-db'
const IDB_STORE = 'sqlite'
const IDB_KEY = 'database'

let sqlStatic: SqlJsStatic | null = null
let dbInstance: Database | null = null

/**
 * IndexedDB 工具：持久化 sql.js 数据库
 */
function openIndexedDB(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(IDB_NAME, 1)
    req.onupgradeneeded = () => {
      req.result.createObjectStore(IDB_STORE)
    }
    req.onsuccess = () => resolve(req.result)
    req.onerror = () => reject(req.error)
  })
}

async function saveToIndexedDB(data: Uint8Array): Promise<void> {
  const idb = await openIndexedDB()
  return new Promise((resolve, reject) => {
    const tx = idb.transaction(IDB_STORE, 'readwrite')
    tx.objectStore(IDB_STORE).put(data, IDB_KEY)
    tx.oncomplete = () => { idb.close(); resolve() }
    tx.onerror = () => { idb.close(); reject(tx.error) }
  })
}

async function loadFromIndexedDB(): Promise<Uint8Array | null> {
  const idb = await openIndexedDB()
  return new Promise((resolve, reject) => {
    const tx = idb.transaction(IDB_STORE, 'readonly')
    const req = tx.objectStore(IDB_STORE).get(IDB_KEY)
    req.onsuccess = () => { idb.close(); resolve(req.result ?? null) }
    req.onerror = () => { idb.close(); reject(req.error) }
  })
}

/**
 * 兼容 SQLiteDBConnection 接口的 Web 实现
 * 提供 query / run / execute 方法，供 Repository 调用
 */
export interface WebDBConnection {
  query(sql: string, params?: unknown[]): Promise<{ values: Record<string, unknown>[] }>
  run(sql: string, params?: unknown[]): Promise<{ changes?: { changes: number; lastId: number } }>
  execute(sql: string): Promise<{ changes?: { changes: number; lastId: number } }>
  open(): Promise<void>
  close(): Promise<void>
}

/**
 * 初始化 Web 数据库（sql.js + IndexedDB 持久化）
 */
export async function getWebDatabase(): Promise<WebDBConnection> {
  if (dbInstance) {
    return createAdapter(dbInstance)
  }

  // 加载 sql.js wasm
  // 用 fetch 直接从 public/assets/ 加载 wasm 二进制数据
  // 避免 Vite 的 ?url 导入和 locateFile 回调的兼容性问题
  if (!sqlStatic) {
    console.log('[WebDB] 正在加载 sql.js wasm...')
    const wasmResponse = await fetch('/assets/sql-wasm.wasm')
    if (!wasmResponse.ok) {
      throw new Error(`加载 sql-wasm.wasm 失败: HTTP ${wasmResponse.status}`)
    }
    const wasmBinary = await wasmResponse.arrayBuffer()
    sqlStatic = await initSqlJs({ wasmBinary })
    console.log('[WebDB] sql.js wasm 加载完成')
  }

  // 尝试从 IndexedDB 恢复数据库
  const savedData = await loadFromIndexedDB()
  if (savedData) {
    dbInstance = new sqlStatic.Database(savedData)
    console.log('[WebDB] 从 IndexedDB 恢复数据库')
  } else {
    dbInstance = new sqlStatic.Database()
    console.log('[WebDB] 创建新数据库')
  }

  return createAdapter(dbInstance)
}

/**
 * 创建适配器对象（包装 sql.js 的 API 为 SQLiteDBConnection 兼容接口）
 */
function createAdapter(db: Database): WebDBConnection {
  return {
    async open() {
      // sql.js 的 Database 创建时已打开
    },

    async close() {
      db.close()
    },

    async query(sql: string, params: unknown[] = []) {
      const stmt = db.prepare(sql)
      try {
        if (params.length > 0) {
          stmt.bind(params)
        }
        const values: Record<string, unknown>[] = []
        while (stmt.step()) {
          values.push(stmt.getAsObject() as Record<string, unknown>)
        }
        return { values }
      } finally {
        stmt.free()
      }
    },

    async run(sql: string, params: unknown[] = []) {
      if (params.length > 0) {
        db.run(sql, params)
      } else {
        db.run(sql)
      }
      // 持久化到 IndexedDB
      await saveToIndexedDB(db.export())
      // 获取变更行数（sql.js 不直接返回，用 lastInsertRowId）
      const result = db.exec('SELECT changes() as changes, last_insert_rowid() as lastId')
      const changes = result[0]?.values?.[0]?.[0] as number ?? 0
      const lastId = result[0]?.values?.[0]?.[1] as number ?? 0
      return { changes: { changes, lastId } }
    },

    async execute(sql: string) {
      db.run(sql)
      // 持久化到 IndexedDB
      await saveToIndexedDB(db.export())
      const result = db.exec('SELECT changes() as changes, last_insert_rowid() as lastId')
      const changes = result[0]?.values?.[0]?.[0] as number ?? 0
      const lastId = result[0]?.values?.[0]?.[1] as number ?? 0
      return { changes: { changes, lastId } }
    },
  }
}
