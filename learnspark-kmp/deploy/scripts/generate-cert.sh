#!/usr/bin/env bash
# 阶段 3.2：HTTPS 证书生成脚本
#
# 两种方式：
#   1. 自签名证书（开发/内网用，立即可用）
#   2. Let's Encrypt 证书（生产用，需要公网域名）
#
# 用法：
#   ./deploy/scripts/generate-cert.sh self-signed       # 自签名
#   ./deploy/scripts/generate-cert.sh letsencrypt DOMAIN EMAIL  # Let's Encrypt
#
# 输出：deploy/nginx/certs/ 下生成 fullchain.pem + privkey.pem

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
CERT_DIR="$PROJECT_ROOT/deploy/nginx/certs"

mkdir -p "$CERT_DIR"

MODE="${1:-self-signed}"

if [[ "$MODE" == "self-signed" ]]; then
    echo "[cert] generating self-signed cert for localhost/dev..."
    openssl req -x509 -newkey rsa:2048 -nodes -days 365 \
        -keyout "$CERT_DIR/privkey.pem" \
        -out "$CERT_DIR/fullchain.pem" \
        -subj "/C=CN/ST=Beijing/L=Beijing/O=LearnSpark/OU=Dev/CN=localhost" \
        -addext "subjectAltName=DNS:localhost,DNS:*.localhost,IP:127.0.0.1"
    echo "[cert] done: $CERT_DIR/{fullchain.pem,privkey.pem}"

elif [[ "$MODE" == "letsencrypt" ]]; then
    DOMAIN="${2:?Usage: $0 letsencrypt DOMAIN EMAIL}"
    EMAIL="${3:?Usage: $0 letsencrypt DOMAIN EMAIL}"
    echo "[cert] requesting Let's Encrypt cert for $DOMAIN..."

    if ! command -v certbot >/dev/null 2>&1; then
        echo "[cert] certbot not found. Install:"
        echo "  Ubuntu/Debian: sudo apt install certbot"
        echo "  macOS:         brew install certbot"
        echo "  Windows:       choco install certbot"
        exit 1
    fi

    # 使用 standalone 模式（需要 80 端口空闲）
    certbot certonly --standalone \
        --preferred-challenges http \
        -d "$DOMAIN" \
        --email "$EMAIL" \
        --agree-tos \
        --no-eff-email

    # 复制到 nginx certs 目录
    cp "/etc/letsencrypt/live/$DOMAIN/fullchain.pem" "$CERT_DIR/"
    cp "/etc/letsencrypt/live/$DOMAIN/privkey.pem" "$CERT_DIR/"

    echo "[cert] done: $CERT_DIR/{fullchain.pem,privkey.pem}"
    echo "[cert] auto-renew: add to crontab:"
    echo "  0 0 1 * * certbot renew --quiet && cp /etc/letsencrypt/live/$DOMAIN/*.pem $CERT_DIR/ && docker compose -f $PROJECT_ROOT/docker-compose.yml restart nginx"
else
    echo "Usage: $0 {self-signed|letsencrypt DOMAIN EMAIL}"
    exit 1
fi
