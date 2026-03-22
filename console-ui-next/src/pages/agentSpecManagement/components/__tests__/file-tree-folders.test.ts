import { describe, expect, it } from 'vitest';
import { buildFileTree } from '../file-tree-utils';

describe('buildFileTree folder support', () => {
  it('builds nested folders for resource paths', () => {
    const tree = buildFileTree(
      {
        'tools/helper.py': {
          name: 'tools/helper.py',
          type: 'skill',
          content: 'print("ok")',
          metadata: null,
        },
      },
      '{}',
    );

    const skillFolder = tree.find((node) => node.key === 'skill/');
    expect(skillFolder).toBeDefined();

    const toolsFolder = skillFolder?.children?.find((node) => node.key === 'skill/tools/');
    expect(toolsFolder).toBeDefined();
    expect(toolsFolder?.children?.[0]?.key).toBe('skill/tools/helper.py');
    expect(toolsFolder?.children?.[0]?.name).toBe('helper.py');
  });

  it('keeps explicit empty folders in the tree', () => {
    const tree = buildFileTree({}, '{}', ['other/scripts']);

    const otherFolder = tree.find((node) => node.key === 'other/');
    expect(otherFolder).toBeDefined();

    const scriptsFolder = otherFolder?.children?.find((node) => node.key === 'other/scripts/');
    expect(scriptsFolder).toBeDefined();
    expect(scriptsFolder?.children).toEqual([]);
  });
});