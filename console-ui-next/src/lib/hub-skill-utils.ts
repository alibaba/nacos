import type { HubSkillDetail, HubSkillSummary } from '@/types/hub-skill';

export const HUB_SYSTEM_NAMESPACE = '__nacos_ai_hub__';

/** Reject unsafe or lossy 64-bit IDs; never Number(...) a Java database ID. */
export function validHubId(value: unknown): value is string {
  return typeof value === 'string' && /^[1-9]\d*$/.test(value) &&
    (value.length < 19 || (value.length === 19 && value <= '9223372036854775807'));
}

export function hubDetailPath(hubEntryId: string): string {
  if (!validHubId(hubEntryId)) throw new Error('Invalid Hub entry ID');
  return `/hub/skills/${encodeURIComponent(hubEntryId)}`;
}

export function sourceSkillPath(detail: HubSkillDetail): string | null {
  if (detail.sourceReadable !== true || !detail.sourceNamespaceId || !detail.sourceName ||
      detail.sourceNamespaceId === HUB_SYSTEM_NAMESPACE || !detail.sharedVersion?.trim()) return null;
  const query = new URLSearchParams({
    namespaceId: detail.sourceNamespaceId,
    version: detail.sharedVersion,
  });
  return `/skill/${encodeURIComponent(detail.sourceName)}?${query}`;
}

export function activeSourceVersion(
  requested: string | null,
  versions: readonly { version: string }[],
  latest: string | undefined,
): string {
  if (requested) {
    // A missing pinned version must never silently resolve to latest.
    return versions.some(v => v.version === requested) ? requested : '';
  }
  if (latest && versions.some(v => v.version === latest)) return latest;
  return versions[0]?.version ?? '';
}

export function distinctHubEntries(items: readonly HubSkillSummary[]): boolean {
  const ids = items.map(item => item.hubEntryId);
  return ids.every(validHubId) && new Set(ids).size === ids.length;
}

export function revokeWarning(active: boolean, affectedVersion?: string, sharedVersion?: string): boolean {
  return active && (!affectedVersion || affectedVersion === sharedVersion);
}

export function hubErrorMessage(error: unknown, fallback: string): string {
  if (!error || typeof error !== 'object') return fallback;
  const e = error as { code?: number; response?: { status?: number; data?: { code?: number; message?: string } }; message?: string };
  const status = e.response?.status;
  const code = e.response?.data?.code ?? e.code;
  if (status === 401 || status === 403 || code === 10001) return 'permission';
  if (status === 404 || status === 410 || code === 20004) return 'unavailable';
  if (status === 409 || code === 409 || code === 20005) return 'conflict';
  return fallback;
}
