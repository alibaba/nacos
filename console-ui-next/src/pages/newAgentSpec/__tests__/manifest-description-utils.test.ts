import { describe, expect, it } from 'vitest';

import {
  getAgentSpecDescription,
  syncManifestDescription,
} from '../manifest-description-utils';

describe('AgentSpec manifest description utils', () => {
  it('prefers the root description and falls back to worker.description', () => {
    expect(getAgentSpecDescription('{"description":"root","worker":{"description":"worker"}}')).toBe('root');
    expect(getAgentSpecDescription('{"worker":{"description":"worker"}}')).toBe('worker');
  });

  it('writes the same description back to manifest and worker fields', () => {
    const synced = syncManifestDescription(
      '{"name":"demo","description":"old","worker":{"description":"legacy","runtime":"nodejs"}}',
      'new description',
    );

    expect(JSON.parse(synced)).toEqual({
      name: 'demo',
      description: 'new description',
      worker: {
        description: 'new description',
        runtime: 'nodejs',
      },
    });
  });

  it('removes description fields when the basic info description is cleared', () => {
    const synced = syncManifestDescription(
      '{"description":"old","worker":{"description":"legacy","runtime":"nodejs"}}',
      '   ',
    );

    expect(JSON.parse(synced)).toEqual({
      worker: {
        runtime: 'nodejs',
      },
    });
  });

  it('leaves invalid manifest content unchanged', () => {
    expect(syncManifestDescription('{invalid json', 'next')).toBe('{invalid json');
    expect(getAgentSpecDescription('{invalid json')).toBe('');
  });
});