import { describe, expect, it } from 'vitest';
import fc from 'fast-check';
import { buildAgentSpecEditorSearch } from '../version-workflow';

describe('AgentSpec detail version workflow navigation', () => {
  it('version mode includes the sourceVersion query param', () => {
    const search = buildAgentSpecEditorSearch({
      mode: 'version',
      name: 'demo-agent',
      namespaceId: 'public',
      sourceVersion: 'v3',
    });

    const params = new URLSearchParams(search);
    expect(params.get('mode')).toBe('version');
    expect(params.get('name')).toBe('demo-agent');
    expect(params.get('namespaceId')).toBe('public');
    expect(params.get('sourceVersion')).toBe('v3');
  });

  it('edit mode omits sourceVersion even if a value is passed', () => {
    const search = buildAgentSpecEditorSearch({
      mode: 'edit',
      name: 'demo-agent',
      namespaceId: 'public',
      sourceVersion: 'v3',
    });

    const params = new URLSearchParams(search);
    expect(params.get('mode')).toBe('edit');
    expect(params.has('sourceVersion')).toBe(false);
  });

  it('property: version mode always preserves sourceVersion when provided', () => {
    fc.assert(
      fc.property(
        fc.string({ minLength: 1, maxLength: 32 }),
        fc.string({ minLength: 1, maxLength: 32 }),
        fc.string({ minLength: 1, maxLength: 32 }),
        (name, namespaceId, sourceVersion) => {
          const search = buildAgentSpecEditorSearch({
            mode: 'version',
            name,
            namespaceId,
            sourceVersion,
          });

          const params = new URLSearchParams(search);
          expect(params.get('name')).toBe(name);
          expect(params.get('namespaceId')).toBe(namespaceId);
          expect(params.get('sourceVersion')).toBe(sourceVersion);
        },
      ),
      { numRuns: 100 },
    );
  });
});