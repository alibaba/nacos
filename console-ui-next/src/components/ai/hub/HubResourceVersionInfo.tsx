import { AgentDetailSidebar } from '@/components/ai/AgentDetailContent';
import type { AgentSummary } from '@/types/agent';
import { PipelineStatusDisplay } from '@/pages/skillManagement/components/PipelineStatusDisplay';
import { parsePipelineInfo } from '@/types/pipeline';
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

import { Download, Globe, MessageSquare, Tag } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Card, CardContent } from '@/components/ui/card';
import { CliCommandCard } from '@/components/ai/CliCommandCard';
import { DetailTagChip } from '@/components/ai/DetailTagChip';
import { resourceDownloadName } from '@/lib/hub-resource-downloads';
import type { SharedResource } from '@/api/hub-resource';

export function HubResourceVersionInfo({ detail, content, install, onDownload, downloading }: {
  detail: SharedResource; content: Record<string, unknown>; install: string | null; onDownload: () => void; downloading: boolean;
}) {
  const { t } = useTranslation();
  if (detail.resourceType === 'mcp') return null;
  const prefix = detail.resourceType === 'agentspec' ? 'agentSpec' : detail.resourceType;
  const versions = detail.metadata?.versionDetails ?? detail.metadata?.versions;
  const versionSummary = Array.isArray(versions)
    ? versions.find(value => value && typeof value === 'object' && value.version === detail.version) as Record<string, unknown> | undefined
    : undefined;
  const pipeline = parsePipelineInfo((content.publishPipelineInfo ?? versionSummary?.publishPipelineInfo) as string | null | undefined);
  const status = typeof content.status === 'string' ? content.status : 'online';
  const publisher = typeof content.srcUser === 'string' ? content.srcUser : typeof content.author === 'string' ? content.author : typeof versionSummary?.author === 'string' ? versionSummary.author : detail.author;
  const commit = typeof content.commitMsg === 'string' ? content.commitMsg : typeof content.changeDescription === 'string' ? content.changeDescription : '';
  if (detail.resourceType === 'agent') {
    const agent = (detail.metadata || {
      owner: detail.author, provider: detail.provider ? { name: detail.provider } : null, tags: detail.tags,
      versionInfo: { labels: Object.fromEntries((detail.versionLabels || []).map(label => [label, detail.version])),
        onlineVersions: [{ version: detail.version }] },
    }) as Partial<AgentSummary>;
    const labels = Object.fromEntries(Object.entries(agent.versionInfo?.labels || {})
      .filter(([label, version]) => label !== 'latest' && version === detail.version));
    return <AgentDetailSidebar agent={agent} labelsText={JSON.stringify(labels, null, 2)} readOnlyLabels={labels} />;
  }
  return <aside className="w-full space-y-4 lg:w-[320px] lg:shrink-0">
    <CliCommandCard commands={install ? [{ label: t('common.cliUsage.cliInstall'), command: install }] : []} onDownload={detail.resourceType === 'prompt' ? onDownload : undefined} downloadDisabled={downloading} downloadFileName={detail.resourceType === 'prompt' ? resourceDownloadName(detail) : undefined} />
    <Card className="overflow-hidden gap-0 py-0"><div className="border-b bg-muted/30 px-4 py-3"><h2 className="flex items-center gap-2 text-sm font-semibold"><Globe className="h-4 w-4 text-muted-foreground" />{t(`${prefix}.basicInfo`, { defaultValue: t('skill.basicInfo') })}</h2></div><CardContent className="p-0">
      <dl className="grid grid-cols-2 [&>*:nth-child(n+3)]:border-t [&>*]:border-border">
        <div className="px-4 py-2.5"><dt className="text-[11px] text-muted-foreground">{t(`${prefix}.status`)}</dt><dd className="text-[13px] font-medium">{t(`${prefix}.versionStatus.${status}`)}</dd></div>
        <div className="flex min-w-0 items-center gap-2.5 border-l px-4 py-2.5"><Globe className="h-3.5 w-3.5 text-muted-foreground/60 shrink-0" /><div className="min-w-0"><dt className="text-[11px] text-muted-foreground leading-none mb-1">{t('hub.author')}</dt><dd className="text-[13px] font-medium break-all">{publisher || '-'}</dd></div></div>
        <div className="col-span-2 flex min-w-0 items-center gap-2.5 px-4 py-2.5"><Download className="h-3.5 w-3.5 text-muted-foreground/60 shrink-0" /><div><dt className="text-[11px] text-muted-foreground leading-none mb-1">{t('hub.downloads')}</dt><dd className="text-[13px] font-medium">{detail.downloadCount ?? 0}</dd></div></div>
        {detail.resourceType === 'prompt' && <div className="col-span-2 flex min-w-0 items-center gap-2.5 px-4 py-2.5"><MessageSquare className="h-3.5 w-3.5 text-muted-foreground/60 shrink-0" /><div><dt className="text-[11px] text-muted-foreground leading-none mb-1">{t('skill.commitMsg')}</dt><dd className="text-xs text-muted-foreground whitespace-pre-wrap">{commit.trim() || '-'}</dd></div></div>}

      </dl>
    </CardContent></Card>
    {pipeline && <Card className="overflow-hidden gap-0 py-0"><div className="border-b bg-muted/30 px-4 py-3"><h2 className="text-sm font-semibold">{t(`${prefix}.pipelineStatus`)}</h2></div><CardContent className="p-3.5"><PipelineStatusDisplay pipelineInfo={pipeline} translationPrefix={detail.resourceType === 'agentspec' ? 'agentSpec' : 'skill'} /></CardContent></Card>}
    <Card className="overflow-hidden gap-0 py-0"><div className="border-b bg-muted/30 px-4 py-3"><h2 className="flex items-center gap-2 text-sm font-semibold"><Tag className="h-4 w-4 text-muted-foreground" />{t('common.bizTags')}</h2></div><CardContent className="p-3.5">{detail.tags?.length ? <div className="flex flex-wrap gap-1.5">{detail.tags.map(tag => <DetailTagChip key={tag} label={tag} />)}</div> : <p className="text-xs text-muted-foreground">{t('skill.noBizTags')}</p>}</CardContent></Card>
    <Card className="overflow-hidden gap-0 py-0"><div className="border-b bg-muted/30 px-4 py-3"><h2 className="flex items-center gap-2 text-sm font-semibold"><Tag className="h-4 w-4 text-muted-foreground" />{t('common.versionLabels.title')}</h2></div><CardContent className="p-3.5">{detail.versionLabels?.length ? <div className="flex flex-wrap gap-1.5">{detail.versionLabels.map(label => <DetailTagChip key={label} label={label} />)}</div> : <p className="text-xs text-muted-foreground">{t('common.versionLabels.noLabels')}</p>}</CardContent></Card>
  </aside>;
}
