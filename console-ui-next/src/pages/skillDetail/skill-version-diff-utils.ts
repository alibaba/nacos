import type { SkillDocument, SkillResource } from '@/types/skill';

export type SkillDiffFileStatus = 'added' | 'removed' | 'modified';

export interface SkillVersionFile {
  path: string;
  content: string;
}

export interface SkillVersionFileDiff {
  path: string;
  status: SkillDiffFileStatus;
  beforeContent?: string;
  afterContent?: string;
}

export interface SkillVersionDiff {
  added: SkillVersionFileDiff[];
  removed: SkillVersionFileDiff[];
  modified: SkillVersionFileDiff[];
}

export function buildSkillVersionFiles(doc: SkillDocument | null): Map<string, SkillVersionFile> {
  const files = new Map<string, SkillVersionFile>();
  if (!doc) return files;

  files.set('SKILL.md', {
    path: 'SKILL.md',
    content: doc.skillMd ?? '',
  });

  for (const resource of Object.values(doc.resource ?? {})) {
    const path = getResourcePath(resource);
    if (!path) continue;
    files.set(path, {
      path,
      content: resource.content ?? '',
    });
  }

  return files;
}

export function compareSkillVersions(
  before: SkillDocument | null,
  after: SkillDocument | null,
): SkillVersionDiff {
  const beforeFiles = buildSkillVersionFiles(before);
  const afterFiles = buildSkillVersionFiles(after);
  const added: SkillVersionFileDiff[] = [];
  const removed: SkillVersionFileDiff[] = [];
  const modified: SkillVersionFileDiff[] = [];

  for (const [path, afterFile] of afterFiles) {
    const beforeFile = beforeFiles.get(path);
    if (!beforeFile) {
      added.push({
        path,
        status: 'added',
        afterContent: afterFile.content,
      });
      continue;
    }
    if (beforeFile.content !== afterFile.content) {
      modified.push({
        path,
        status: 'modified',
        beforeContent: beforeFile.content,
        afterContent: afterFile.content,
      });
    }
  }

  for (const [path, beforeFile] of beforeFiles) {
    if (afterFiles.has(path)) continue;
    removed.push({
      path,
      status: 'removed',
      beforeContent: beforeFile.content,
    });
  }

  return {
    added: sortDiffs(added),
    removed: sortDiffs(removed),
    modified: sortDiffs(modified),
  };
}

function getResourcePath(resource: SkillResource): string {
  const name = resource.name?.trim();
  if (!name) return '';
  const type = resource.type?.trim();
  return type ? `${type.replace(/\/+$/, '')}/${name.replace(/^\/+/, '')}` : name;
}

function sortDiffs(items: SkillVersionFileDiff[]): SkillVersionFileDiff[] {
  return [...items].sort((a, b) => a.path.localeCompare(b.path));
}
