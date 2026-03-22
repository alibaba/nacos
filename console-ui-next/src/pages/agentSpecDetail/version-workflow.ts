export interface AgentSpecEditorNavigationInput {
  mode: 'edit' | 'version';
  name: string;
  namespaceId: string;
  sourceVersion?: string;
}

export function buildAgentSpecEditorSearch({
  mode,
  name,
  namespaceId,
  sourceVersion,
}: AgentSpecEditorNavigationInput): string {
  const params = new URLSearchParams({
    mode,
    name,
    namespaceId,
  });

  if (mode === 'version' && sourceVersion) {
    params.set('sourceVersion', sourceVersion);
  }

  return params.toString();
}