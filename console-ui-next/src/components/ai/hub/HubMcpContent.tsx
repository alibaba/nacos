import { McpGovernanceInfo } from '@/components/ai/mcp/McpGovernanceInfo';
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

import { useTranslation } from 'react-i18next';
import { ReadOnlyVersionLabelsCard } from '@/components/ai/ReadOnlyVersionLabelsCard';
import { McpDetailContent } from '@/components/ai/mcp/McpDetailContent';
import type { SharedResource } from '@/api/hub-resource';
import type { McpServerVersionDetail, McpServerDetailInfo } from '@/types/mcp';

export function HubMcpContent({ detail, content }: { detail: SharedResource; content: Record<string, unknown> }) {
  const { t } = useTranslation();
  const labels = Object.entries((content.labels || {}) as Record<string, unknown>)
    .filter(([label, version]) => label !== 'latest' && version === detail.version)
    .map(([label]) => label);
  const mcp = { ...detail.mcp, ...(content.serverSpecification as McpServerDetailInfo), name: detail.resourceName,
    version: detail.version, toolSpec: content.toolSpecification } as McpServerDetailInfo;
  return <McpDetailContent mcp={mcp} namespaceId={mcp.remoteServerConfig?.serviceRef?.namespaceId} management={<><McpGovernanceInfo detail={content as unknown as McpServerVersionDetail} /><ReadOnlyVersionLabelsCard title={t('mcp.customLabels')} labels={labels} /></>} />;
}
