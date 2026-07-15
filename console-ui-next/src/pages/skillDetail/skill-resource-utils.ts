import type { SkillResource } from '@/types/skill';
import type { FileTreeNode } from '../agentSpecManagement/components/FileTreePanel';

// ===== File type detection =====

export type FileCategory = 'text' | 'image' | 'svg' | 'binary';

const BINARY_EXTENSIONS = new Set([
  '.zip', '.tar', '.gz', '.rar', '.7z',
  '.pdf', '.doc', '.docx', '.xls', '.xlsx', '.pptx',
  '.exe', '.bin', '.dll', '.so', '.dylib',
  '.wasm', '.class', '.jar',
  '.mp3', '.mp4', '.avi', '.mov', '.wav', '.flac',
  '.ttf', '.otf', '.woff', '.woff2',
]);

const IMAGE_EXTENSIONS = new Set([
  '.png', '.jpg', '.jpeg', '.gif', '.webp', '.ico', '.bmp',
]);

function getExtension(fileName: string): string {
  const dotIdx = fileName.lastIndexOf('.');
  if (dotIdx === -1) return '';
  return fileName.slice(dotIdx).toLowerCase();
}

export function getFileCategory(fileName: string): FileCategory {
  const ext = getExtension(fileName);
  if (IMAGE_EXTENSIONS.has(ext)) return 'image';
  if (ext === '.svg') return 'svg';
  if (BINARY_EXTENSIONS.has(ext)) return 'binary';
  return 'text';
}

export function getLanguageFromFileName(fileName: string): string {
  if (fileName === 'Dockerfile' || fileName.endsWith('/Dockerfile')) {
    return 'dockerfile';
  }
  const ext = getExtension(fileName);
  const map: Record<string, string> = {
    '.json': 'json', '.md': 'markdown',
    '.js': 'javascript', '.jsx': 'javascript',
    '.ts': 'typescript', '.tsx': 'typescript',
    '.yaml': 'yaml', '.yml': 'yaml',
    '.xml': 'xml', '.svg': 'xml',
    '.html': 'html', '.css': 'css',
    '.sh': 'shell', '.bash': 'shell',
    '.py': 'python', '.java': 'java',
    '.go': 'go', '.rs': 'rust',
    '.sql': 'sql', '.toml': 'toml',
    '.ini': 'ini', '.conf': 'ini', '.properties': 'ini',
  };
  return map[ext] || 'plaintext';
}

// ===== File tree building =====

interface MutableFolder extends FileTreeNode {
  type: 'folder';
  children: FileTreeNode[];
}

function makeFolder(key: string, name: string, resourceType: string): MutableFolder {
  return { key, name, type: 'folder', children: [], resourceType };
}

function sortNodes(nodes: FileTreeNode[]): FileTreeNode[] {
  return [...nodes]
    .sort((a, b) => {
      if (a.type !== b.type) return a.type === 'folder' ? -1 : 1;
      return a.name.localeCompare(b.name);
    })
    .map((n) =>
      n.type === 'folder' && n.children
        ? { ...n, children: sortNodes(n.children) }
        : n,
    );
}

function ensurePath(
  root: MutableFolder,
  relativePath: string,
  rType: string,
): MutableFolder {
  const parts = relativePath.split('/').filter(Boolean);
  let cur = root;
  let acc = '';
  for (const p of parts) {
    acc = acc ? `${acc}/${p}` : p;
    const fk = `${rType}/${acc}/`;
    let next = cur.children.find(
      (c) => c.type === 'folder' && c.key === fk,
    ) as MutableFolder | undefined;
    if (!next) {
      next = makeFolder(fk, p, rType);
      cur.children.push(next);
    }
    cur = next;
  }
  return cur;
}

/**
 * Build a file tree from Skill resources.
 * Resources are grouped by their `type` field into virtual folders.
 *
 * Supports two naming conventions:
 * 1. Flat type, path in name:  type="skill", name="subfolder/file.txt"
 * 2. Hierarchical type:        type="skill/subfolder", name="file.txt"
 *
 * Convention 2 is preferred for new resources because the server's
 * generateResourceId only sanitises `/` inside `type` (→ `.`), not `name`.
 */
export function buildSkillFileTree(
  resources: Record<string, SkillResource>,
): FileTreeNode[] {
  const folders = new Map<string, MutableFolder>();
  const rootTypes = new Set<string>();
  const rootFiles: FileTreeNode[] = [];

  /**
   * Get (or create) the folder node for a given type string.
   * Handles hierarchical types like "skill/subfolder" by recursively
   * creating nested folders under the root type ("skill").
   */
  const getFolder = (t: string): MutableFolder => {
    let f = folders.get(t);
    if (f) return f;

    const typeParts = t.split('/').filter(Boolean);

    if (typeParts.length <= 1) {
      // Simple root type like "skill"
      f = makeFolder(`${t}/`, `${t}/`, t);
      folders.set(t, f);
      rootTypes.add(t);
      return f;
    }

    // Hierarchical type like "skill/subfolder"
    // Recursively ensure parent folder exists
    const parentType = typeParts.slice(0, -1).join('/');
    const parentFolder = getFolder(parentType);

    // Derive the root type for the resourceType field
    const rootType = typeParts[0];

    // Build folder key consistent with ensurePath: "rootType/subpath/"
    const subPath = typeParts.slice(1).join('/');
    const fk = `${rootType}/${subPath}/`;

    // Check if ensurePath already created this folder
    const existing = parentFolder.children.find(
      (c) => c.type === 'folder' && c.key === fk,
    ) as MutableFolder | undefined;
    if (existing) {
      folders.set(t, existing);
      return existing;
    }

    const childName = typeParts[typeParts.length - 1];
    f = makeFolder(fk, childName, rootType);
    parentFolder.children.push(f);
    folders.set(t, f);
    return f;
  };

  for (const res of Object.values(resources)) {
    const ft = res.type;
    if (!ft) {
      rootFiles.push({ key: res.name, name: res.name, type: 'file' });
      continue;
    }
    const tf = getFolder(ft);

    // Derive root type (first segment) for ensurePath compatibility
    const rootType = ft.split('/')[0];

    const parts = res.name.split('/').filter(Boolean);
    const fn = parts.pop();
    if (!fn) continue;
    const parent =
      parts.length > 0 ? ensurePath(tf, parts.join('/'), rootType) : tf;
    parent.children.push({
      key: `${ft}/${res.name}`,
      name: fn,
      type: 'file',
      resourceType: ft,
    });
  }

  // Only add root-level type folders to the top-level nodes
  const nodes: FileTreeNode[] = [...rootFiles];
  for (const t of [...rootTypes].sort()) {
    const f = folders.get(t)!;
    nodes.push({ ...f, children: sortNodes(f.children) });
  }
  return sortNodes(nodes);
}

/**
 * Resolve a tree key back to the actual SkillResource and its map key.
 */
export function resolveResourceByKey(
  resources: Record<string, SkillResource>,
  key: string,
): { mapKey: string; resource: SkillResource } | null {
  for (const [mk, res] of Object.entries(resources)) {
    const rk = res.type ? `${res.type}/${res.name}` : res.name;
    if (rk === key) return { mapKey: mk, resource: res };
  }
  return null;
}
