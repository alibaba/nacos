/**
 * PromptVersionTimeline logic tests.
 * Verifies version sorting, action button computation, and disabled state logic.
 * Requirements: 4.2, 4.3, 4.8
 */
import { describe, it, expect } from 'vitest';
import { sortVersionsDescending, getValidActions, getValidActionsWithContext } from '../prompt-version-utils';
import type { PromptVersionSummary } from '@/types/prompt';

const makeVersion = (version: string, status: string, gmtModified: number): PromptVersionSummary => ({
  promptKey: 'test',
  version,
  status: status as PromptVersionSummary['status'],
  commitMsg: '',
  srcUser: 'user',
  gmtModified,
  publishPipelineInfo: null,
  downloadCount: null,
});

describe('PromptVersionTimeline logic', () => {
  describe('version sorting', () => {
    it('sorts versions by gmtModified descending', () => {
      const versions = [
        makeVersion('1.0.0', 'online', 1000),
        makeVersion('1.0.2', 'draft', 3000),
        makeVersion('1.0.1', 'reviewing', 2000),
      ];
      const sorted = sortVersionsDescending(versions);
      expect(sorted.map((v) => v.version)).toEqual(['1.0.2', '1.0.1', '1.0.0']);
    });

    it('does not mutate the original array', () => {
      const versions = [
        makeVersion('1.0.0', 'online', 1000),
        makeVersion('1.0.1', 'draft', 2000),
      ];
      const sorted = sortVersionsDescending(versions);
      expect(sorted).not.toBe(versions);
      expect(versions[0].version).toBe('1.0.0');
    });
  });

  describe('action buttons for each status', () => {
    it('draft shows submit and deleteDraft', () => {
      const actions = getValidActions('draft');
      expect(actions).toEqual(['submit', 'deleteDraft']);
    });

    it('reviewing shows publish', () => {
      const actions = getValidActions('reviewing');
      expect(actions).toEqual(['publish']);
    });

    it('online shows offline', () => {
      const actions = getValidActions('online');
      expect(actions).toEqual(['offline']);
    });

    it('offline shows online', () => {
      const actions = getValidActions('offline');
      expect(actions).toEqual(['online']);
    });
  });

  describe('disabled state logic', () => {
    it('createDraftFrom is disabled when editing/reviewing version exists', () => {
      const items = getValidActionsWithContext('online', true);
      const createDraft = items.find((i) => i.action === 'createDraftFrom');
      expect(createDraft).toBeDefined();
      expect(createDraft!.disabled).toBe(true);
      expect(createDraft!.disabledReason).toBe('prompt.draftExistsTip');
    });

    it('createDraftFrom is enabled when no editing/reviewing version exists', () => {
      const items = getValidActionsWithContext('online', false);
      const createDraft = items.find((i) => i.action === 'createDraftFrom');
      expect(createDraft).toBeDefined();
      expect(createDraft!.disabled).toBeUndefined();
    });

    it('publish is disabled when pipeline not approved', () => {
      const items = getValidActionsWithContext('reviewing', false, 'IN_PROGRESS');
      const publish = items.find((i) => i.action === 'publish');
      expect(publish).toBeDefined();
      expect(publish!.disabled).toBe(true);
      expect(publish!.disabledReason).toBe('prompt.publishDisabledPipeline');
    });

    it('forcePublish shown for admin when pipeline rejected', () => {
      const items = getValidActionsWithContext('reviewing', false, 'REJECTED', true);
      const forcePublish = items.find((i) => i.action === 'forcePublish');
      expect(forcePublish).toBeDefined();
    });

    it('forcePublish not shown for non-admin', () => {
      const items = getValidActionsWithContext('reviewing', false, 'REJECTED', false);
      const forcePublish = items.find((i) => i.action === 'forcePublish');
      expect(forcePublish).toBeUndefined();
    });
  });
});
