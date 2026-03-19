# Nacos Console UI (Next)

新版 Nacos 控制台前端，基于 React 19 + TypeScript + Vite 7 + Tailwind CSS 4 + Shadcn/ui 构建。

## 环境要求

- Node.js >= 18
- npm >= 9

## 安装依赖

```bash
npm install
```

国内可使用镜像加速：

```bash
npm install --registry=https://registry.npmmirror.com
```

## 本地开发

```bash
npm run dev
```

访问 http://localhost:8000 ，Vite 代理规则：

- `/nacos/v1/auth/*`、`/nacos/v3/auth/*` 转发至 Admin Server（localhost:8848）
- 其余 `/nacos/*` 转发至 Console Server（localhost:8080，去除 `/nacos` 前缀）

## 构建

```bash
npm run build
```

构建流程：`tsc -b`（TypeScript 类型检查）+ `vite build`（生产构建），产物输出到 `dist/` 目录。

## 部署

将构建产物复制到后端静态资源目录：

```bash
rm -rf ../console/src/main/resources/static/next/*
cp -r dist/* ../console/src/main/resources/static/next/
```

部署后目录结构：

```
console/src/main/resources/static/next/
├── index.html
├── css/
├── js/
├── img/
├── favicon.svg
└── icons.svg
```

## contextPath 适配

构建产物使用相对路径（`./`），可适配任意 `nacos.console.contextPath` 配置值。无需针对不同 contextPath 重新构建。

## 代理配置

开发代理规则在 `vite.config.ts` 的 `server.proxy` 中配置。
