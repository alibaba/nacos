# Nacos Console UI (Next)

The new Nacos console frontend, built with React 19 + TypeScript + Vite 7 + Tailwind CSS 4 + Shadcn/ui.

## Prerequisites

- Node.js >= 18
- npm >= 9

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
