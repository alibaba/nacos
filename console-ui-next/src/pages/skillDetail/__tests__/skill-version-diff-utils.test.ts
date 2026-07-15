import { describe, expect, it } from 'vitest';
import type { SkillDocument, SkillResource } from '@/types/skill';
import {
  buildSkillVersionFiles,
  compareSkillVersions,
} from '../skill-version-diff-utils';

function skillDoc(skillMd: string, resource: Record<string, SkillResource>): SkillDocument {
  return {
    namespaceId: 'public',
    name: 'demo-skill',
    description: 'demo',
    skillMd,
    resource,
  };
}

describe('skill-version-diff-utils', () => {
  it('normalizes SKILL.md and resource paths into version files', () => {
    const files = buildSkillVersionFiles(
      skillDoc('skill md', {
        a: { type: 'skill/scripts', name: 'run.sh', content: 'echo hi', metadata: null },
        b: { type: '', name: 'README.md', content: 'readme', metadata: null },
      }),
    );

    expect([...files.keys()].sort()).toEqual([
      'README.md',
      'SKILL.md',
      'skill/scripts/run.sh',
    ]);
  });

  it('detects added, removed, and modified files between two versions', () => {
    const before = skillDoc('old md', {
      unchanged: { type: 'skill', name: 'same.txt', content: 'same', metadata: null },
      changed: { type: 'skill', name: 'changed.txt', content: 'before', metadata: null },
      removed: { type: 'skill', name: 'removed.txt', content: 'removed', metadata: null },
    });
    const after = skillDoc('new md', {
      unchanged: { type: 'skill', name: 'same.txt', content: 'same', metadata: null },
      changed: { type: 'skill', name: 'changed.txt', content: 'after', metadata: null },
      added: { type: 'skill', name: 'added.txt', content: 'added', metadata: null },
    });

    const diff = compareSkillVersions(before, after);

    expect(diff.added.map((file) => file.path)).toEqual(['skill/added.txt']);
    expect(diff.removed.map((file) => file.path)).toEqual(['skill/removed.txt']);
    expect(diff.modified.map((file) => file.path)).toEqual([
      'SKILL.md',
      'skill/changed.txt',
    ]);
    expect(diff.modified.find((file) => file.path === 'skill/changed.txt')).toMatchObject({
      beforeContent: 'before',
      afterContent: 'after',
    });
  });

  it('ignores unchanged same-path files', () => {
    const before = skillDoc('same md', {
      item: { type: 'skill', name: 'same.txt', content: 'same', metadata: null },
    });
    const after = skillDoc('same md', {
      item: { type: 'skill', name: 'same.txt', content: 'same', metadata: null },
    });

    const diff = compareSkillVersions(before, after);

    expect(diff.added).toHaveLength(0);
    expect(diff.removed).toHaveLength(0);
    expect(diff.modified).toHaveLength(0);
  });
});
