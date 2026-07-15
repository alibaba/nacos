# Nacos Console UI 升级 - 实施进度文档

## 项目概述

将 Nacos 控制台前端从 React 16 + Webpack 升级到 React 18 + Vite + Shadcn/ui，保留全部原有功能并改善用户体验。

## 技术栈

| 分类 | 技术选型 |
|------|---------|
| 框架 | React 18 + TypeScript |
| 构建工具 | Vite 7 |
| 样式 | Tailwind CSS 4（oklch 色彩系统，蓝白主题） |
| UI 组件库 | Shadcn/ui（基于 Radix UI） |
| 状态管理 | Zustand v5（stores: auth, server, namespace, app, config, history, service） |
| 路由 | React Router v7（HashRouter, 懒加载, AuthGuard/AdminGuard/GuestGuard） |
| 国际化 | react-i18next（zh-CN / en-US） |
| HTTP 客户端 | Axios（请求/响应拦截器, Token 处理） |
| 代码编辑器 | @monaco-editor/react |

## 后端架构

- **端口 8848**（主服务器）：Admin/认证端点（`/nacos/v3/auth/*`），有 `/nacos` 上下文路径
- **端口 8080**（控制台服务器）：控制台 API 端点，**无** `/nacos` 上下文路径前缀
  - 命名空间：`/v3/console/core/namespace/*`
  - 配置管理：`/v3/console/cs/config/*`
  - 历史记录：`/v3/console/cs/history/*`
  - 服务器状态：`/v3/console/server/*`
- **端口 9080**（MCP 注册中心）：AI MCP 注册服务器

## 实施计划与进度

### 阶段 0：项目基础 [已完成]
- [x] Vite + React 18 + TypeScript 项目初始化
- [x] Tailwind CSS 4 + Shadcn/ui + 色彩系统配置
- [x] Vite 构建配置（代理、固定输出文件名）
- [x] API 客户端层（Axios 拦截器、Token 处理）
- [x] Zustand 状态管理（auth, server, namespace, app stores）
- [x] 国际化迁移（zh-CN/en-US JSON + react-i18next）
- [x] 路由系统（React Router v7 + 路由守卫）
- [x] 布局系统（可折叠侧边栏 + 顶部导航栏）
- [x] 登录 / 注册页面
- [x] 蓝白配色主题调整

### 阶段 1：配置管理 - 核心增删改查 [已完成]
- [x] 类型定义（`src/types/config.ts`）
- [x] 配置管理 API 层（`src/api/config.ts`）- 涵盖所有端点
- [x] 配置管理 Zustand store（`src/stores/config-store.ts`）
- [x] Monaco 编辑器封装组件
- [x] i18n 翻译（配置模块 32 个 key）
- [x] Shadcn/ui 组件新增：Textarea, RadioGroup, Skeleton
- [x] 配置列表页（`src/pages/configurationManagement/`）
- [x] 新建配置页（`src/pages/newconfig/`）
- [x] 配置详情页（`src/pages/configdetail/`）
- [x] 编辑配置页（`src/pages/configeditor/`）

### 阶段 2：配置管理 - 历史版本与回滚 [已完成]
- [x] ConfigHistory 类型扩展（添加 srcIp, extInfo 字段）
- [x] history-store.ts 状态管理（Zustand store，含分页、加载、详情获取）
- [x] DiffEditor.tsx 差异对比组件（基于 @monaco-editor/react DiffEditor）
- [x] i18n 翻译（history 模块 19 个 key，中英文）
- [x] 历史版本列表页（`src/pages/historyRollback/`）- 搜索、分页、差异对比弹窗
- [x] 历史版本详情页（`src/pages/historyDetail/`）- 元数据展示、灰度规则解析
- [x] 版本差异对比（Monaco DiffEditor，左侧选中版本 vs 右侧当前版本）
- [x] 回滚确认页（`src/pages/configRollback/`）- 按 opType 区分回滚逻辑与警告提示
- [x] 分页切换防闪烁优化（historyRollback + configurationManagement 两个列表页）

### 阶段 3：配置管理 - 批量操作与导入导出 [已完成]
- [x] 类型扩展（`ConflictPolicy` 冲突策略类型、`ConfigCloneItem` 克隆条目接口）
- [x] API 层扩展（`exportUrl` 导出 URL 构建、`importFile` ZIP 上传、`clone` JSON 克隆）
- [x] config-store 选择状态管理（`selectedIds: Set<string>`、`toggleSelect`、`selectAll`、`clearSelection`）
- [x] i18n 翻译（批量操作模块 26 个 key，中英文）
- [x] 配置列表多选（Checkbox 列 + 全选/反选 + 选中行高亮）
- [x] 批量操作工具栏（选中后显示：批量删除 + 导出 + 克隆 + 取消选择）
- [x] 批量删除对话框（展示待删除配置列表 + 确认）
- [x] 导出功能（导出全部 / 导出选中，通过 `window.open` 触发浏览器下载）
- [x] 导入功能（ZIP 文件上传 + 冲突策略选择：中止/跳过/覆盖）
- [x] 克隆功能（目标命名空间选择 + 冲突策略 + 配置列表展示）
- [x] Header 按钮布局：导入 + 新建配置；批量工具栏：删除 + 导出 + 克隆

### 阶段 4：配置管理 - 灰度发布与监听查询 [已完成]
- [x] 类型扩展（`ConfigListenerInfo` 监听查询响应、`ConfigBetaInfo` 灰度配置信息）
- [x] API 层扩展（`getBeta` 获取灰度配置、`publishBeta` 灰度发布、`stopBeta` 停止灰度）
- [x] i18n 翻译（listener 模块 7 个 key + beta 发布 10 个 key，中英文）
- [x] 监听查询页面重写（`src/pages/listeningToQuery/`）
  - ToggleGroup 切换查询模式（按配置查询 / 按 IP 查询）
  - 条件表单：按配置查询（DataId + Group）/ 按 IP 查询（IP 地址）
  - 结果表格：按配置查询显示（IP, MD5）/ 按 IP 查询显示（DataId, Group, MD5）
  - 客户端分页（API 返回全量结果，前端分页展示）
  - URL 参数预填充（从配置列表页跳转时自动填充 DataId 和 Group）
  - 搜索栏重置按钮（清空输入框 + 清除查询结果）
- [x] 配置编辑器灰度发布标签页（`src/pages/configeditor/`）
  - Tabs 组件（正式版 / Beta 版），Beta 标签页仅在编辑模式下显示
  - Beta 标签页切换时通过 `getBeta` API 加载现有灰度配置
  - Beta 发布：IP 输入框（逗号分隔）+ 内容编辑器 + 发布按钮
  - 停止灰度：确认对话框 + 调用 `stopBeta` API + 清除灰度状态
  - 页脚按钮根据当前标签页自适应（正式版：发布；Beta 版：灰度发布 + 停止灰度）

### 阶段 5：服务管理 [已完成]
- [x] 类型定义（`src/types/service.ts`）- ServiceView, ServiceDetailInfo, Instance, ClusterInfo, Selector, HealthChecker, SubscriberInfo 等
- [x] API 层（`src/api/service.ts`）- 10 个方法：listServices, getService, createService, updateService, deleteService, getSelectorTypes, updateCluster, listInstances, updateInstance, listSubscribers
- [x] Zustand Store（`src/stores/service-store.ts`）- 服务列表/搜索/分页/详情/选择器类型状态管理
- [x] i18n 翻译（service 模块 ~45 个 key，中英文）
- [x] 服务列表页（`src/pages/serviceManagement/`）
  - 搜索过滤（服务名、分组名、隐藏空服务 Switch）
  - 表格展示（服务名、分组名、集群数、IP 数、健康实例数、触发保护阈值）
  - 行背景色：健康实例数为 0 时红色高亮
  - 行操作：详情、订阅者、删除（确认对话框）
  - 创建服务对话框（服务名、分组名、保护阈值、临时服务、选择器类型/表达式、元数据）
  - 服务端分页
- [x] 服务详情页（`src/pages/serviceDetail/`）
  - 服务信息卡片（网格布局只读展示 + 编辑按钮）
  - 集群卡片（按 clusterMap 遍历，每个集群一个 Card）
  - 实例数据通过独立 API 获取（`v3/console/ns/instance/list`），不依赖服务详情响应的 `cluster.hosts` 字段
  - 实例表格（IP、端口、临时、权重、健康 Badge、元数据 Badge、操作）
  - 实例行背景色：健康绿色、不健康红色
  - 实例快捷上线/下线（直接切换 enabled，无需对话框）
  - 编辑服务对话框（保护阈值、临时服务、选择器、元数据）
  - 编辑集群对话框（健康检查类型 TCP/HTTP/NONE、检查端口、使用实例端口、HTTP 路径/请求头、元数据）
  - 编辑实例对话框（IP/端口只读、权重、启用、元数据）
  - 每个集群独立的服务端分页（分页变更触发 API 重新获取）
- [x] 订阅者列表页（`src/pages/subscriberList/`）
  - 搜索过滤（服务名、分组名 + 搜索/重置按钮）
  - URL 参数预填充（从服务列表页跳转时自动填充）
  - 表格展示（分组名、服务名、订阅者地址、订阅数、集群）
  - 服务端分页

### 阶段 6：AI 注册中心 [进行中]

#### MCP 服务器管理 [进行中]
- [x] MCP 类型定义（`src/types/mcp.ts`）- McpServerBasicInfo, McpServerDetailInfo, McpToolSpecification, McpVersionDetail, McpSecurityScheme 等
- [x] MCP API 层（`src/api/mcp.ts`）- listMcpServers, getMcpServer, createMcpServer, updateMcpServer, deleteMcpServer, importMcpTools, verifyMcpImport 等
- [x] MCP Zustand Store（`src/stores/mcp-store.ts`）- 列表/搜索/分页/详情/版本选择状态管理
- [x] MCP 服务器列表页（`src/pages/mcpServerManagement/`）- 搜索、分页、启用/禁用、删除
- [x] MCP 服务器详情页（`src/pages/mcpServerDetail/`）
  - Hero Header：渐变背景 + 大图标 + 协议/版本徽章 + 操作栏（版本切换、启用/禁用、复制、新版本、编辑）
  - Card 统一样式：`overflow-hidden py-0 gap-0` + 自定义 header（`px-5 py-3.5 border-b bg-muted/30`）+ 语义图标
  - 基本信息卡片：双列 `InfoCell` 网格布局
  - 端点信息卡片：前后端端点分栏展示
  - 安全方案卡片：Table 展示安全认证配置，左间距对齐修复
  - 工具列表组件（`McpToolList`）：树形参数层级展示（递归 `SchemaTreeNode`）+ 请求/响应模版 + 标注信息
  - 版本下拉 Latest 绿色徽章（`is_latest` 字段映射修复）
  - Hero Header Latest 徽章（emerald 绿色，查看最新版本时显示）
- [x] MCP 服务器编辑页（`src/pages/newMcpServer/`）
  - Hero Header：渐变背景 + 大图标，协议/版本徽章动态联动
  - Card 统一样式：与详情页一致的 `py-0 gap-0` + 自定义 header + 语义图标（Server/Terminal/Globe/Shield/Wrench）
  - 协议选择器：卡片式 Radio 按钮，三色方案（stdio=purple, SSE=blue, Streamable=cyan）+ 选中态 ring 高亮
  - 表单间距统一：`space-y-5` + `space-y-2.5`（Label-Input）
  - 安全方案编辑：Shield 图标（rose）+ 计数 Badge + 默认上下游安全配置
  - 工具管理卡片（`tool-manager/`）：Wrench 图标（amber）+ 计数 Badge + 左侧列表 `bg-muted/20` + 选中态 `bg-primary/10 ring-1`
  - 发布策略弹窗：点击发布按钮弹出 Dialog，三选项 Radio（发布新版本/设为最新版本/更新当前版本），根据版本号动态计算可用性
    - 发布新版本（emerald）：以新版本号发布，但不设为默认版本。`latest=false, overrideExisting=false`
    - 设为最新版本（blue）：设为默认版本，客户端未指定版本时自动使用。`latest=true, overrideExisting=true`
    - 更新当前版本（orange）：覆盖当前版本配置，版本号和最新标记不变。`latest=isCurrentLatest, overrideExisting=true`
    - 不可用选项灰化 + 原因提示
    - `useEffect` 自动切换：当前策略不可用时切换到第一个可用策略
  - 工具导入模式限制（`importMode` prop）：HTTP 转换仅支持 OpenAPI 导入，SSE/Streamable 支持 MCP 实例导入，Stdio 不支持导入
  - 提交按钮文案改为"发布"
  - Sticky 底部提交栏：`backdrop-blur-sm` 毛玻璃效果
  - 编辑页缓存优化：从详情页进入时优先使用 Zustand store 中的 `currentMcp` 缓存数据填充表单，避免重复 API 请求和 skeleton 闪烁
- [x] HTTP 转换标识（列表页 + 详情页）
  - McpCard 列表卡片：当 `protocol === 'http' || 'https'` 时显示琥珀色 "HTTP 转换" 徽章 + RefreshCw 图标
  - 详情页基本信息：协议 InfoCell 旁显示双色徽章（协议类型 + HTTP 转换指示器）
- [x] 页面切换闪烁优化
  - Store 层优化（`mcp-store.ts`）：`fetchMcpServers` 和 `fetchMcpDetail` 仅在无缓存数据时设置 `loading=true`，有数据时保持 UI 可见
  - 详情页清理（`mcpServerDetail`）：组件卸载时不再调用 `clearCurrentMcp()`，保留缓存数据供编辑页复用
  - 路由 Suspense fallback（`routes.tsx`）：PageLoading 从全屏 `min-h-screen` 缩小为 `py-32`，减少视觉跳动
  - 编辑页缓存命中：检测 store 中 `currentMcp.name === editMcpName` 时跳过 API 请求，`initLoading` 初始为 `false`
- [ ] MCP 服务器列表页视觉重设计（对齐详情页风格）

#### Agent 管理 [进行中]
- [x] Agent 类型定义（`src/types/agent.ts`）- AgentBasicInfo（对齐后端 AgentCardVersionInfo）, AgentDetailInfo（对齐后端 AgentCardDetailInfo）, AgentSkill, AgentCapabilities, AgentProvider, AgentVersionDetail 等
- [x] Agent API 层（`src/api/agent.ts`）- listAgents, getAgent, createAgent, updateAgent, deleteAgent, getVersionList
- [x] Agent Zustand Store（`src/stores/agent-store.ts`）- 列表/搜索/分页/详情/版本/选择状态管理
- [x] Agent 列表页（`src/pages/agentManagement/`）- 卡片网格布局、搜索、分页、批量删除
- [x] Agent 列表卡片（`src/components/ai/agent/AgentCard.tsx`）
  - 图标渲染：iconUrl 存在时白色背景展示图片，加载失败回退为 Bot 图标 + 紫色渐变背景
  - 展示信息：名称、版本号（latestPublishedVersion）、描述（2行截断）、能力标签（streaming/pushNotifications/stateTransitionHistory）、Skill 数量
  - 清理死代码：移除列表接口不返回的 transport/url 渲染逻辑
  - Footer 操作按钮：详情、编辑、删除
- [x] Agent 详情页（`src/pages/agentDetail/`）- Hero Header + 基本信息 + 能力 + Skills + Provider + I/O 模式 + 附加接口
- [x] Agent 编辑/创建页（`src/pages/newAgent/`）
  - 基本信息表单：名称、版本、URL、传输协议、协议版本、描述
  - iconUrl 输入框内联图片预览
  - Skills 编辑（JSON 编辑器）
  - 高级配置：Provider、输入/输出模式、文档 URL、Extended Card Support
  - 发布策略统一：与 MCP Server 一致的底部工具栏 + 策略选择 Dialog
    - 发布新版本（emerald）：`setAsLatest=false`
    - 设为最新版本（blue）：`setAsLatest=true`
    - 更新当前版本（orange）：保持原有 latest 状态
    - `strategyAvailability` useMemo 动态计算可用性，不可用选项灰化 + 原因提示
- [x] Agent 类型对齐后端模型
  - `AgentBasicInfo` 移除列表接口不返回的字段（url, preferredTransport, defaultInputModes, defaultOutputModes, provider），新增 versionDetails, registrationType
  - `AgentDetailInfo` 承接详情接口专属字段，新增 latestVersion
- [x] MCP Import API 修复：`ImportMcpToolsDialog.tsx` 中 `response.data.data` 改为 `response.data`（axios 拦截器已解包一层）
- [x] 工具列表溢出修复：`ToolList.tsx` 用原生 `div overflow-y-auto overflow-x-hidden` 替换 Radix ScrollArea（其 min-width:100% 导致内容宽度不受约束）

#### 其他 AI 模块 [待开发]
- [ ] Prompt 管理（发布、版本管理、标签绑定、回滚）
- [ ] Skill 管理（增删改查 + 文件上传）

### 阶段 7：平台管理 [待开发]
- [ ] 命名空间管理
- [ ] 集群节点管理
- [ ] 插件管理
- [ ] 权限与角色管理（RBAC）

## 遇到的问题与解决方案

### 问题 1：登录提示"用户名密码错误"
**现象**：登录始终返回错误，同时弹出两个错误提示。

**根因**：
1. Vite 代理将所有 `/nacos/*` 转发到 8080 端口，但登录接口（`/nacos/v3/auth/user/login`）实际在 8848 端口。
2. Axios 响应拦截器已经做了 `return response.data`（解包 HTTP body），但 auth-store 又做了一次 `response.data`（双重解包）。

**修复**：
1. 为 `/nacos/v1/auth` 和 `/nacos/v3/auth` 添加单独的代理规则，指向 8848 端口。
2. 修复 auth-store，使用 `response as unknown as TokenData` 代替 `response.data`。

### 问题 2：配置列表页无数据
**现象**：配置管理页面加载成功但表格显示"暂无数据"。

**根因**（3 个 bug）：
1. **Vite 代理路径不匹配**：控制台服务器（8080 端口）的 `contextPath` 为空（`nacos.console.contextPath=`），API 路径是 `/v3/console/*` 而非 `/nacos/v3/console/*`。代理原样转发导致 8080 将请求当作静态资源处理，返回 "No static resource" 错误。
2. **namespace-store 双重解包**：拦截器已返回 HTTP body，store 还做了 `response.data?.data`（多了一层 `.data`），导致 `namespaces = []`，命名空间始终为空。
3. **server-store 类型不匹配**：服务器状态 API 返回字符串值（如 `"true"`, `"30"`）而非布尔/数字类型，store 直接使用导致类型判断失败。

**修复**：
1. 在 Vite 代理规则中为 8080 端口添加 `rewrite: (path) => path.replace(/^\/nacos/, '')`，去掉 `/nacos` 前缀。
2. 修改 namespace-store 为 `const body = response as unknown as { code: number; data: Namespace[] }; const namespaces = body.data || []`。
3. 修改 server-store，使用 `String(val) === 'true'` 做布尔转换，`Number(val)` 做数值转换。

### 问题 3：Shadcn/ui 组件安装失败
**现象**：`npx shadcn@latest add` 因 TLS 证书错误失败。

**修复**：手动创建组件文件（textarea.tsx, radio-group.tsx, skeleton.tsx），按照 Shadcn/ui 规范使用 @radix-ui 原语实现。

### 问题 4：配置列表页 UI 问题（用户 review 反馈）
**现象**（4 项）：
1. 搜索栏上方有不明空白。
2. 列表页左右元素紧贴边缘，无留白。
3. 每页 10 条翻到后面，切换为每页 50 条时超出页数范围，显示无数据且无法回退。
4. 没有命名空间切换入口。

**根因**：
1. Card 组件默认 `py-6` + CardContent `pt-6` = 双重 top padding（48px）；同时 AppLayout `<main p-6>` 和页面自身 `p-6` 也是双重 padding。
2. 表格 Card 使用 `p-0` 移除了所有内边距，表头和数据行紧贴卡片边缘。
3. 切换 pageSize 时传入当前 pageNo（可能已超出新的总页数），且分页器仅在 `configs.length > 0` 时显示。
4. Header 组件左侧为空，未放置命名空间选择器。

**修复**：
1. 搜索 Card 改为 `py-0` + CardContent `py-4`；移除所有页面级 `p-6`（由 AppLayout 统一提供）。
2. 表格首列增加 `pl-6`，尾列增加 `pr-6`（表头和数据行均添加）。
3. 切换 pageSize 时强制 `setPage(1, newSize)` 重置到第 1 页；分页器显示条件改为 `total > 0`。
4. 在 Header 左侧添加命名空间 Select 选择器 + "命名空间" 文字标签，切换后触发配置列表重新加载。

### 问题 5：列表页分页切换闪烁
**现象**：配置列表页和历史版本列表页切换每页条数时，整个表格区域闪烁（先显示 Skeleton 再显示数据）。

**根因**：加载状态触发了条件渲染，将整个表格替换为 Skeleton 组件，导致布局位移。

**修复**：
1. 仅在 `loading && list.length === 0`（首次加载无数据）时显示 Skeleton。
2. 分页加载时保持表格可见，通过 `opacity-50 pointer-events-none` 表示加载中状态。
3. 分页器显示条件改为 `total > 0`（不再受 loading 状态影响）。
4. 该修复同时应用于 `configurationManagement` 和 `historyRollback` 两个列表页。

### 问题 6：服务详情页实例不显示
**现象**：服务列表页显示 IP 数 = 1，但点进详情页后集群卡片中无实例数据。

**根因**：初始实现直接使用服务详情 API 响应的 `cluster.hosts` 字段渲染实例，但该字段可能为空或不完整。旧版代码中每个集群的实例是通过独立的 `InstanceTable` 组件单独调用 `v3/console/ns/instance/list` API 获取的。

**修复**：
1. 新增 `instancesByCluster` 状态，独立存储每个集群的实例数据。
2. 新增 `fetchClusterInstances()` 回调，按集群名调用 `serviceApi.listInstances()` 获取实例列表（带分页参数）。
3. `useEffect` 监听 `currentService` 变化，服务详情加载后自动为每个集群发起实例获取请求。
4. `ClusterCard` 组件改为接收 `instances`、`instanceTotal`、`instanceLoading` props，不再使用 `cluster.hosts`。
5. 分页切换时直接将新分页参数传给 `fetchClusterInstances()`，避免 `setTimeout` hack。

### 问题 7：McpVersionDetail 字段名不匹配导致 Latest 标记不显示
**现象**：详情页版本下拉和 Hero Header 中的 Latest 徽章始终不显示。

**根因**：后端 Java 类 `ServerVersionDetail` 的字段名为 `is_latest`（snake_case），Jackson 序列化后 JSON 也是 `is_latest`。但前端 TypeScript `McpVersionDetail` 接口定义为 `isLatest`（camelCase），导致字段永远为 `undefined`。

**修复**：将 `McpVersionDetail` 接口的字段名从 `isLatest` 改为 `is_latest`，同步更新详情页和编辑页所有引用（4 处）。

### 已知限制：非持久化实例删除

后端 `ConsoleInstanceController`（`/v3/console/ns/instance`）目前仅提供 `list` 和 `update` 两个端点，**没有 DELETE 方法**。Admin API（`/v3/admin/ns/instance`）有 `deregister` 端点，但属于内部/SDK 接口，Console 前端不应直接调用。

**待办**：后端在 `ConsoleInstanceController` 中添加 DELETE 端点后，前端再实现非持久化实例删除功能（删除按钮 + 确认对话框）。

1. **响应拦截器模式**：Axios 拦截器执行 `return response.data`，所有 API 调用结果已经是 HTTP body。Store 层必须正确类型转换：对于标准包装响应（`{ code, data }`）使用 `response as unknown as { code: number; data: T }`，对于扁平响应（如服务器状态）使用 `response as unknown as T`。

2. **命名空间 ID 约定**：Nacos v3 使用 `"public"` 作为公共命名空间 ID（不是空字符串）。namespace store 初始化时默认为 `""`，从 API 获取后更新为 `"public"`。

3. **双端口代理策略**：认证端点 → 8848（保留 `/nacos` 前缀），控制台端点 → 8080（通过 rewrite 去掉 `/nacos` 前缀）。

4. **旧代码调研原则**：实现每个模块前，充分调研旧版 `console-ui/` React 16 代码库，了解 API 结构、数据流和 UI 交互模式。

## 项目文件结构

```
console-ui-next/
├── src/
│   ├── api/           # API 层（client.ts, auth.ts, config.ts, namespace.ts, server.ts, service.ts）
│   ├── components/
│   │   ├── config/    # 配置相关组件（MonacoEditor.tsx, DiffEditor.tsx）
│   │   ├── layout/    # 布局组件（sidebar.tsx, header.tsx）
│   │   └── ui/        # Shadcn/ui 组件（21+ 个）
│   ├── layouts/       # 应用布局（AppLayout.tsx）
│   ├── locales/       # 国际化 JSON 文件（zh-CN.json, en-US.json）
│   ├── pages/         # 页面组件
│   │   ├── configurationManagement/  # 配置列表
│   │   ├── newconfig/                # 新建配置
│   │   ├── configdetail/             # 配置详情
│   │   ├── configeditor/             # 编辑配置
│   │   ├── historyRollback/          # 历史版本列表 + 差异对比
│   │   ├── historyDetail/            # 历史版本详情
│   │   ├── configRollback/           # 回滚确认
│   │   ├── listeningToQuery/          # 监听查询（按配置/按 IP）
│   │   ├── serviceManagement/          # 服务列表
│   │   ├── serviceDetail/              # 服务详情（集群卡片 + 实例表格）
│   │   ├── subscriberList/             # 订阅者列表
│   │   ├── login/                    # 登录页
│   │   ├── register/                 # 注册页
│   │   └── welcome/                  # 欢迎/首页
│   ├── router/        # 路由与守卫
│   ├── stores/        # Zustand stores（auth, server, namespace, app, config, history, service）
│   └── types/         # TypeScript 类型定义
├── vite.config.ts     # Vite 配置（含双端口代理）
├── tailwind.config.ts
└── package.json
```

## 代码与 UI 风格规范

### 组件模式
- 全部使用函数式组件 + Hooks，`export default` 导出页面主组件
- 局部状态用 `useState`，副作用用 `useEffect`，回调优化用 `useCallback`
- 从 Zustand store 中解构获取状态和 action

### 样式系统
- **色彩体系**：Tailwind CSS 4 + oklch 格式，CSS 变量定义在 `globals.css`
  - 主色（Primary）：蓝色 `oklch(0.55 0.18 240)`
  - 辅色（Secondary）：`oklch(0.95 0.01 230)`
  - 破坏性操作（Destructive）：红色 `oklch(0.6 0.22 20)`
  - 侧边栏专用色：`--sidebar-background`, `--sidebar-primary` 等
- **圆角半径**：`--radius: 0.75rem`（12px），衍生 `-sm`, `-md`, `-lg`, `-xl`
- **整体风格**：蓝白主色调，现代简洁，充分留白，微妙动画

### 布局模式
- 顶部固定导航（含命名空间切换 + 商业化引流链接）+ 可折叠侧边栏
- AppLayout 的 `<main p-6>` 统一提供页面内边距，页面组件不再重复添加 padding
- Card 组件包装主要内容区域，搜索区域用 `py-0` 紧凑样式，表格区域首尾列加 `pl-6`/`pr-6`
- 响应式使用 `grid grid-cols-1 md:grid-cols-2` 等

### 表单模式
- 结构：`<div className="space-y-2"><Label /><Input /></div>`
- 必填字段：红色星号 `<span className="text-destructive ml-1">*</span>`
- Button 变体：`default`（主操作）、`outline`（次要）、`ghost`（文字）、`destructive`（删除）
- 搜索区域：`flex flex-wrap items-end gap-4`

### Zustand Store 模式
- 分离 `State` 和 `Actions` 接口定义，合并为 `Store = State & Actions`
- `set()` 更新状态，`get()` 读取当前状态
- API 调用在 action 中完成，`loading`/`error` 统一管理
- 响应处理：`response as unknown as { code: number; data: T }` 正确类型转换

### API 调用模式
- 统一定义在 `src/api/` 下，对象字面量风格：`export const xxxApi = { list, get, create, ... }`
- POST 数据自动序列化为 `application/x-www-form-urlencoded`（通过 qs 库）
- 错误处理：拦截器中 toast 提示，401/403 跳转登录

### 国际化模式
- `const { t } = useTranslation()`，键名格式 `domain.key`（如 `config.dataId`）
- 所有用户可见文本必须通过 `t()` 翻译

### 图标与 UI 组件
- 图标：lucide-react（`Plus`, `Search`, `MoreHorizontal`, `ChevronLeft` 等）
- Toast 通知：sonner 库（`toast.error()`, `toast.success()`）
- 代码编辑器：@monaco-editor/react，自定义封装支持语言切换和只读模式
- Dialog：Shadcn/ui `Dialog` 用于确认操作
- DropdownMenu：Shadcn/ui `DropdownMenu` 用于表格行操作菜单

### 命名规范
- 文件名：kebab-case（`config-store.ts`），组件文件用 PascalCase（`MonacoEditor.tsx`）
- 组件名：PascalCase，常量：UPPER_SNAKE_CASE
- 导入别名：`@/` 指向 `src/`（如 `@/components`, `@/stores`, `@/api`）

## 当前状态

**阶段 6（AI 注册中心）MCP 服务器管理与 Agent 管理进行中。**

MCP 服务器列表、详情、编辑/创建页面已完成核心功能与视觉重设计。详情页包含 Hero Header、基本信息、端点、安全方案、工具列表（树形参数+模版）、HTTP 转换标识等完整展示。编辑页包含协议选择器（卡片式三色方案）、发布策略弹窗（点击发布按钮触发 Dialog 选择策略）、工具管理（按协议类型限制导入方式）、安全方案编辑等完整编辑功能。

Agent 管理已完成列表、详情、编辑/创建页面核心功能。列表卡片展示名称、版本、描述、能力标签、Skill 数量，支持 iconUrl 图片渲染（白色背景 + 加载失败回退）。编辑页发布逻辑已统一为与 MCP Server 一致的底部工具栏 + 三选项策略 Dialog 模式。TypeScript 类型已对齐后端 Java 模型（AgentBasicInfo 对应 AgentCardVersionInfo，AgentDetailInfo 对应 AgentCardDetailInfo）。

版本管理统一支持发布新版本、设为最新版本、更新当前版本三种语义清晰的操作。页面切换和命名空间切换已优化为无闪烁体验（store 缓存 + 按需 loading）。侧边栏收起态交互已重设计，用 Popover flyout 替代 Tooltip 实现可交互的子菜单面板。

### UI / 品牌优化 [已完成]
- [x] **顶部栏商业化导航链接**：添加 Nacos 官网、企业版（HOT）、MCP 市场（HOT）、文档、博客、社区 6 个外部链接，支持中英文 URL 自动切换，含 SPM 埋点参数
- [x] **顶部栏样式调整**：`bg-background/80 backdrop-blur-md` 半透明模糊背景，导航链接居中排列，左侧命名空间选择器，右侧语言/主题/用户操作
- [x] **侧边栏 Logo 品牌化**：展开状态显示 `nacos-logo-dark.svg` 完整 Logo，收缩状态显示蓝色无穷符号图标（`nacos-icon.png`）
- [x] **侧边栏版本信息**：版本号和运行模式移至底部设置按钮上方独立展示
- [x] **侧边栏收起态交互重设计**：
  - NavGroup（带子菜单的分组项）：用 `Popover` 替换 `Tooltip`，hover 触发 flyout 面板（120ms 延迟关闭），面板包含分组标题 + Separator + 带图标的子菜单项列表，当前活跃项高亮，点击子项导航并自动关闭
  - NavLink（单项）：Tooltip 样式从深色 `bg-primary` 改为卡片风格（`bg-popover` + border + shadow），与 flyout 面板视觉统一
  - Logo Tooltip：同步更新为卡片风格
- [x] **浏览器 Favicon**：使用原版 Nacos 蓝色无穷符号图标（从 `console-ui` 原始资源复制），与 Nacos 官网保持一致
- [x] **新建配置页布局优化**：重构为 Card 布局，CardHeader 标题 + CardContent 表单 + 外部页脚，Select 组件响应式宽度
- [x] **表单输入框边框优化**：`--input` 颜色从 `oklch(0.91 0.008 230)` 调深至 `oklch(0.82 0.012 230)`，保持 `bg-transparent` 背景
- [x] **全局 Card 边框阴影优化**：基础 Card 组件 `shadow-sm` 改为极轻阴影 `shadow-[0_1px_2px_0_rgba(0,0,0,0.03)]`，`border` 改为 `border-border/60`；McpCard 悬停效果从 `hover:shadow-md` 降为 `hover:shadow-sm`，边框从 `hover:border-primary/30` 降为 `hover:border-primary/20`
- [x] **Sticky 底部工具栏修复**：MCP Server 和 Agent 编辑页底部工具栏添加 `-mb-6`，解决滚动到底时工具栏上移问题（main 元素的 `p-6` 底部 padding 将 sticky 元素推离视口底部）
