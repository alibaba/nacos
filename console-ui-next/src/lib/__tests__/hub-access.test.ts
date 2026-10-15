import { describe, expect, it } from 'vitest';
import { isPublicHubPath } from '../hub-access';

describe('public Hub route boundary', () => {
  it('allows the Hub directory and entry details', () => {
    for (const path of ['/hub/skills', '/hub/skills/', '/hub/skills/305', '/hub/skills/9223372036854775807']) {
      expect(isPublicHubPath(path)).toBe(true);
    }
  });
  it('keeps source management, mutations and look-alike routes protected', () => {
    for (const path of ['/skill', '/skill/example', '/namespace', '/hub/skills/305/edit',
      '/hub/skills/share', '/hub/skills-extra', '/hub/skills/0', '/hub/skills/-1']) {
      expect(isPublicHubPath(path)).toBe(false);
    }
  });
});
