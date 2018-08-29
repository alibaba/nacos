#开始项目
## cd proj dir
npm install
## 开启
npm start

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
https://github.com/dvajs/dva/blob/master/docs/api/README.md