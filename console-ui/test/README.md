## 使用说明

### 安装依赖
```sh
npm install uirecorder mocha -g
npm install
```

### 安装chrome浏览器插件
```sh
npm run installdriver
```

### 开始录制测试用例
```sh
// xxx.spec.js 为你的测试用例文件名称
uirecorder sample/xxx.spec.js
```

### 回归测试
#### 启动服务
```sh
npm run server
```

#### 单个文件测试
```sh
// xxx.spec.js 为你的测试用例文件名称
npm run singletest sample/xxx.spec.js
```

#### 并发测试
```sh 
npm run paralleltest
```

### 查看报告
```sh
open reports/index.html
```