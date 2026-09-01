import { describe, expect, it } from 'vitest';
import { nextMcpVersion } from '../version-utils';

describe('nextMcpVersion', () => {
  it('bumps semantic and legacy numeric versions without assuming all versions are SemVer', () => {
    expect(nextMcpVersion('1.2.3')).toBe('1.2.4');
    expect(nextMcpVersion('v9')).toBe('v10');
    expect(nextMcpVersion('release-a')).toBe('release-a-next');
    expect(nextMcpVersion('')).toBe('');
  });
});
