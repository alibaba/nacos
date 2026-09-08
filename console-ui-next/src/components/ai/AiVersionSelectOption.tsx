/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { Badge } from '@/components/ui/badge';
import { parsePipelineInfo } from '@/types/pipeline';

interface AiVersionSelectOptionLabels {
  latest: string;
  draft: string;
  reviewing: string;
  pendingPublish: string;
  rejected: string;
}

interface AiVersionSelectOptionProps {
  version: string;
  status?: string;
  latest?: boolean;
  publishPipelineInfo?: string | null;
  labels: AiVersionSelectOptionLabels;
}

const BADGE_BASE = 'text-[10px] px-1 py-0 border-0';

/**
 * Shared version-selector presentation for lifecycle-managed AI resources.
 */
export function AiVersionSelectOption({
  version,
  status,
  latest = false,
  publishPipelineInfo,
  labels,
}: AiVersionSelectOptionProps) {
  const pipeline = parsePipelineInfo(publishPipelineInfo);
  const pendingPublish = (status === 'reviewed' && pipeline?.status !== 'REJECTED')
    || (status === 'reviewing' && pipeline?.status === 'APPROVED');
  const rejected = status === 'reviewed' && pipeline?.status === 'REJECTED';

  return (
    <span className="flex items-center gap-2">
      <span>{version}</span>
      {latest && (
        <Badge className={`bg-emerald-100 text-emerald-700 dark:bg-emerald-950/50 dark:text-emerald-300 ${BADGE_BASE}`}>
          {labels.latest}
        </Badge>
      )}
      {status === 'draft' && (
        <Badge className={`bg-amber-100 text-amber-700 dark:bg-amber-950/50 dark:text-amber-300 ${BADGE_BASE}`}>
          {labels.draft}
        </Badge>
      )}
      {rejected && (
        <Badge className={`bg-red-100 text-red-700 dark:bg-red-950/50 dark:text-red-300 ${BADGE_BASE}`}>
          {labels.rejected}
        </Badge>
      )}
      {!rejected && (status === 'reviewing' || status === 'reviewed') && (
        <Badge className={pendingPublish
          ? `bg-teal-100 text-teal-700 dark:bg-teal-950/50 dark:text-teal-300 ${BADGE_BASE}`
          : `bg-blue-100 text-blue-700 dark:bg-blue-950/50 dark:text-blue-300 ${BADGE_BASE}`
        }>
          {pendingPublish ? labels.pendingPublish : labels.reviewing}
        </Badge>
      )}
    </span>
  );
}
