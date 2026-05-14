/**
 * Version status state machine utilities.
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
 */

const STATE_ACTIONS: Record<string, string[]> = {
  draft: ['submit', 'deleteDraft'],
  reviewing: ['publish'],
  reviewed: ['publish', 'redraft', 'deleteDraft'],
  online: ['offline'],
  offline: ['online'],
};

export function sortVersionsDescending<T extends { updateTime: number }>(versions: T[]): T[] {
  return [...versions].sort((a, b) => b.updateTime - a.updateTime);
}

/**
 * Returns the list of valid action names for a given version status.
 * Unknown statuses return an empty array.
 */
export function getValidActions(status: string): string[] {
  return Object.hasOwn(STATE_ACTIONS, status) ? STATE_ACTIONS[status] : [];
}
