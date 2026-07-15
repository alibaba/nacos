import JSZip from 'jszip';
import type { SkillUploadPrecheckRequest } from '@/types/skill';

const SKILL_MD = 'SKILL.md';
const META_JSON = '_meta.json';
const MACOSX_DIR = '__MACOSX';

function stripBom(value: string): string {
  return value.charCodeAt(0) === 0xfeff ? value.slice(1) : value;
}

function getSkillPrefix(path: string): string {
  const lastSlash = path.lastIndexOf('/');
  return lastSlash < 0 ? '' : path.slice(0, lastSlash + 1);
}

function isSkillMdPath(path: string): boolean {
  return path === SKILL_MD || path.endsWith(`/${SKILL_MD}`);
}

function isMacOsMetadataPath(path: string): boolean {
  const fileName = path.slice(path.lastIndexOf('/') + 1);
  return fileName.startsWith('._')
    || fileName === '.DS_Store'
    || path === MACOSX_DIR
    || path.startsWith(`${MACOSX_DIR}/`)
    || path.includes(`/${MACOSX_DIR}/`);
}

function hasAncestorSkillPrefix(prefix: string, prefixes: Set<string>): boolean {
  if (!prefix) {
    return false;
  }
  if (prefixes.has('')) {
    return true;
  }
  for (const candidate of prefixes) {
    if (!candidate || candidate === prefix) {
      continue;
    }
    if (prefix.startsWith(candidate)) {
      return true;
    }
  }
  return false;
}

function findRootSkillMdPaths(paths: string[]): string[] {
  const candidates = paths.filter((path) => !isMacOsMetadataPath(path) && isSkillMdPath(path));
  const prefixes = new Set(candidates.map(getSkillPrefix));
  return candidates.filter((path) => !hasAncestorSkillPrefix(getSkillPrefix(path), prefixes));
}

function countPrefixDepth(prefix: string): number {
  return prefix.split('/').filter(Boolean).length;
}

function extractPrefixAtDepth(path: string, depth: number): string | null {
  if (depth <= 0) {
    return '';
  }
  const segments = path.split('/').filter(Boolean);
  if (segments.length <= depth) {
    return null;
  }
  return `${segments.slice(0, depth).join('/')}/`;
}

function findNonSkillFolderPrefixes(paths: string[], skillMdPaths: string[]): string[] {
  if (skillMdPaths.length <= 1) {
    return [];
  }
  const skillPrefixes = new Set(skillMdPaths.map(getSkillPrefix));
  const firstSkillPrefix = skillMdPaths.map(getSkillPrefix).find((prefix) => prefix);
  const skillDepth = firstSkillPrefix ? countPrefixDepth(firstSkillPrefix) : 0;
  const result = new Set<string>();
  for (const path of paths) {
    const peerPrefix = extractPrefixAtDepth(path, skillDepth);
    if (!peerPrefix || skillPrefixes.has(peerPrefix)) {
      continue;
    }
    result.add(peerPrefix);
  }
  return [...result].sort();
}

function unescapeYamlValue(value: string): string {
  if (value.startsWith('"') && value.endsWith('"')) {
    return value.slice(1, -1).replace(/\\\\/g, '\\').replace(/\\"/g, '"');
  }
  if (value.startsWith("'") && value.endsWith("'")) {
    return value.slice(1, -1).replace(/''/g, "'");
  }
  return value;
}

function parseYamlFrontmatter(markdown: string): Record<string, string> {
  const match = markdown.match(/^---\s*\n([\s\S]*?)\n---\s*\n[\s\S]*$/);
  if (!match) {
    throw new Error('SKILL.md must contain YAML front matter (---)');
  }
  const result: Record<string, string> = {};
  let currentKey: string | null = null;
  let currentValue = '';
  for (const rawLine of match[1].split('\n')) {
    if (!rawLine.trim() || rawLine.trim().startsWith('#')) {
      continue;
    }
    if (/^\s/.test(rawLine) && currentKey) {
      const nestedLine = rawLine.trim();
      const nestedColon = nestedLine.indexOf(':');
      if (nestedColon > 0) {
        const nestedKey = nestedLine.slice(0, nestedColon).trim();
        const nestedValue = nestedLine.slice(nestedColon + 1).trim();
        result[`${currentKey}.${nestedKey}`] = unescapeYamlValue(nestedValue);
      }
      currentValue = currentValue ? `${currentValue} ${nestedLine}` : nestedLine;
      result[currentKey] = currentValue;
      continue;
    }
    const trimmed = rawLine.trim();
    const colon = trimmed.indexOf(':');
    if (colon > 0) {
      currentKey = trimmed.slice(0, colon).trim();
      currentValue = unescapeYamlValue(trimmed.slice(colon + 1).trim());
      result[currentKey] = currentValue;
      continue;
    }
    currentKey = null;
    currentValue = '';
  }
  return result;
}

function hasNonFrontmatterContent(markdown: string): boolean {
  const match = markdown.match(/^---\s*\n[\s\S]*?\n---\s*\n([\s\S]*)$/);
  return !!match?.[1]?.trim();
}

function buildSiblingMetaJsonPath(skillMdPath: string): string {
  const prefix = getSkillPrefix(skillMdPath);
  return `${prefix}${META_JSON}`;
}

async function parseSkillUploadPrecheckRequestFromZip(
  namespaceId: string,
  zip: JSZip,
  skillMdPath: string,
): Promise<SkillUploadPrecheckRequest> {
  const skillMdFile = zip.file(skillMdPath);
  if (!skillMdFile) {
    throw new Error('SKILL.md file not found in zip');
  }
  const skillMd = stripBom(await skillMdFile.async('string'));
  if (!skillMd.trim()) {
    throw new Error('SKILL.md file not found in zip');
  }
  const frontmatter = parseYamlFrontmatter(skillMd);
  const skillName = frontmatter.name?.trim();
  const description = frontmatter.description?.trim();
  if (!skillName) {
    throw new Error('Skill name is required in YAML front matter');
  }
  if (!description) {
    throw new Error('Skill description is required in YAML front matter');
  }
  if (!hasNonFrontmatterContent(skillMd)) {
    throw new Error('Skill markdown body is required');
  }

  let parsedVersion = frontmatter.version?.trim() || frontmatter['metadata.version']?.trim();
  let versionSource = parsedVersion ? 'SKILL.md frontmatter' : undefined;
  if (!parsedVersion) {
    const metaFile = zip.file(buildSiblingMetaJsonPath(skillMdPath));
    if (metaFile) {
      const meta = JSON.parse(await metaFile.async('string')) as { version?: unknown };
      const metaVersion = meta.version == null ? '' : String(meta.version).trim();
      if (metaVersion) {
        parsedVersion = metaVersion;
        versionSource = '_meta.json';
      }
    }
  }

  return {
    namespaceId,
    skillName,
    description,
    parsedVersion,
    versionSource,
  };
}

export type ParsedSkillUploadEntryKind = 'SKILL' | 'INVALID_SKILL' | 'NON_SKILL_FOLDER';

export interface ParsedSkillUploadEntry {
  kind: ParsedSkillUploadEntryKind;
  entryKey: string;
  rootPrefix: string;
  request?: SkillUploadPrecheckRequest;
  error?: string;
}

export async function parseSkillUploadEntries(
  namespaceId: string,
  file: File,
): Promise<ParsedSkillUploadEntry[]> {
  const zip = await JSZip.loadAsync(await file.arrayBuffer());
  const filePaths = Object.values(zip.files)
    .filter((entry) => !entry.dir)
    .map((entry) => entry.name)
    .filter((path) => !isMacOsMetadataPath(path));
  const skillMdPaths = findRootSkillMdPaths(filePaths);
  if (skillMdPaths.length === 0) {
    throw new Error('SKILL.md file not found in zip');
  }
  const entries = await Promise.all(skillMdPaths.map(async (skillMdPath) => {
    const rootPrefix = getSkillPrefix(skillMdPath);
    try {
      const request = await parseSkillUploadPrecheckRequestFromZip(
        namespaceId,
        zip,
        skillMdPath,
      );
      return {
        kind: 'SKILL' as const,
        entryKey: rootPrefix || skillMdPath,
        rootPrefix,
        request,
      };
    } catch (error) {
      return {
        kind: 'INVALID_SKILL' as const,
        entryKey: rootPrefix || skillMdPath,
        rootPrefix,
        error: error instanceof Error ? error.message : String(error),
      };
    }
  }));
  const nonSkillEntries: ParsedSkillUploadEntry[] = findNonSkillFolderPrefixes(
    filePaths,
    skillMdPaths,
  ).map((rootPrefix) => ({
    kind: 'NON_SKILL_FOLDER',
    entryKey: rootPrefix,
    rootPrefix,
    error: 'SKILL.md not found in this folder, skipped',
  }));
  return [...entries, ...nonSkillEntries];
}

export async function parseSkillUploadPrecheckRequest(
  namespaceId: string,
  file: File,
): Promise<SkillUploadPrecheckRequest> {
  const entries = await parseSkillUploadEntries(namespaceId, file);
  const first = entries[0];
  if (!first?.request) {
    throw new Error(first?.error || 'SKILL.md file not found in zip');
  }
  return first.request;
}

export async function buildSkillBatchZipExcludingPrefixes(
  file: File,
  rootPrefixes: string[],
): Promise<File> {
  const sourceZip = await JSZip.loadAsync(await file.arrayBuffer());
  const targetZip = new JSZip();
  const files = Object.values(sourceZip.files).filter((entry) => !entry.dir);

  await Promise.all(files.map(async (entry) => {
    if (isMacOsMetadataPath(entry.name)) {
      return;
    }
    const excluded = rootPrefixes.some((prefix) => {
      if (!prefix) {
        return true;
      }
      return entry.name.startsWith(prefix);
    });
    if (excluded) {
      return;
    }
    targetZip.file(entry.name, await entry.async('arraybuffer'));
  }));

  const blob = await targetZip.generateAsync({ type: 'blob' });
  return new File([blob], file.name, { type: 'application/zip' });
}
