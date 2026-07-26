// ===== Prompt Types =====

export type PromptSearchMode = 'accurate' | 'blur';

// ===== Prompt Version Status =====

export type PromptVersionStatus = 'draft' | 'reviewing' | 'reviewed' | 'online' | 'offline';

// ===== Prompt Variable =====

export interface PromptVariable {
  name: string;
  defaultValue: string;
  description: string;
}

// ===== Prompt Meta Summary (list item) =====

export interface PromptMetaSummary {
  schemaVersion: number;
  promptKey: string;
  description: string;
  bizTags: string[]; // parsed list of biz tags
  bizTagsStr: string; // raw biz tags string for console-ui compatibility
  latestVersion: string;
  gmtModified: number;
  editingVersion: string | null;
  reviewingVersion: string | null;
  onlineCnt: number;
  labels: Record<string, string>;
  downloadCount: number | null;
}

// ===== Prompt Meta Info (governance detail) =====

export interface PromptMetaInfo extends PromptMetaSummary {
  versions: string[];
  versionDetails: PromptVersionSummary[];
}

// ===== Prompt Version Summary =====

export interface PromptVersionSummary {
  promptKey: string;
  version: string;
  status: PromptVersionStatus;
  commitMsg: string;
  srcUser: string;
  gmtModified: number;
  publishPipelineInfo: string | null;
  downloadCount: number | null;
}

// ===== Prompt Version Info (full detail) =====

export interface PromptVersionInfo extends PromptVersionSummary {
  template: string;
  md5: string;
  variables: PromptVariable[];
}

// ===== List Request/Response =====

export interface PromptListParams {
  promptKey?: string;
  namespaceId?: string;
  search?: PromptSearchMode;
  pageNo?: number;
  pageSize?: number;
}

export interface PromptListResponse {
  pageNo: number;
  pageSize: number;
  totalCount: number;
  pagesAvailable: number;
  pageItems: PromptMetaSummary[];
}

// ===== Version List Response =====

export interface PromptVersionListResponse {
  pageNo: number;
  pageSize: number;
  totalCount: number;
  pagesAvailable: number;
  pageItems: PromptVersionSummary[];
}

// ===== Lifecycle API Request Types =====

export interface PromptDraftCreateData {
  promptKey: string;
  template?: string;
  variables?: string;
  commitMsg?: string;
  description?: string;
  bizTags?: string;
  basedOnVersion?: string;
  targetVersion?: string;
  namespaceId?: string;
}

export interface PromptDraftUpdateData {
  promptKey: string;
  template: string;
  variables?: string;
  commitMsg?: string;
  namespaceId?: string;
}

export interface PromptSubmitData {
  promptKey: string;
  version?: string;
  namespaceId?: string;
}

export interface PromptPublishData {
  promptKey: string;
  version: string;
  namespaceId?: string;
}

export interface PromptOnlineOfflineData {
  promptKey: string;
  version: string;
  namespaceId?: string;
}

export interface PromptLabelsUpdateData {
  promptKey: string;
  labels: string; // JSON string
  namespaceId?: string;
}

export interface PromptDescriptionUpdateData {
  promptKey: string;
  description: string;
  namespaceId?: string;
}

export interface PromptBizTagsUpdateData {
  promptKey: string;
  bizTags: string;
  namespaceId?: string;
}

/** Safely parse bizTags string into an array (handles JSON array, comma-separated, or plain string) */
export function parseBizTags(raw: string | null | undefined): string[] {
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === 'string') : [];
  } catch {
    // fallback: treat as comma-separated
    return raw.split(',').map((s) => s.trim()).filter(Boolean);
  }
}
