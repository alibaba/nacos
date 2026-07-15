import { describe, expect, it } from 'vitest';

import { prepareSkillMarkdownPreview } from '../markdown-utils';

describe('prepareSkillMarkdownPreview', () => {
  it('converts equals title blocks to markdown headings', () => {
    const source = [
      '你必须严格遵循以下流程：',
      '==========================',
      '1. 问题本质（What）',
      '==========================',
      '用一句话描述这个问题的真实目标',
    ].join('\n');

    expect(prepareSkillMarkdownPreview(source)).toBe([
      '你必须严格遵循以下流程：',
      '## 1. 问题本质（What）',
      '用一句话描述这个问题的真实目标',
    ].join('\n'));
  });

  it('escapes a leftover standalone equals divider', () => {
    const source = [
      '你必须严格遵循以下流程：',
      '==========================',
    ].join('\n');

    expect(prepareSkillMarkdownPreview(source)).toBe([
      '你必须严格遵循以下流程：',
      '\\=\\=\\=\\=\\=\\=\\=\\=\\=\\=\\=\\=\\=\\=\\=\\=\\=\\=\\=\\=\\=\\=\\=\\=\\=\\=',
    ].join('\n'));
  });

  it('keeps equals lines inside fenced code blocks unchanged', () => {
    const source = [
      '```text',
      '==========================',
      '```',
      '==========================',
    ].join('\n');

    expect(prepareSkillMarkdownPreview(source)).toBe([
      '```text',
      '==========================',
      '```',
      '\\=\\=\\=\\=\\=\\=\\=\\=\\=\\=\\=\\=\\=\\=\\=\\=\\=\\=\\=\\=\\=\\=\\=\\=\\=\\=',
    ].join('\n'));
  });
});
