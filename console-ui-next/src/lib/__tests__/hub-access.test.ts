import { describe, expect, it } from 'vitest';
import { isPublicHubPath } from '../hub-access';

describe('public Hub route boundary', () => {
  it('allows shared Skills inside Skill management and legacy redirects', () => {
    for (const path of ['/skill/shared/list', '/skill/shared/list/', '/skill/shared/detail/305',
      '/skill/shared/detail/9223372036854775807', '/hub/skills', '/hub/skills/',
      '/hub/skills/305', '/hub/skills/9223372036854775807']) {
      expect(isPublicHubPath(path)).toBe(true);
    }
  });
  it('keeps source management, mutations and look-alike routes protected', () => {
    for (const path of ['/skill', '/skill/example', '/skill/shared', '/namespace',
      '/skill/shared/list/edit', '/skill/shared/detail/305/edit', '/skill/shared/detail/0',
      '/skill/shared/detail/-1', '/skill/shared/list-extra', '/hub/skills/305/edit',
      '/hub/skills/share', '/hub/skills-extra', '/hub/skills/0', '/hub/skills/-1']) {
      expect(isPublicHubPath(path)).toBe(false);
    }
  });
});
