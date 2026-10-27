/** Hub ids and optimistic meta versions are serialized by Java as strings. */
export type HubId = string;

export interface HubSkillSummary {
  hubEntryId: HubId;
  displayName: string;
  sourceDisplayName?: string | null;
  author?: string | null;
  description?: string | null;
  sharedVersion: string;
  tags?: string[] | null;
  sharedAt?: number | null;
  /** Downloads through this shared entry, independent of source Skill counters. */
  downloadCount?: number | null;
  origin: 'HUB';
}

export interface HubSkillDetail extends HubSkillSummary {
  /** Metadata of the immutable shared source version. */
  versionStatus?: string | null;
  /** Shared entry downloads for the pinned version, independent of source version counters. */
  versionDownloadCount?: number | null;
  commitMsg?: string | null;
  versionLabels?: string[] | null;
  sourceReadable: boolean;
  /** Provided only after the server checks independent source READ. */
  sourceNamespaceId?: string | null;
  sourceName?: string | null;
}

export interface HubSkillResource {
  name: string;
  type: string;
  content: string;
}

export interface HubSkillContent {
  hubEntryId: HubId;
  displayName: string;
  sharedVersion: string;
  skillMd: string;
  resource: Record<string, HubSkillResource>;
}

export interface HubSkillPage {
  totalCount: number;
  pageItems: HubSkillSummary[];
}

export interface HubShareState {
  hubEntryId: HubId;
  metaVersion: HubId;
  sharedVersion: string;
  active: boolean;
}

export interface HubShareResult {
  hubEntryId: HubId;
  metaVersion: HubId;
  sourceVersion: string;
  outcome: string;
  active: boolean;
}

export interface HubTransferResult {
  targetNamespaceId: string;
  targetName: string;
  draftVersion: string;
  status: string;
  origin: string;
  targetResourceId: HubId;
  hubEntryId?: HubId | null;
}

export interface SkillOriginSummary {
  sourceName: string;
  sourceVersion: string;
  hubEntryId?: HubId | null;
  importedAt?: string | null;
  mode: 'HUB_IMPORT' | 'DIRECT_COPY';
}
