import { describe, expect, it } from 'vitest';
import type { PromptVersionInfo } from '@/types/prompt';
import { comparePromptVersions } from '../prompt-version-diff-utils';

function promptVersion(template: string): PromptVersionInfo {
  return {
    promptKey: 'demo',
    version: '0.0.1',
    status: 'draft',
    commitMsg: '',
    srcUser: '',
    gmtModified: 0,
    publishPipelineInfo: null,
    downloadCount: null,
    template,
    md5: '',
    variables: [],
  };
}

describe('prompt-version-diff-utils', () => {
  it('detects modified prompt templates', () => {
    const diff = comparePromptVersions(promptVersion('before'), promptVersion('after'));

    expect(diff).toEqual({
      modified: true,
      beforeContent: 'before',
      afterContent: 'after',
    });
  });

  it('ignores unchanged prompt templates', () => {
    const diff = comparePromptVersions(promptVersion('same'), promptVersion('same'));

    expect(diff.modified).toBe(false);
  });
});
