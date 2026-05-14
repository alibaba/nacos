// ===== AgentSpec Types =====

export type AgentSpecVersionStatus = 'draft' | 'reviewing' | 'reviewed' | 'online' | 'offline';

/** 列表项 */
export interface AgentSpecListItem {
  namespaceId: string;
  name: string;
  description: string;
  enable: boolean;
  scope: string; // "PUBLIC" or "PRIVATE"
  bizTags: string; // JSON string: ["tag1","tag2"]
  from: string;
  labels: Record<string, string>; // e.g. {"latest":"v3","stable":"v2"}
  editingVersion: string | null;
  reviewingVersion: string | null;
  onlineCnt: number;
  updateTime: number; // epoch millis
  downloadCount: number;
}

/** 版本摘要 */
export interface AgentSpecVersionSummary {
  version: string;
  status: AgentSpecVersionStatus;
  author: string;
  description: string;
  createTime: number;
  updateTime: number;
  publishPipelineInfo: string | null;
  downloadCount: number;
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
  enable: boolean;
  scope: string; // "PUBLIC" or "PRIVATE"
  bizTags: string; // JSON string: ["tag1","tag2"]
  from: string;
  editingVersion: string | null;
  reviewingVersion: string | null;
  labels: Record<string, string>;
  onlineCnt: number;
  updateTime: number;
  versions: AgentSpecVersionSummary[];
  downloadCount: number;
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
  /** Sort field (e.g. "download_count"). Empty = default sort by gmt_modified. */
  orderBy?: string;
  /** Filter by resource owner. Admin: any value; non-admin: own username only. */
  owner?: string;
  /** Filter by visibility scope: "PUBLIC" or "PRIVATE". Empty = no filter. */
  scope?: string;
  pageNo?: number;
  pageSize?: number;
}

// ===== Pipeline Types =====

export type PipelineExecutionStatus = 'IN_PROGRESS' | 'APPROVED' | 'REJECTED';

/** Per-criterion audit checkpoint from a pipeline plugin. */
export interface PipelineCheckpoint {
  title: string;
  passed: boolean;
}

/** Single pipeline node execution result */
export interface PipelineNode {
  nodeId: string;
  executedAt?: string;
  passed: boolean;
  message?: string;
  /** Semantic type of message: text | json | markdown | html */
  messageType?: string;
  /** Per-criterion audit outcomes from the pipeline plugin */
  checkpoints?: PipelineCheckpoint[];
  durationMs?: number;
}

/** Pipeline execution info stored in publishPipelineInfo JSON */
export interface PublishPipelineInfo {
  executionId: string;
  status: PipelineExecutionStatus;
  pipeline: PipelineNode[];
  historical?: boolean;
}

/** Safely parse publishPipelineInfo JSON string */
export function parsePipelineInfo(raw: string | null | undefined): PublishPipelineInfo | null {
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw);
    if (parsed && typeof parsed.executionId === 'string' && typeof parsed.status === 'string') {
      return parsed as PublishPipelineInfo;
    }
    return null;
  } catch {
    return null;
  }
}

/** Safely parse bizTags JSON string */
export function parseBizTags(raw: string | null | undefined): string[] {
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === 'string') : [];
  } catch {
    return [];
  }
}
