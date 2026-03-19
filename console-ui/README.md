# Nacos Console UI (Legacy)

The legacy Nacos console frontend, built with React + Webpack 4.

## Prerequisites

- Node.js >= 14 (recommended 14.x ~ 22.x)
- npm
- Global CLI tools:

```bash
npm install -g cross-env webpack webpack-cli
```

## Install Dependencies

```bash
npm install
```

## Local Development

```bash
npm start
```

Proxy configuration is in `build/webpack.dev.conf.js` under the `proxy` property:

```js
proxy: [{
  context: ['/'],
  changeOrigin: true,
  secure: false,
  target: 'http://ip:port',
}],
```

## Build

```bash
npm run build
```

Build output goes to the `dist/` directory.

> Note: Node.js 17+ requires the `--openssl-legacy-provider` flag (already configured in the package.json build script).

## Deploy

Copy build artifacts to the backend static resources directory:

```bash
rm -rf ../console/src/main/resources/static/legacy/*
cp -r dist/* ../console/src/main/resources/static/legacy/
```

Deployed directory structure:

```
console/src/main/resources/static/legacy/
├── index.html
├── css/
└── js/
```

## contextPath Adaptation

Build artifacts use relative paths (`../`), adapting to any `nacos.console.contextPath` configuration value. No rebuild is needed for different contextPath settings.
