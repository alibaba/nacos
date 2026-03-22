// ===== AgentSpec Types =====

/** 列表项 */
export interface AgentSpecListItem {
  namespaceId: string;
  name: string;
  description: string;
  enable: boolean;
  bizTags: string; // JSON string: ["tag1","tag2"]
  labels: Record<string, string>; // e.g. {"latest":"v3","stable":"v2"}
  editingVersion: string | null;
  reviewingVersion: string | null;
  onlineCnt: number;
  updateTime: number; // epoch millis
}

/** 版本摘要 */
export interface AgentSpecVersionSummary {
  version: string;
  status: 'draft' | 'reviewing' | 'online' | 'offline';
  author: string;
  description: string;
  createTime: number;
  updateTime: number;
  publishPipelineInfo: string | null;
}

/** 资源 */
export interface AgentSpecResource {
  name: string;
  type: string;
  content: string;
  metadata: Record<string, unknown> | null;
}

export interface AgentSpecDocument {
  namespaceId: string;
  name: string;
  description: string;
  content: string;
  resource: Record<string, AgentSpecResource>;
}

/** 详情 */
export interface AgentSpecDetail {
  agentSpec: AgentSpecDocument | null;
  enable: boolean;
  version: string;
  versionStatus: 'draft' | 'reviewing' | 'online' | 'offline';
  editingVersion: string | null;
  reviewingVersion: string | null;
  labels: Record<string, string>;
  onlineCnt: number;
  updateTime: number;
  versions: AgentSpecVersionSummary[];
}

/** 列表响应 */
export interface AgentSpecListResponse {
  totalCount: number;
  pageItems: AgentSpecListItem[];
}

/** 列表查询参数 */
export interface AgentSpecListParams {
  namespaceId?: string;
  agentSpecName?: string;
  search?: string;
  pageNo?: number;
  pageSize?: number;
}
