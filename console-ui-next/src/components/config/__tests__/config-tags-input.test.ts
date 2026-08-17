import { describe, expect, it } from 'vitest';

import { parseConfigTags, serializeConfigTags } from '../config-tags';

describe('ConfigTagsInput value conversion', () => {
  it('renders comma-separated values as unique individual tags', () => {
    expect(parseConfigTags('issue-15695, layout-demo，issue-15695')).toEqual([
      'issue-15695',
      'layout-demo',
    ]);
  });

  it('serializes individual tags as the API-compatible comma-separated value', () => {
    expect(serializeConfigTags(['issue-15695', 'layout-demo'])).toBe(
      'issue-15695,layout-demo',
    );
  });
});
