type PipelineInfo = {
  status?: string;
  historical?: boolean;
};

export function canResubmitReview(
  versionStatus: string | null | undefined,
  pipelineInfo: PipelineInfo | null | undefined,
): boolean {
  if (versionStatus === 'reviewed') {
    return true;
  }
  if (versionStatus !== 'reviewing' || pipelineInfo?.historical) {
    return false;
  }
  return pipelineInfo?.status === 'APPROVED' || pipelineInfo?.status === 'REJECTED';
}
