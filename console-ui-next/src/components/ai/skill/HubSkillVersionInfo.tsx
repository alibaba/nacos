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

import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Download, Globe, MessageSquare, Tag, Wand2 } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent } from '@/components/ui/card';
import { CliCommandCard } from '@/components/ai/CliCommandCard';
import type { HubSkillDetail } from '@/types/hub-skill';

/** Read-only metadata and download of exactly the published Hub version. */
export function HubSkillVersionInfo({ detail, onDownload, downloading }: {
  detail: HubSkillDetail;
  onDownload: () => void;
  downloading: boolean;
}) {
  const { t } = useTranslation();
  return <aside className="w-full space-y-4 lg:w-[320px] lg:shrink-0">
    <CliCommandCard commands={[]} onDownload={onDownload} downloadDisabled={downloading}
      downloadFileName={`${detail.displayName}-${detail.sharedVersion}.zip`} />
    <InfoCard title={t('skill.basicInfo')} icon={<Wand2 className="h-4 w-4" />}>
      <dl className="grid grid-cols-2 [&>*:nth-child(n+3)]:border-t [&>*:nth-child(even)]:border-l">
        <InfoCell label={t('skill.status')} icon={<Tag className="h-3.5 w-3.5" />}>
          <Badge variant="secondary" className="bg-emerald-500/10 text-emerald-600">
            {detail.versionStatus ? t(`skill.versionStatus.${detail.versionStatus}`) : '-'}
          </Badge>
        </InfoCell>
        <InfoCell label={t('skill.author')} icon={<Globe className="h-3.5 w-3.5" />}>
          {detail.author || '-'}
        </InfoCell>
        <InfoCell label={t('skill.downloads')} icon={<Download className="h-3.5 w-3.5" />}>
          {detail.downloadCount ?? 0}
        </InfoCell>
        <InfoCell label={t('skill.versionDownloads')} icon={<Download className="h-3.5 w-3.5" />}>
          {detail.versionDownloadCount ?? 0}
        </InfoCell>
        <InfoCell label={t('skill.commitMsg')} icon={<MessageSquare className="h-3.5 w-3.5" />} wide>
          <span className="whitespace-pre-wrap font-normal">{detail.commitMsg?.trim() || '-'}</span>
        </InfoCell>
      </dl>
    </InfoCard>
    <InfoCard title={t('common.bizTags')} icon={<Tag className="h-4 w-4" />}>
      <div className="p-3.5">
        <Tags tags={detail.tags} empty={t('skill.noBizTags')} />
      </div>
    </InfoCard>
    <InfoCard title={t('common.versionLabels.title')} icon={<Tag className="h-4 w-4" />}>
      <div className="p-3.5">
        <Tags tags={detail.versionLabels} empty={t('common.versionLabels.noLabels')} />
      </div>
    </InfoCard>
  </aside>;
}

function InfoCard({ title, icon, children }: { title: string; icon: ReactNode; children: ReactNode }) {
  return <Card className="overflow-hidden gap-0 py-0">
    <div className="border-b bg-muted/30 px-4 py-3">
      <h2 className="flex items-center gap-2 text-sm font-semibold">
        <span className="text-muted-foreground">{icon}</span>{title}
      </h2>
    </div>
    <CardContent className="p-0">{children}</CardContent>
  </Card>;
}

function InfoCell({ label, icon, children, wide }: {
  label: string; icon: ReactNode; children: ReactNode; wide?: boolean;
}) {
  return <div className={`flex min-w-0 items-start gap-2 px-3.5 py-3 ${wide ? 'col-span-2' : ''}`}>
    <span className="mt-1 text-muted-foreground">{icon}</span>
    <div className="min-w-0">
      <dt className="text-[10px] text-muted-foreground">{label}</dt>
      <dd className="mt-0.5 break-words text-xs font-medium">{children}</dd>
    </div>
  </div>;
}

function Tags({ tags, empty }: { tags?: string[] | null; empty: string }) {
  return tags?.length ? <div className="flex flex-wrap gap-1.5">
    {tags.map(tag => <Badge key={tag} variant="secondary" className="max-w-full whitespace-normal break-all">
      {tag}
    </Badge>)}
  </div> : <p className="text-xs text-muted-foreground">{empty}</p>;
}
