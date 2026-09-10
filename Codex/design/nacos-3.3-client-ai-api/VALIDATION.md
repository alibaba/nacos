# 第一步实施验证

基线：`upstream/develop`，`3623b19db6be69545d7a5af36705b92274d10390`。
分支：`codex/client-ai-resource-services`。只做本地 commit，不提交 PR。

## C1：接口和兼容委托

- 五个资源接口与固定 delegate；旧签名 default/deprecated；Agent 原生 API 调用点迁到 `agent()`。
- 迁移前后 36 个业务方法体去除格式差异后完全一致；未改 A2A wire 或资源业务算法。
- `mvn -B -pl api,client,test/java-sdk-test spotless:apply spotless:check`：通过。
- `mvn -B -pl api,client test -Dtest=AiServiceDefaultMethodTest,A2aServiceDefaultMethodTest,AiFactoryTest,NacosAiServiceTest,NacosAiServiceAgentSpecPropertyTest -Dsurefire.failIfNoSpecifiedTests=false`：通过，Client 89 项（含 3 项属性测试）。
- `mvn -B -pl test/java-sdk-test -am install ...`：依赖及 SDK IT test-compile 通过。包名通配符的 `-Dtest` 未匹配测试，不能算 UT 证据；UT 以以上明确类名的独立命令为准。
- 首次 reactor 校验被既有 `test/naming-test` 两个运行日志触发 RAT；已保留到 `/tmp/nacos-ai-phase1/preexisting-logs` 后重跑通过，没有关闭 RAT。
- standalone、旧字节码和 transport 组合执行结果继续补充。

## C2：资源 transport

- 五个 override 构造时严格校验；MCP/Agent/Prompt 独立 mode 和 AUTO 状态；Skill/AgentSpec 固定使用原 HTTP proxy；旧 A2A 保留原 gRPC。
- 连接能力检查保留旧 `NacosRuntimeException(500)` 类型、码和文案，仅增加 `CLIENT_DISCONNECT` cause 作为 AUTO 安全读回退证据。业务错误优先，写操作不重放。
- API 相关回归：536 项通过。Client 相关回归：528 项，527 通过、1 项既有 Disabled。最后连接竞态修正后的定向 UT：184 项全部通过。
- `spotless:apply` / `spotless:check`：api、client、test/java-sdk-test 通过；构建携带 Checkstyle/RAT 通过。
- SDK IT 使用 `release-nacos,!dev` 形态的 client jar；普通开发 install 的精简 POM 不携带未重定位 gRPC，不能混作外部 SDK 制品使用。
- 独立 standalone：当前仓库构建，HTTP 18488 / gRPC 19488 / Console 18080，默认鉴权开启；通过原 auth-it-identity.sh 建立业务、只读、无权限身份。
- `AiServiceJavaSdkITCase` 15 项、`McpHttpClientJavaSdkITCase` 3 项、`AgentPublishJavaSdkITCase` 5 项、`AiTransportResourceMatrixJavaSdkITCase` 6 项全部通过（29 个不同测试）。矩阵验证三种全局 mode、两组反向资源 override、gRPC 不可达时原生 HTTP 可用及旧 A2A 原异常契约。
- 真实环境暴露了旧测试断言误认为 A2A 断连返回 checked exception，已改为校验原 runtime exception；未改变旧异常契约。
- 历史增量构建残留的 datasource/client-test SPI 通过 clean 重建清除；未改服务端代码。一个既有 MCP 缓存 UT 的固定 110ms 等待改为有界条件等待，避免调度抖动。
- 原始日志与报告：`/tmp/nacos-ai-phase1/`。C3 继续旧字节码、旧 SDK/旧服务端和组合回归。
