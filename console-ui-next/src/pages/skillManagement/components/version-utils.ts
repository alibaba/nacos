/**
 * Skill version status state machine utilities.
 *
 * State transitions:
 *   draft     → submit   → reviewing
 *   reviewing → publish  → online
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
  online: ['offline'],
  offline: ['online'],
};

export function sortVersionsDescending<T extends { updateTime: number }>(versions: T[]): T[] {
  return [...versions].sort((a, b) => b.updateTime - a.updateTime);
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
 * - reviewing: disables publish if pipeline hasn't approved
 * - online/offline: adds createDraftFrom when no editing/reviewing version exists
 */
export function getValidActionsWithContext(
  status: string,
  hasEditingOrReviewing: boolean,
  pipelineStatus?: PipelineExecutionStatus | null,
): ActionItem[] {
  const base = getValidActions(status);
  const items: ActionItem[] = base.map((action) => {
    if (action === 'publish' && pipelineStatus && pipelineStatus !== 'APPROVED') {
      return { action, disabled: true, disabledReason: 'skill.publishDisabledPipeline' };
    }
    return { action };
  });

  if ((status === 'online' || status === 'offline') && !hasEditingOrReviewing) {
    items.push({ action: 'createDraftFrom' });
  }

  return items;
}
