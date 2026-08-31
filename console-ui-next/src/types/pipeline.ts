export type PipelineExecutionStatus = 'IN_PROGRESS' | 'APPROVED' | 'REJECTED';

export interface PipelineCheckpoint {
  title: string;
  passed: boolean;
}

export interface PipelineNode {
  nodeId: string;
  executedAt?: string;
  passed: boolean;
  message?: string;
  messageType?: string;
  checkpoints?: PipelineCheckpoint[];
  durationMs?: number;
}

export interface PublishPipelineInfo {
  executionId: string;
  status: PipelineExecutionStatus;
  pipeline: PipelineNode[];
  historical?: boolean;
}

export function parsePipelineInfo(
  raw: string | null | undefined,
): PublishPipelineInfo | null {
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw);
    if (parsed && typeof parsed.executionId === 'string' && typeof parsed.status === 'string') {
      return parsed as PublishPipelineInfo;
    }
    return null;
  } catch {
    return null;
  }
}
