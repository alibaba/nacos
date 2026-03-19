// ===== Prompt Types =====

export type PromptSearchMode = 'accurate' | 'blur';

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
  bizTags: string[];
  latestVersion: string;
  gmtModified: number;
}

// ===== Prompt Meta Info (detail metadata) =====

export interface PromptMetaInfo extends PromptMetaSummary {
  versions: string[];
  labels: Record<string, string>; // label -> version mapping
}

// ===== Prompt Version Summary =====

export interface PromptVersionSummary {
  promptKey: string;
  version: string;
  commitMsg: string;
  srcUser: string;
  gmtModified: number;
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

// ===== Publish Data =====

export interface PromptPublishData {
  promptKey: string;
  version: string;
  template: string;
  commitMsg?: string;
  description?: string;
  bizTags?: string;
  variables?: string; // JSON string of PromptVariable[]
  namespaceId?: string;
}

// ===== Update Metadata =====

export interface PromptUpdateMetadataData {
  promptKey: string;
  description?: string;
  bizTags?: string;
  namespaceId?: string;
}

// ===== Label Bind =====

export interface PromptLabelBindData {
  promptKey: string;
  label: string;
  version: string;
  namespaceId?: string;
}
