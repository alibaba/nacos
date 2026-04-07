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

interface MutableFolderNode extends FileTreeNode {
  type: 'folder';
  children: FileTreeNode[];
}

function createFolderNode(
  key: string,
  name: string,
  resourceType: string,
): MutableFolderNode {
  return {
    key,
    name,
    type: 'folder',
    children: [],
    resourceType,
  };
}

function sortTree(nodes: FileTreeNode[]): FileTreeNode[] {
  return [...nodes]
    .sort((left, right) => {
      if (left.type !== right.type) {
        return left.type === 'folder' ? -1 : 1;
      }
      return left.name.localeCompare(right.name);
    })
    .map((node) => {
      if (node.type === 'folder' && node.children) {
        return {
          ...node,
          children: sortTree(node.children),
        };
      }
      return node;
    });
}

function ensureFolderPath(
  root: MutableFolderNode,
  relativePath: string,
  resourceType: string,
): MutableFolderNode {
  const parts = relativePath.split('/').filter(Boolean);
  let current = root;
  let accumulated = '';

  for (const part of parts) {
    accumulated = accumulated ? `${accumulated}/${part}` : part;
    const folderKey = `${resourceType}/${accumulated}/`;

    let nextFolder = current.children?.find(
      (child) => child.type === 'folder' && child.key === folderKey,
    ) as MutableFolderNode | undefined;

    if (!nextFolder) {
      nextFolder = createFolderNode(folderKey, part, resourceType);
      current.children.push(nextFolder);
    }

    current = nextFolder;
  }

  return current;
}

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
  folders: string[] = [],
): FileTreeNode[] {
  const nodes: FileTreeNode[] = [];
  const explicitTopLevelFolders = new Set<string>();

  // manifest.json is always the root node
  nodes.push({
    key: MANIFEST_KEY,
    name: MANIFEST_KEY,
    type: 'file',
  });

  // Group resources by type into virtual folders
  const folderMap = new Map<string, MutableFolderNode>();

  const ensureTypeFolder = (folderType: string): MutableFolderNode => {
    let folderNode = folderMap.get(folderType);
    if (!folderNode) {
      folderNode = createFolderNode(`${folderType}/`, `${folderType}/`, folderType);
      folderMap.set(folderType, folderNode);
    }
    return folderNode;
  };

  for (const folder of folders) {
    const normalized = folder.replace(/^\/+|\/+$/g, '');
    if (!normalized) {
      continue;
    }

    const [folderType, ...segments] = normalized.split('/');
    if (!folderType) {
      continue;
    }

    if (segments.length === 0) {
      ensureTypeFolder(folderType);
      explicitTopLevelFolders.add(folderType);
      continue;
    }

    ensureFolderPath(ensureTypeFolder(folderType), segments.join('/'), folderType);
  }

  for (const resource of Object.values(resources)) {
    const folderType = resource.type || 'other';
    const typeFolder = ensureTypeFolder(folderType);
    const pathSegments = resource.name.split('/').filter(Boolean);
    const fileName = pathSegments.pop();
    if (!fileName) {
      continue;
    }

    const parentFolder = pathSegments.length > 0
      ? ensureFolderPath(typeFolder, pathSegments.join('/'), folderType)
      : typeFolder;

    parentFolder.children.push({
      key: `${folderType}/${resource.name}`,
      name: fileName,
      type: 'file',
      resourceType: folderType,
    });
  }

  // Sort folders by predefined order, then add non-empty folders
  const sortedTypes = [...folderMap.keys()].sort(
    (a, b) => (FOLDER_ORDER[a] ?? 99) - (FOLDER_ORDER[b] ?? 99),
  );

  for (const folderType of sortedTypes) {
    const folderNode = folderMap.get(folderType)!;
    if ((folderNode.children?.length ?? 0) > 0 || explicitTopLevelFolders.has(folderType)) {
      nodes.push({
        ...folderNode,
        children: sortTree(folderNode.children),
      });
    }
  }

  return nodes;
}
