// ===== Skill Types =====

export type SkillSearchMode = 'accurate' | 'blur';

/** Skill version status */
export type SkillVersionStatus = 'draft' | 'reviewing' | 'reviewed' | 'online' | 'offline';

/** Skill list item for admin API */
export interface SkillListItem {
  namespaceId: string;
  name: string;
  description: string;
  owner: string;
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
  writable: boolean;
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
  owner: string;
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
  writable: boolean;
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

export type SkillUploadPrecheckCode =
  | 'READY'
  | 'VERSION_ADJUSTED'
  | 'DRAFT_EXISTS'
  | 'REVIEWING_EXISTS'
  | 'NO_PERMISSION'
  | 'NOT_A_SKILL'
  | 'INVALID_SKILL';

export interface SkillUploadPrecheckResult {
  namespaceId: string;
  entryPath?: string | null;
  skillName?: string | null;
  reason?: string | null;
  owner?: string | null;
  maxPublishedVersion?: string | null;
  parsedVersion?: string | null;
  targetVersion?: string | null;
  exists: boolean;
  editingVersion?: string | null;
  reviewingVersion?: string | null;
  precheckCode: SkillUploadPrecheckCode;
}

// ===== Pipeline Types =====

export { parsePipelineInfo } from './pipeline';
export type {
  PipelineCheckpoint,
  PipelineExecutionStatus,
  PipelineNode,
  PublishPipelineInfo,
} from './pipeline';

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
