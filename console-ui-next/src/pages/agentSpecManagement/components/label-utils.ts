/**
 * Validates a label key.
 * A valid key contains only alphanumeric characters, hyphens, underscores, or dots,
 * and is not already present in the existing keys list.
 */
export function isValidLabelKey(key: string, existingKeys: string[]): boolean {
  if (!key) return false;
  const validPattern = /^[a-zA-Z0-9._-]+$/;
  if (!validPattern.test(key)) return false;
  return !existingKeys.includes(key);
}
