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
