type PipelineInfo = {
  status?: string;
  historical?: boolean;
};

export function canForcePublish(
  versionStatus: string | null | undefined,
  pipelineInfo: PipelineInfo | null | undefined,
  globalAdmin: boolean,
): boolean {
  if (!globalAdmin || pipelineInfo?.status !== 'REJECTED') {
    return false;
  }
  if (versionStatus === 'draft') {
    return !pipelineInfo.historical;
  }
  return versionStatus === 'reviewing' || versionStatus === 'reviewed';
}

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
