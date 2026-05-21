# Nacos Console UI (Next)

The new Nacos console frontend, built with React 19 + TypeScript + Vite 7 + Tailwind CSS 4 + Shadcn/ui.

## Prerequisites

- Node.js >= 20.19+
- npm >= 10

## .npmrc Configuration

本项目配置了 `.npmrc` 文件，设置 `min-release-age=3`，确保 `npm install` 时不安装发布时间不足 3 天的包，降低供应链风险。

This project uses `.npmrc` with `min-release-age=3` to ensure `npm install` does not install packages released less than 3 days ago, reducing supply chain risks.

> 注意：`min-release-age` 仅 npm >= 11 生效，单位：天。
>
> Note: `min-release-age` only takes effect with npm >= 11. Unit: day.
>
> 参考 / Ref: https://docs.npmjs.com/cli/v11/commands/npm-install#min-release-age

## Install Dependencies

```bash
npm install
```

## Local Development

```bash
npm run dev
```

Visit http://localhost:8000. Vite proxy rules:

- `/nacos/v1/auth/*`, `/nacos/v3/auth/*` are forwarded to Admin Server (localhost:8848)
- All other `/nacos/*` requests are forwarded to Console Server (localhost:8080, with `/nacos` prefix stripped)

## Build

```bash
npm run build
```

Build pipeline: `tsc -b` (TypeScript type checking) + `vite build` (production build). Output goes to the `dist/` directory.

## Deploy

Copy build artifacts to the backend static resources directory:

```bash
rm -rf ../console/src/main/resources/static/next/*
cp -r dist/* ../console/src/main/resources/static/next/
```

Deployed directory structure:

```
console/src/main/resources/static/next/
├── index.html
├── css/
├── js/
├── img/
├── favicon.svg
└── icons.svg
```

## contextPath Adaptation

Build artifacts use relative paths (`./`), adapting to any `nacos.console.contextPath` configuration value. No rebuild is needed for different contextPath settings.

## Proxy Configuration

Development proxy rules are configured in `vite.config.ts` under `server.proxy`.
