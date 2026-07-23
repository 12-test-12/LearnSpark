# 阶段 3.2：LearnSpark 部署说明

## 目录结构

```
learnspark-kmp/
├── docker-compose.yml              # 主编排：mysql + app-api + app-worker + nginx
├── .env.example                    # 环境变量模板
└── deploy/
    ├── mysql/
    │   └── conf/charset.cnf        # MySQL 字符集 + ngram 配置
    ├── nginx/
    │   ├── conf.d/default.conf     # HTTPS 反向代理 + 安全头
    │   └── certs/                  # 证书目录（运行时生成）
    └── scripts/
        ├── generate-cert.sh        # 证书生成（自签 / Let's Encrypt）
        └── backup-mysql.sh         # 数据库备份
```

## 快速启动（开发环境）

```bash
# 1. 复制环境变量
cp .env.example .env
# 编辑 .env，修改密码 + JWT_SECRET

# 2. 生成自签名证书
./deploy/scripts/generate-cert.sh self-signed

# 3. 启动
docker compose up -d
docker compose logs -f app-api app-worker
```

## 生产环境

```bash
# 1. 准备域名 + DNS
# 2. 申请证书
./deploy/scripts/generate-cert.sh letsencrypt your-domain.com admin@your-domain.com

# 3. 启动
docker compose up -d

# 4. 配置定时备份
crontab -e
# 添加：0 3 * * * /opt/learnspark/deploy/scripts/backup-mysql.sh --remote s3://my-bucket/db-backups
```

## 进程职责

| 服务 | 端口 | 职责 | 内存 |
|------|------|------|------|
| mysql | 3306 | 主数据 | 默认 |
| app-api | 8080 | REST API | -Xmx512m |
| app-worker | - | 文件解析（@Scheduled 轮询） | -Xmx1024m |
| nginx | 80/443 | HTTPS 终止 + 反向代理 | 默认 |

## 验证

```bash
# 健康检查
curl https://localhost/actuator/health
# {"status":"UP"}

# 迁移导入（旧版 Vue3 数据）
curl -X POST -H "X-User-Id: 00000000-0000-0000-0000-000000000001" \
     -H "Content-Type: application/json" \
     --data-binary @server/src/main/resources/samples/migration-sample.json \
     https://localhost/api/v1/migration/import
```

## 回滚

```bash
docker compose down                  # 停止但保留数据
docker compose down -v               # 停止并删除所有数据卷（谨慎）
```

## 监控（建议）

- Prometheus + Grafana 抓取 `/actuator/prometheus`
- 日志：app-api / app-worker / nginx 都有独立 volume，可用 Loki 收集
- 告警：app-worker 心跳停止 / MySQL 连接失败 / 磁盘空间 < 10%
