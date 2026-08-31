import type { McpVersionStatus } from '@/types/mcp';
import type { PublishPipelineInfo } from '@/types/pipeline';

import { canForcePublish, canResubmitReview } from '@/components/ai/version-lifecycle';

export type McpVersionAction =
  | 'editDraft'
  | 'submit'
  | 'publish'
  | 'forcePublish'
  | 'redraft'
  | 'online'
  | 'offline'
  | 'deleteDraft';

export function getMcpVersionActions(
  status: McpVersionStatus,
  pipelineInfo: PublishPipelineInfo | null,
  globalAdmin: boolean,
): McpVersionAction[] {
  const forcePublish = canForcePublish(status, pipelineInfo, globalAdmin)
    ? ['forcePublish' as const]
    : [];
  const resubmit = canResubmitReview(status, pipelineInfo) ? ['submit' as const] : [];
  switch (status) {
    case 'draft':
      return ['editDraft', 'submit', 'deleteDraft', ...forcePublish];
    case 'reviewing':
      return [...resubmit, 'publish', ...forcePublish];
    case 'reviewed':
      return [...resubmit, 'publish', 'redraft', ...forcePublish];
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
