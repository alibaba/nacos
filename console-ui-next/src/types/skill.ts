// ===== Skill Types =====

export type SkillSearchMode = 'accurate' | 'blur';

/** Skill version status */
export type SkillVersionStatus = 'draft' | 'reviewing' | 'online' | 'offline';

/** Skill list item for admin API */
export interface SkillListItem {
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
  downloadCount: number;
}

/** Skill version summary */
export interface SkillVersionSummary {
  version: string;
  status: SkillVersionStatus;
  author: string;
  description: string;
  createTime: number;
  updateTime: number;
  publishPipelineInfo: string | null;
  downloadCount: number;
}

/** Skill resource */
export interface SkillResource {
  name: string;
  type: string;
  content: string;
  metadata: Record<string, unknown> | null;
}

/** Full skill content (version detail) */
export interface SkillDocument {
  namespaceId: string;
  name: string;
  description: string;
  instruction: string;
  resource: Record<string, SkillResource>;
}

/** Skill admin detail */
export interface SkillAdminDetail {
  enable: boolean;
  editingVersion: string | null;
  reviewingVersion: string | null;
  labels: Record<string, string>;
  onlineCnt: number;
  updateTime: number;
  versions: SkillVersionSummary[];
  downloadCount: number;
}

/** List response */
export interface SkillListResponse {
  totalCount: number;
  pageItems: SkillListItem[];
}

/** List params */
export interface SkillListParams {
  namespaceId?: string;
  skillName?: string;
  search?: SkillSearchMode;
  orderBy?: string;
  pageNo?: number;
  pageSize?: number;
}

// ===== Pipeline Types =====

export type PipelineExecutionStatus = 'IN_PROGRESS' | 'APPROVED' | 'REJECTED';

/** Single pipeline node execution result */
export interface PipelineNode {
  nodeId: string;
  executedAt?: string; // ISO 8601
  passed: boolean;
  message?: string;
  durationMs?: number;
}

/** Pipeline execution info stored in publishPipelineInfo JSON */
export interface PublishPipelineInfo {
  executionId: string;
  status: PipelineExecutionStatus;
  pipeline: PipelineNode[];
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
