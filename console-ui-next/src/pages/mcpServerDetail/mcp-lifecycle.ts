import type { McpVersionStatus } from '@/types/mcp';

export type McpVersionAction =
  | 'editDraft'
  | 'submit'
  | 'publish'
  | 'forcePublish'
  | 'redraft'
  | 'online'
  | 'offline'
  | 'deleteDraft';

export function getMcpVersionActions(status: McpVersionStatus): McpVersionAction[] {
  switch (status) {
    case 'draft':
      return ['editDraft', 'submit', 'forcePublish', 'deleteDraft'];
    case 'reviewing':
      return ['forcePublish'];
    case 'reviewed':
      return ['publish', 'forcePublish', 'redraft'];
    case 'online':
      return ['offline'];
    case 'offline':
      return ['online'];
  }
}

export function isMcpLifecycleUnavailable(error: unknown): boolean {
  const response = (error as {
    response?: { status?: number; data?: { message?: unknown; data?: unknown } };
  })?.response;
  if (response?.status !== 409) {
    return false;
  }
  const message = `${String(response.data?.message || '')} ${String(response.data?.data || '')}`;
  return message.includes('LIFECYCLE_MANAGED');
}
