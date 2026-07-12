# =============================================================================
# LearnSpark 前端 Dockerfile（多阶段构建 + nginx）
# 构建：docker build -t learnspark-frontend:0.1.0 ./frontend
# =============================================================================

# ---------- Stage 1: 构建 ----------
FROM node:20-alpine AS builder
WORKDIR /build

COPY package.json package-lock.json* ./
RUN npm config set registry https://registry.npmmirror.com && \
    npm ci || npm install

COPY . .
RUN npm run build

# ---------- Stage 2: 运行 ----------
FROM nginx:alpine
WORKDIR /usr/share/nginx/html

# 清空默认页面，拷贝构建产物
RUN rm -rf ./*
COPY --from=builder /build/dist .

# nginx 配置：SPA history 路由 + /api 反代到后端
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -q --spider http://localhost/ || exit 1
