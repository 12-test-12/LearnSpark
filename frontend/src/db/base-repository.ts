/**
 * ============================================================
 *  LearnSpark · Repository 基类
 *
 *  提供通用的 CRUD 操作，子类只需指定表名和实体类型
 *  所有子类通过 getDatabase() 获取 SQLite 连接
 * ============================================================
 */

import { getDatabase, uuid, now, type SQLiteDBConnection } from './database'

/**
 * 实体基类（对应后端 BaseEntity）
 */
export interface BaseEntity {
  id: string
  created_at?: string
  updated_at?: string
}

/**
 * Repository 基类
 * 泛型 T 必须继承 BaseEntity
 */
export abstract class BaseRepository<T extends BaseEntity> {
  constructor(
    protected readonly tableName: string,
    protected readonly idField: string = 'id'
  ) {}

  /**
   * 获取数据库连接
   */
  protected async db(): Promise<WebDBConnection> {
    return getDatabase()
  }

  /**
   * 生成新 UUID
   */
  protected newId(): string {
    return uuid()
  }

  /**
   * 获取当前时间戳
   */
  protected now(): string {
    return now()
  }

  /**
   * 根据 ID 查询单条记录
   */
  async findById(id: string): Promise<T | null> {
    const db = await this.db()
    const result = await db.query(
      `SELECT * FROM ${this.tableName} WHERE ${this.idField} = ? LIMIT 1`,
      [id]
    )
    return (result.values?.[0] as T) ?? null
  }

  /**
   * 查询所有记录
   * @param where 可选 WHERE 子句（不含 WHERE 关键字）
   * @param params 参数
   * @param orderBy 可选 ORDER BY 子句
   * @param limit 可选 LIMIT
   */
  async findAll(
    where?: string,
    params: unknown[] = [],
    orderBy?: string,
    limit?: number
  ): Promise<T[]> {
    const db = await this.db()
    let sql = `SELECT * FROM ${this.tableName}`
    if (where) sql += ` WHERE ${where}`
    if (orderBy) sql += ` ORDER BY ${orderBy}`
    if (limit) sql += ` LIMIT ${limit}`
    const result = await db.query(sql, params)
    return (result.values ?? []) as T[]
  }

  /**
   * 插入一条记录
   * @param data 键值对对象
   * @returns 生成的 ID
   */
  async insert(data: Partial<T> & Record<string, unknown>): Promise<string> {
    const db = await this.db()
    const id = (data[this.idField] as string) || this.newId()
    const timestamp = this.now()

    const fields = [this.idField, 'created_at', 'updated_at']
    const values: unknown[] = [id, timestamp, timestamp]
    const placeholders = ['?', '?', '?']

    for (const [key, value] of Object.entries(data)) {
      if (key === this.idField || key === 'created_at' || key === 'updated_at') continue
      fields.push(key)
      values.push(this.serializeValue(value))
      placeholders.push('?')
    }

    const sql = `INSERT INTO ${this.tableName} (${fields.join(', ')}) VALUES (${placeholders.join(', ')})`
    await db.run(sql, values)
    return id
  }

  /**
   * 根据 ID 更新记录
   * @param id 主键
   * @param data 要更新的字段
   */
  async update(id: string, data: Partial<T> & Record<string, unknown>): Promise<void> {
    const db = await this.db()
    const timestamp = this.now()

    const sets: string[] = ['updated_at = ?']
    const values: unknown[] = [timestamp]

    for (const [key, value] of Object.entries(data)) {
      if (key === this.idField || key === 'created_at' || key === 'updated_at') continue
      sets.push(`${key} = ?`)
      values.push(this.serializeValue(value))
    }

    values.push(id)
    const sql = `UPDATE ${this.tableName} SET ${sets.join(', ')} WHERE ${this.idField} = ?`
    await db.run(sql, values)
  }

  /**
   * 根据 ID 删除记录（物理删除）
   */
  async delete(id: string): Promise<void> {
    const db = await this.db()
    await db.run(`DELETE FROM ${this.tableName} WHERE ${this.idField} = ?`, [id])
  }

  /**
   * 软删除（设置 deleted_at 字段）
   */
  async softDelete(id: string): Promise<void> {
    const db = await this.db()
    await db.run(
      `UPDATE ${this.tableName} SET deleted_at = ?, updated_at = ? WHERE ${this.idField} = ?`,
      [this.now(), this.now(), id]
    )
  }

  /**
   * 统计记录数
   */
  async count(where?: string, params: unknown[] = []): Promise<number> {
    const db = await this.db()
    let sql = `SELECT COUNT(*) as cnt FROM ${this.tableName}`
    if (where) sql += ` WHERE ${where}`
    const result = await db.query(sql, params)
    return (result.values?.[0]?.cnt as number) ?? 0
  }

  /**
   * 序列化值（处理 JSON 对象/数组）
   */
  protected serializeValue(value: unknown): unknown {
    if (value === null || value === undefined) return null
    if (typeof value === 'object') return JSON.stringify(value)
    if (Array.isArray(value)) return JSON.stringify(value)
    return value
  }

  /**
   * 反序列化 JSON 字段
   */
  protected deserializeJSON<T = unknown>(value: unknown): T | null {
    if (!value || typeof value !== 'string') return null
    try {
      return JSON.parse(value) as T
    } catch {
      return null
    }
  }
}
