import type { AgentSpecResource } from '@/types/agentspec';
import type { FileTreeNode } from './FileTreePanel';

const MANIFEST_KEY = 'manifest.json';

const FOLDER_ORDER: Record<string, number> = {
  config: 0,
  skill: 1,
  cron: 2,
  dockerfile: 3,
  other: 4,
};

/**
 * Build a file tree from AgentSpec resources.
 * - manifest.json is always the root node
 * - Resources are grouped by type into virtual folders
 * - Empty folders are filtered out
 */
export function buildFileTree(
  resources: Record<string, AgentSpecResource>,
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  _content: string,
): FileTreeNode[] {
  const nodes: FileTreeNode[] = [];

  // manifest.json is always the root node
  nodes.push({
    key: MANIFEST_KEY,
    name: MANIFEST_KEY,
    type: 'file',
  });

  // Group resources by type into virtual folders
  const folderMap = new Map<string, FileTreeNode[]>();

  for (const resource of Object.values(resources)) {
    const folderType = resource.type || 'other';
    if (!folderMap.has(folderType)) {
      folderMap.set(folderType, []);
    }
    folderMap.get(folderType)!.push({
      key: `${folderType}/${resource.name}`,
      name: resource.name,
      type: 'file',
      resourceType: folderType,
    });
  }

  // Sort folders by predefined order, then add non-empty folders
  const sortedTypes = [...folderMap.keys()].sort(
    (a, b) => (FOLDER_ORDER[a] ?? 99) - (FOLDER_ORDER[b] ?? 99),
  );

  for (const folderType of sortedTypes) {
    const children = folderMap.get(folderType)!;
    if (children.length > 0) {
      nodes.push({
        key: `${folderType}/`,
        name: `${folderType}/`,
        type: 'folder',
        children: children.sort((a, b) => a.name.localeCompare(b.name)),
        resourceType: folderType,
      });
    }
  }

  return nodes;
}
