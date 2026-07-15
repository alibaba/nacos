import JSZip from 'jszip';
import { describe, expect, it } from 'vitest';
import { parseSkillUploadEntries } from '../skillUploadParser';

function addSkill(zip: JSZip, prefix: string, name: string, version: string): void {
  zip.file(`${prefix}/SKILL.md`, `---
name: ${name}
description: ${name} description
version: ${version}
---

${name} instructions`);
}

async function createZipFile(zip: JSZip): Promise<File> {
  const blob = await zip.generateAsync({ type: 'blob' });
  return new File([blob], 'skills.zip', { type: 'application/zip' });
}

describe('skillUploadParser', () => {
  it('keeps invalid and non-skill folders out of precheck when valid skills are present', async () => {
    const zip = new JSZip();
    addSkill(zip, 'ai-avatar-video', 'ai-avatar-video', '0.0.1');
    addSkill(zip, 'pdf 3', 'pdf', '0.0.1');
    zip.file('invalid-skill/SKILL.md', 'invalid');
    zip.file('not-a-skill/bababababab.md', 'not a skill');

    const entries = await parseSkillUploadEntries('public', await createZipFile(zip));
    const skills = entries.filter((entry) => entry.kind === 'SKILL');

    expect(entries).toHaveLength(4);
    expect(skills.map((entry) => entry.request?.skillName).sort()).toEqual([
      'ai-avatar-video',
      'pdf',
    ]);
    expect(entries.find((entry) => entry.entryKey === 'invalid-skill/')?.kind)
      .toBe('INVALID_SKILL');
    expect(entries.find((entry) => entry.entryKey === 'not-a-skill/')?.kind)
      .toBe('NON_SKILL_FOLDER');
  });

  it('returns parse errors when no valid skills are present', async () => {
    const zip = new JSZip();
    zip.file('invalid-skill/SKILL.md', 'invalid');

    const entries = await parseSkillUploadEntries('public', await createZipFile(zip));

    expect(entries).toHaveLength(1);
    expect(entries[0].request).toBeUndefined();
    expect(entries[0].error).toContain('YAML front matter');
  });

  it('keeps raw uploaded version for precheck display', async () => {
    const zip = new JSZip();
    addSkill(zip, 'fp-engineering-solver', 'fp-engineering-solver', '0.1');

    const entries = await parseSkillUploadEntries('public', await createZipFile(zip));

    expect(entries[0].request?.parsedVersion).toBe('0.1');
    expect(entries[0].request?.versionSource).toBe('SKILL.md frontmatter');
  });
});
