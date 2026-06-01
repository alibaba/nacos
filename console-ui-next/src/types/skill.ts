// ===== Skill Types =====

export type SkillSearchMode = 'accurate' | 'blur';

/** Skill version status */
export type SkillVersionStatus = 'draft' | 'reviewing' | 'reviewed' | 'online' | 'offline';

/** Skill list item for admin API */
export interface SkillListItem {
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

/** Skill version summary */
export interface SkillVersionSummary {
  version: string;
  status: SkillVersionStatus;
  author: string;
  commitMsg: string;
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
  skillMd: string;
  resource: Record<string, SkillResource>;
}

/** Skill admin detail */
export interface SkillAdminDetail {
  enable: boolean;
  scope: string; // "PUBLIC" or "PRIVATE"
  bizTags: string; // JSON string: ["tag1","tag2"]
  from: string;
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
  /** Filter by resource owner. Admin: any value; non-admin: own username only. */
  owner?: string;
  /** Filter by visibility scope: "PUBLIC" or "PRIVATE". Empty = no filter. */
  scope?: string;
  /** Filter by business tag (fuzzy match on bizTags). Empty = no filter. */
  bizTag?: string;
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
  executedAt?: string; // ISO 8601
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
