export type AgentSpecEditorMode = 'new' | 'edit' | 'version';

export interface VersionModePlanInput {
  mode: AgentSpecEditorMode;
  editingVersion: string | null;
  currentVersion?: string;
  sourceVersion?: string;
}

export interface VersionModePlan {
  versionToLoad?: string;
  shouldCreateDraft: boolean;
  basedOnVersion?: string;
}

export function planAgentSpecEditorVersionMode({
  mode,
  editingVersion,
  currentVersion,
  sourceVersion,
}: VersionModePlanInput): VersionModePlan {
  if (mode === 'new') {
    return {
      shouldCreateDraft: false,
    };
  }

  if (editingVersion) {
    return {
      versionToLoad: editingVersion,
      shouldCreateDraft: false,
    };
  }

  if (mode === 'edit') {
    return {
      versionToLoad: undefined,
      shouldCreateDraft: false,
    };
  }

  return {
    shouldCreateDraft: true,
    basedOnVersion: sourceVersion || currentVersion,
  };
}