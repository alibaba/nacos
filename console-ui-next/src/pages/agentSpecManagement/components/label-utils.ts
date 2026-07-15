/**
 * Server-managed label keys must not be edited by the frontend.
 */
export const RESERVED_LABEL_LATEST = 'latest';

export function isReservedLabelKey(key: string): boolean {
  return key.trim().toLowerCase() === RESERVED_LABEL_LATEST;
}

/**
 * Validates a label key.
 * A valid key contains only alphanumeric characters, hyphens, underscores, or dots,
 * and is not already present in the existing keys list.
 */
export function isValidLabelKey(key: string, existingKeys: string[]): boolean {
  if (!key) return false;
  if (isReservedLabelKey(key)) return false;
  const validPattern = /^[a-zA-Z0-9._-]+$/;
  if (!validPattern.test(key)) return false;
  return !existingKeys.includes(key);
}
