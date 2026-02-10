# nacos-api-legacy-adapter

本模块为 **兼容旧版本 HTTP API** 的适配层，提供 legacy v1/v2 接口的兼容实现，便于在 Nacos 升级后仍能支持尚未改造的旧客户端或调用方。

**使用说明：**

- 本模块**仅面向**迫切需要使用旧版本 API、或正在对旧客户端进行改造的用户**临时使用**。
- **不保证**未来 Nacos 版本仍会保留或继续支持本模块，建议尽快迁移到新版本 API 与客户端。

---

## 可插拔使用方式

本模块**非默认依赖**，仅当 JAR 出现在运行期 classpath 时生效；默认构建与启动不包含本模块。

### 需要 legacy API 时如何引入

- **使用发行包**：将 `nacos-api-legacy-adapter-${version}.jar` 放入 Nacos 可加载的目录（如 `plugins`），保证在 classpath 中即可。
- **自定义/嵌入应用**：在构建或运行 classpath 中增加对 `nacos-api-legacy-adapter` 的 Maven 依赖。

无需改代码或配置；classpath 中存在该 JAR 时，模块会自动加载并生效。
