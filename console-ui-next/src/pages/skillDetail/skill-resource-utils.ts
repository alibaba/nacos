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
 */
export function buildSkillFileTree(
  resources: Record<string, SkillResource>,
): FileTreeNode[] {
  const folders = new Map<string, MutableFolder>();
  const rootFiles: FileTreeNode[] = [];

  const getFolder = (t: string): MutableFolder => {
    let f = folders.get(t);
    if (!f) {
      f = makeFolder(`${t}/`, `${t}/`, t);
      folders.set(t, f);
    }
    return f;
  };

  for (const res of Object.values(resources)) {
    const ft = res.type;
    if (!ft) {
      rootFiles.push({ key: res.name, name: res.name, type: 'file' });
      continue;
    }
    const tf = getFolder(ft);
    const parts = res.name.split('/').filter(Boolean);
    const fn = parts.pop();
    if (!fn) continue;
    const parent =
      parts.length > 0 ? ensurePath(tf, parts.join('/'), ft) : tf;
    parent.children.push({
      key: `${ft}/${res.name}`,
      name: fn,
      type: 'file',
      resourceType: ft,
    });
  }

  const nodes: FileTreeNode[] = [...rootFiles];
  for (const t of [...folders.keys()].sort()) {
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
