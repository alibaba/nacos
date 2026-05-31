/**
 * Prompt version status state machine utilities.
 *
 * State transitions:
 *   draft     → submit   → reviewing
 *   reviewing → (pipeline approved) → reviewed
 *   reviewing → (pipeline rejected) → draft
 *   reviewed  → publish  → online
 *   reviewed  → redraft   → draft
 *   reviewing → publish  → online  (backward compat: historical data without reviewed status)
 *   online    → offline  → offline
 *   offline   → online   → online
 *
 * Additionally, draft versions can be deleted.
 * Online/offline versions can serve as base for new drafts when no editing/reviewing version exists.
 */

import type { PipelineExecutionStatus } from '@/types/skill';

const STATE_ACTIONS: Record<string, string[]> = {
  draft: ['submit', 'deleteDraft'],
  reviewing: ['publish'],
  reviewed: ['publish', 'redraft', 'deleteDraft'],
  online: ['offline'],
  offline: ['online'],
};

export function sortVersionsDescending<T extends { gmtModified: number }>(versions: T[]): T[] {
  return [...versions].sort((a, b) => b.gmtModified - a.gmtModified);
}

export function getValidActions(status: string): string[] {
  return Object.hasOwn(STATE_ACTIONS, status) ? STATE_ACTIONS[status] : [];
}

export interface ActionItem {
  action: string;
  disabled?: boolean;
  disabledReason?: string;
}

/**
 * Context-aware version of getValidActions.
 * - reviewing: disables publish if pipeline hasn't approved; adds forcePublish for admin when pipeline is REJECTED
 * - draft: adds forcePublish for admin when pipeline is REJECTED
 * - online/offline: adds createDraftFrom when no editing/reviewing version exists
 */
export function getValidActionsWithContext(
  status: string,
  hasEditingOrReviewing: boolean,
  pipelineStatus?: PipelineExecutionStatus | null,
  isGlobalAdmin?: boolean,
  pipelineHistorical?: boolean | null,
): ActionItem[] {
  const base = getValidActions(status);
  const items: ActionItem[] = base.map((action) => {
    if (action === 'publish' && pipelineStatus && pipelineStatus !== 'APPROVED') {
      return { action, disabled: true, disabledReason: 'prompt.publishDisabledPipeline' };
    }
    return { action };
  });

  // Admin force-publish: show when pipeline REJECTED on reviewing/reviewed,
  // or on draft when pipeline is not historical (legacy backward compat)
  if (isGlobalAdmin && pipelineStatus === 'REJECTED') {
    if (status === 'reviewing' || status === 'reviewed') {
      items.push({ action: 'forcePublish' });
    } else if (status === 'draft' && !pipelineHistorical) {
      items.push({ action: 'forcePublish' });
    }
  }

  if (status === 'online' || status === 'offline') {
    if (hasEditingOrReviewing) {
      items.push({ action: 'createDraftFrom', disabled: true, disabledReason: 'prompt.draftExistsTip' });
    } else {
      items.push({ action: 'createDraftFrom' });
    }
  }

  return items;
}
