import type { PromptVersionInfo } from '@/types/prompt';

export interface PromptVersionDiff {
  modified: boolean;
  beforeContent: string;
  afterContent: string;
}

export function comparePromptVersions(
  before: PromptVersionInfo | null,
  after: PromptVersionInfo | null,
): PromptVersionDiff {
  const beforeContent = before?.template ?? '';
  const afterContent = after?.template ?? '';

  return {
    modified: beforeContent !== afterContent,
    beforeContent,
    afterContent,
  };
}
