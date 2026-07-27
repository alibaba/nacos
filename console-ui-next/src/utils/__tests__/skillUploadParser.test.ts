import JSZip from 'jszip';
import { describe, expect, it } from 'vitest';
import {
  buildSkillBatchZipExcludingPrefixes,
  getSkillEntryDisplayName,
  isInvalidSkillEntryCode,
} from '../skillUploadParser';

async function createZipFile(zip: JSZip): Promise<File> {
  const blob = await zip.generateAsync({ type: 'blob' });
  return new File([blob], 'skills.zip', { type: 'application/zip' });
}

describe('buildSkillBatchZipExcludingPrefixes', () => {
  it('removes skipped skill folders and keeps the other archive entries', async () => {
    const zip = new JSZip();
    zip.file('skill-a/SKILL.md', 'skill a');
    zip.file('skill-a/scripts/run.sh', 'run a');
    zip.file('skill-b/SKILL.md', 'skill b');
    zip.file('not-a-skill/readme.md', 'readme');

    const result = await buildSkillBatchZipExcludingPrefixes(
      await createZipFile(zip),
      ['skill-a/'],
    );
    const resultZip = await JSZip.loadAsync(await result.arrayBuffer());

    expect(resultZip.file('skill-a/SKILL.md')).toBeNull();
    expect(resultZip.file('skill-b/SKILL.md')).not.toBeNull();
    expect(resultZip.file('not-a-skill/readme.md')).not.toBeNull();
  });

  it('drops macOS metadata while rebuilding the archive', async () => {
    const zip = new JSZip();
    zip.file('skill-b/SKILL.md', 'skill b');
    zip.file('__MACOSX/skill-b/._SKILL.md', 'metadata');
    zip.file('skill-b/.DS_Store', 'metadata');

    const result = await buildSkillBatchZipExcludingPrefixes(
      await createZipFile(zip),
      [],
    );
    const resultZip = await JSZip.loadAsync(await result.arrayBuffer());

    expect(resultZip.file('skill-b/SKILL.md')).not.toBeNull();
    expect(resultZip.file('__MACOSX/skill-b/._SKILL.md')).toBeNull();
    expect(resultZip.file('skill-b/.DS_Store')).toBeNull();
  });
});

describe('getSkillEntryDisplayName', () => {
  it('shows only the last directory without a trailing slash', () => {
    expect(getSkillEntryDisplayName('multi-skill/not-a-skill/')).toBe('not-a-skill');
    expect(getSkillEntryDisplayName('multi-skill/invalid-skill/')).toBe('invalid-skill');
  });

  it('handles an empty or single-level entry path', () => {
    expect(getSkillEntryDisplayName('skill-a/')).toBe('skill-a');
    expect(getSkillEntryDisplayName('')).toBe('');
    expect(getSkillEntryDisplayName()).toBe('');
  });
});

describe('isInvalidSkillEntryCode', () => {
  it('recognizes non-skill and invalid-skill entries', () => {
    expect(isInvalidSkillEntryCode('NOT_A_SKILL')).toBe(true);
    expect(isInvalidSkillEntryCode('INVALID_SKILL')).toBe(true);
    expect(isInvalidSkillEntryCode('READY')).toBe(false);
  });
});
