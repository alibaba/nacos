# Nacos 开发指南

本文档面向 Nacos 核心开发者，介绍如何设置开发环境、运行测试和贡献代码。

## 环境要求

- JDK 8 或 JDK 11
- Maven 3.6+
- MySQL 5.7+ (用于持久化存储，可选)

## 快速开始

### 1. 克隆仓库

```bash
git clone https://github.com/alibaba/nacos.git
cd nacos
```

### 2. 编译项目

```bash
mvn -Prelease-nacos -Dmaven.test.skip=true clean install -U
```

### 3. 启动服务器

```bash
sh distribution/target/nacos-server-$version/nacos/bin/startup.sh -m standalone
```

Windows 用户:
```cmd
distribution\target\nacos-server-$version\nacos\bin\startup.cmd -m standalone
```

### 4. 访问控制台

打开浏览器访问 `http://localhost:8848/nacos`

默认账号密码: `nacos/nacos`

## 开发模式

### IDE 导入

推荐使用 IntelliJ IDEA:

1. `File > Open` 选择项目根目录
2. 等待 Maven 依赖下载完成
3. 启用 Lombok 插件
4. 配置 JDK 版本

### 模块说明

| 模块 | 说明 |
|------|------|
| `api` | 公共 API 接口 |
| `client` | Java SDK 客户端 |
| `core` | 核心逻辑 |
| `config` | 配置管理 |
| `naming` | 服务发现 |
| `auth` | 权限控制 |
| `console` | Web 控制台 |
| `distribution` | 分发包构建 |

### 单元测试

```bash
# 运行所有测试
mvn test

# 运行指定模块测试
mvn test -pl core

# 跳过测试
mvn install -DskipTests
```

### 集成测试

```bash
# 需要先启动 MySQL
mvn verify -Pit
```

## 数据库配置

### 使用 MySQL

1. 创建数据库:
```sql
CREATE DATABASE nacos_dev CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. 导入表结构:
```bash
mysql -u root -p nacos_dev < distribution/conf/mysql-schema.sql
```

3. 修改 `application.properties`:
```properties
spring.datasource.platform=mysql
db.num=1
db.url.0=jdbc:mysql://localhost:3306/nacos_dev?characterEncoding=utf8&connectTimeout=1000&socketTimeout=3000&autoReconnect=true
db.user=root
db.password=your_password
```

## 调试技巧

### 远程调试

启动时添加调试参数:
```bash
sh startup.sh -m standalone --debug
```

然后在 IDEA 中配置 Remote Debug，端口默认 9555。

### 日志配置

修改 `conf/nacos-logback.xml` 调整日志级别:

```xml
<logger name="com.alibaba.nacos" level="DEBUG"/>
```

日志文件位置: `logs/nacos.log`

### 常见问题

**Q: 端口被占用**
```bash
# 查找占用端口的进程
lsof -i :8848
# 或修改端口
# conf/application.properties: server.port=8849
```

**Q: 内存不足**
```bash
# 调整 JVM 参数
# startup.sh 中修改 JAVA_OPT
JAVA_OPT="${JAVA_OPT} -Xms512m -Xmx512m"
```

## 代码规范

### 提交信息

```
[type] scope: description

[optional body]
```

类型:
- `[Fix]` Bug 修复
- `[Feature]` 新功能
- `[Refactor]` 重构
- `[Doc]` 文档更新
- `[Test]` 测试相关
- `[Chore]` 构建/工具

示例:
```
[Fix] naming: fix service list pagination issue
[Feature] config: add beta release support
```

### 代码风格

- 遵循阿里巴巴 Java 开发手册
- 使用 Checkstyle 检查:
  ```bash
  mvn checkstyle:check
  ```

## 贡献流程

1. Fork 仓库
2. 创建分支: `git checkout -b fix/my-fix`
3. 提交代码: `git commit -m "[Fix] xxx"`
4. 推送分支: `git push origin fix/my-fix`
5. 创建 Pull Request

## 资源链接

- [官方文档](https://nacos.io/zh-cn/docs/what-is-nacos.html)
- [API 文档](https://nacos.io/zh-cn/docs/open-api.html)
- [社区论坛](https://nacos.io/zh-cn/community)

---

感谢你对 Nacos 的贡献！🎉
