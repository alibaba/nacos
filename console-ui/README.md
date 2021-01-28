# 开始项目
国内访问 npm 比较慢,我们可以使用阿里的镜像,
在 npm 命令后面加参数:
> --registry=https://registry.npm.taobao.org
例: 
```
npm install --registry=https://registry.npm.taobao.org
```
[详情地址: http://npm.taobao.org/](http://npm.taobao.org/) 
## 安装依赖

```sh
npm install
```

## 启动

```sh
npm start
```

## 构建打包

```sh
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
