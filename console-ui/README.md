# 开始项目
国内访问 npm 比较慢,我们可以使用阿里的镜像,
在 npm 或者 yarn 命令后面加参数:
> --registry=https://registry.npm.taobao.org
例: 
```
npm install --registry=https://registry.npm.taobao.org
yarn --registry=https://registry.npm.taobao.org
```
[详情地址: http://npm.taobao.org/](http://npm.taobao.org/) 

## Node安装

NodeJS提供了一些安装程序，都可以在[nodejs.org](https://nodejs.org/download/release/) 这里下载并安装。mac系统选择.pkg结尾的文件下载安装。
注意node版本号过高可能导致 `npm install` 时失败，建议版本:
- node:v8.16.0
- npm:6.4.1

## 安装依赖
```sh
yarn
```
或
```
npm install
```

## 启动
```sh
yarn start
```
或
```
npm start
```

## 构建打包
```sh
yarn build
```
或
```
npm run build
```
## 

# 代理配置
`build/webpack.dev.conf.js`
修改proxy属性

```
proxy: [{
  context: ['/'],
  changeOrigin: true,
  secure: false,
  target: 'http://ip:port',
}],
```