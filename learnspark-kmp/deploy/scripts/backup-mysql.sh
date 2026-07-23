#!/usr/bin/env bash
# 阶段 3.2：MySQL 数据备份脚本
#
# 特性：
#   - 每天凌晨 3 点全量备份（推荐 cron: 0 3 * * *）
#   - 保留最近 30 天本地备份
#   - 可选：上传到 S3 / OSS / 远程 rsync
#   - 备份完整性校验（mysqldump 退出码）
#
# 用法：
#   ./deploy/scripts/backup-mysql.sh
#   ./deploy/scripts/backup-mysql.sh --remote s3://my-bucket/db-backups
#
# 恢复：
#   gunzip < backups/learnspark-2026-07-22_030000.sql.gz | mysql -u root -p learnspark

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# 配置（可通过环境变量覆盖）
BACKUP_DIR="${BACKUP_DIR:-$PROJECT_ROOT/deploy/backups/mysql}"
MYSQL_HOST="${MYSQL_HOST:-localhost}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-learnspark}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-learnspark}"
MYSQL_DATABASE="${MYSQL_DATABASE:-learnspark}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"

# 远程上传目标
REMOTE_TARGET=""
while [[ $# -gt 0 ]]; do
    case $1 in
        --remote) REMOTE_TARGET="$2"; shift 2 ;;
        --retention) RETENTION_DAYS="$2"; shift 2 ;;
        *) echo "Unknown arg: $1"; exit 1 ;;
    esac
done

mkdir -p "$BACKUP_DIR"

# 备份文件名：learnspark-YYYYMMDD_HHMMSS.sql.gz
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_FILE="$BACKUP_DIR/${MYSQL_DATABASE}-${TIMESTAMP}.sql.gz"

echo "[backup] start at $(date '+%Y-%m-%d %H:%M:%S')"
echo "[backup] target: $BACKUP_FILE"

# 备份（mysqldump 单事务 + 跳过锁表，适合 InnoDB）
# 注：生产环境用 --master-data=2 启用 GTID 恢复
if ! mysqldump \
    --host="$MYSQL_HOST" \
    --port="$MYSQL_PORT" \
    --user="$MYSQL_USER" \
    --password="$MYSQL_PASSWORD" \
    --single-transaction \
    --quick \
    --routines \
    --triggers \
    --events \
    --hex-blob \
    --default-character-set=utf8mb4 \
    --set-gtid-purged=OFF \
    "$MYSQL_DATABASE" | gzip > "$BACKUP_FILE"; then
    echo "[backup] FAILED: mysqldump exit non-zero"
    rm -f "$BACKUP_FILE"
    exit 1
fi

# 校验
BACKUP_SIZE=$(stat -c%s "$BACKUP_FILE" 2>/dev/null || stat -f%z "$BACKUP_FILE")
if [[ "$BACKUP_SIZE" -lt 1024 ]]; then
    echo "[backup] FAILED: backup too small ($BACKUP_SIZE bytes), probably empty"
    rm -f "$BACKUP_FILE"
    exit 1
fi

echo "[backup] success: $BACKUP_FILE ($BACKUP_SIZE bytes)"

# 清理过期
echo "[backup] cleaning backups older than $RETENTION_DAYS days..."
find "$BACKUP_DIR" -name "${MYSQL_DATABASE}-*.sql.gz" -mtime +$RETENTION_DAYS -delete -print
REMAINING=$(find "$BACKUP_DIR" -name "${MYSQL_DATABASE}-*.sql.gz" | wc -l)
echo "[backup] remaining backups: $REMAINING"

# 远程上传
if [[ -n "$REMOTE_TARGET" ]]; then
    echo "[backup] uploading to $REMOTE_TARGET..."
    case "$REMOTE_TARGET" in
        s3://*)
            command -v aws >/dev/null || { echo "[backup] aws cli not found"; exit 1; }
            aws s3 cp "$BACKUP_FILE" "$REMOTE_TARGET/${MYSQL_DATABASE}-${TIMESTAMP}.sql.gz" --storage-class STANDARD_IA
            ;;
        oss://*)
            command -v ossutil >/dev/null || { echo "[backup] ossutil not found"; exit 1; }
            ossutil cp "$BACKUP_FILE" "$REMOTE_TARGET/${MYSQL_DATABASE}-${TIMESTAMP}.sql.gz"
            ;;
        rsync://*|*)
            rsync -avz "$BACKUP_FILE" "$REMOTE_TARGET/"
            ;;
    esac
    echo "[backup] remote upload done"
fi

echo "[backup] done at $(date '+%Y-%m-%d %H:%M:%S')"
