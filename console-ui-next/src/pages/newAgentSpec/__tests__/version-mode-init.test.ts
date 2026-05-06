import { describe, expect, it } from 'vitest';
import { planAgentSpecEditorVersionMode } from '../version-mode';

describe('AgentSpec version-mode initialization', () => {
  it('reuses an existing editing draft for version mode', () => {
    expect(
      planAgentSpecEditorVersionMode({
        mode: 'version',
        editingVersion: 'v4',
        currentVersion: 'v3',
        sourceVersion: 'v3',
      }),
    ).toEqual({
      versionToLoad: 'v4',
      shouldCreateDraft: false,
    });
  });

  it('creates a draft from the requested source version when no editing draft exists', () => {
    expect(
      planAgentSpecEditorVersionMode({
        mode: 'version',
        editingVersion: null,
        currentVersion: 'v3',
        sourceVersion: 'v2',
      }),
    ).toEqual({
      shouldCreateDraft: true,
      basedOnVersion: 'v2',
    });
  });

  it('falls back to the current viewed version when sourceVersion is absent', () => {
    expect(
      planAgentSpecEditorVersionMode({
        mode: 'version',
        editingVersion: null,
        currentVersion: 'v3',
      }),
    ).toEqual({
      shouldCreateDraft: true,
      basedOnVersion: 'v3',
    });
  });

  it('does not invent a draft for edit mode without editingVersion', () => {
    expect(
      planAgentSpecEditorVersionMode({
        mode: 'edit',
        editingVersion: null,
        currentVersion: 'v3',
      }),
    ).toEqual({
      versionToLoad: undefined,
      shouldCreateDraft: false,
    });
  });
});