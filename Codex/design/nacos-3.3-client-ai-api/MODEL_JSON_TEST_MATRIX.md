# Agent 去 Jackson 注解测试矩阵

本轮范围：Agent 模型移除 NON_NULL/JsonIgnore，派生方法改名，Endpoint 默认值，公开 Schema 0.3.0。
保持全局 JsonUtils、MCP、历史 A2A 模型及既有 descriptor null 过滤行为；不修复 CONSOLE-ERR-01。
不自动 commit、push 或创建 PR。旧矩阵结果只用来定位测试，不计为本轮通过证据。

| ID | 验证范围 | 预期与独立断言 | 状态 |
| --- | --- | --- | --- |
| JSON-01 | 全部 Agent 模型 | 无 Jackson 注解；缺省 priority=0/weight=1/healthy=true/enabled=true；优先级越小越优先；0/false 正确往返 | 通过 |
| JSON-02 | AgentVersionInfo | onlineCnt()/latestVersion()；缺省 0/null；JSON 无派生字段；索引和存储投影保持 | 通过 |
| JSON-03 | 声明/运行/注销 | 声明状态不写版本；Runtime 健康可写、管理字段忽略；返回 Endpoint 可直接用于多项注销，只读取自然键 | 通过 |
| JSON-04 | JSON adapters/HTTP | Jackson 2、Jackson 3、Spring 实际 mapper；显式 null 的新响应符合新 Schema；错误类型受控 | 通过 |
| JSON-05 | Schema | 管理/RAD 0.3.0、Artifact/Watch 新引用；历史修订按 Git 追溯；正反实例拒绝非法类型、范围和结构 | 通过 |
| JSON-06 | 存储与摘要 | 显式存储投影不保存默认健康/管理字段；原始 bytes digest；存储读回再发现指纹一致 | 通过 |
| JSON-07 | Agent/旧 A2A 功能 | 沿 EP-01～EP-16 验证发布、生命周期、索引、Artifact、Runtime、选择、Watch、迁移 | 已完成：普通 IT/30 项迁移通过；私有 Watch 缺口保留 |
| JSON-08 | 外部 HTTP/SDK | Admin/Client/合并及独立 Console/ARD；SDK GRPC/HTTP/AUTO，两个 JSON adapter，INDEX/SCAN，旧/资源 API | 已完成：普通 HTTP/SDK/SCAN 通过；独立 Console 保留 3 项已知失败 |
| JSON-09 | 前端及构件 | 默认值及 nullable 类型与转换；前端测试/build；新发行包嵌入本轮 class；规范和场景登记同步 | 通过 |

JSON-03 约定：健康/启用标量在共享 Endpoint 上总有值，注册健康默认 true。
声明内容仍只持久化 uri/transport/priority/weight/metadata；声明地址返回健康与启用默认 true。
传入声明定义的状态字段不覆盖 Naming，也不进入 contentDigest。发现仍排除 disabled Runtime Endpoint。
注销读取 uri/transport，忽略返回对象携带的其他属性。state/bindings 保留既有只读来源语义。

## 执行记录

本轮实现和测试结果持续更新至 [MODEL_JSON_VALIDATION.md](MODEL_JSON_VALIDATION.md)。
本轮矩阵执行完毕：相关 UT 4992 项中 4989 通过、3 原有跳过；前端 88 项通过；外部 262 项中 236 通过、23 原有跳过、3 既有 Console 失败。两个 JSON adapter 各 8 项 Schema/往返测试通过，构建和静态检查通过。已知鉴权及故障恢复缺口单独登记。

Review 后四份公开契约统一为 `0.3.0`；版本元数据/引用调整后的 Schema 契约测试与
Jackson 2/3 复验均通过，具体结果见验证记录末节。模型行为和前述完整 IT 矩阵范围未变。

公开 Schema 目录进一步简化为 agent/、rad/、rad/watch/ 下的固定文件；协议元数据保留
0.3.0，internal/v1 保留。目录与引用调整后的复验结果见验证记录末节。
