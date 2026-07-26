import JSZip from 'jszip';
import type { SkillUploadPrecheckCode } from '@/types/skill';

const MACOSX_DIR = '__MACOSX';

export function getSkillEntryDisplayName(entryPath?: string | null): string {
  const normalizedPath = (entryPath ?? '').replace(/\\/g, '/').replace(/\/+$/, '');
  if (!normalizedPath) {
    return '';
  }
  return normalizedPath.slice(normalizedPath.lastIndexOf('/') + 1);
}

export function isInvalidSkillEntryCode(code: SkillUploadPrecheckCode): boolean {
  return code === 'NOT_A_SKILL' || code === 'INVALID_SKILL';
}

function isMacOsMetadataPath(path: string): boolean {
  const fileName = path.slice(path.lastIndexOf('/') + 1);
  return fileName.startsWith('._')
    || fileName === '.DS_Store'
    || path === MACOSX_DIR
    || path.startsWith(`${MACOSX_DIR}/`)
    || path.includes(`/${MACOSX_DIR}/`);
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
