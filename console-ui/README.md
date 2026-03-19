# Nacos Console UI (Legacy)

旧版 Nacos 控制台前端，基于 React + Webpack 4 构建。

## 环境要求

- Node.js >= 14（推荐 14.x ~ 22.x）
- npm
- 全局安装 CLI 工具：

```bash
npm install -g cross-env webpack webpack-cli
```

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
npm start
```

代理配置在 `build/webpack.dev.conf.js` 的 `proxy` 属性中修改：

```js
proxy: [{
  context: ['/'],
  changeOrigin: true,
  secure: false,
  target: 'http://ip:port',
}],
```

## 构建

```bash
npm run build
```

构建产物输出到 `dist/` 目录。

> 注：Node.js 17+ 版本需要 `--openssl-legacy-provider` 参数（已在 package.json 的 build 脚本中配置）。

## 部署

将构建产物复制到后端静态资源目录：

```bash
rm -rf ../console/src/main/resources/static/legacy/*
cp -r dist/* ../console/src/main/resources/static/legacy/
```

部署后目录结构：

```
console/src/main/resources/static/legacy/
├── index.html
├── css/
└── js/
```

## contextPath 适配

构建产物使用相对路径（`../`），可适配任意 `nacos.console.contextPath` 配置值。无需针对不同 contextPath 重新构建。
