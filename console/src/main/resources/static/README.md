# 开始项目
## cnpm 安装（可忽略）
```sh
npm install -g cnpm --registry=https://registry.npm.taobao.org

# 设置匿名
alias cnpm="npm --registry=https://registry.npm.taobao.org \
--cache=$HOME/.npm/.cache/cnpm \
--disturl=https://npm.taobao.org/dist \
--userconfig=$HOME/.cnpmrc"

# Or alias it in .bashrc or .zshrc
$ echo '\n#alias for cnpm\nalias cnpm="npm --registry=https://registry.npm.taobao.org \
  --cache=$HOME/.npm/.cache/cnpm \
  --disturl=https://npm.taobao.org/dist \
  --userconfig=$HOME/.cnpmrc"' >> ~/.zshrc && source ~/.zshrc

```
[详情地址: http://npm.taobao.org/](http://npm.taobao.org/) 

## 安装依赖
```sh
cnpm install
```

## 启动
```sh
npm start
```

## 构建打包
```sh
npm build
```

## 

# 代理配置
根目录下的 .webpackrc
修改proxy属性

```
"proxy": {
    "/": {
      "target": "http://ip:port/", //这边写你自己的服务Ip
      "changeOrigin": true,
      "pathRewrite": { "^/" : "" }
    }
  },
```

# dva api
[https://github.com/dvajs/dva/blob/master/docs/api/README.md](https://github.com/dvajs/dva/blob/master/docs/api/README.md)