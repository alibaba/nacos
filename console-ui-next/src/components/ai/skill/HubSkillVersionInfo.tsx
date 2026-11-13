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
import { DetailTagChip } from '@/components/ai/DetailTagChip';
import { Card, CardContent } from '@/components/ui/card';
import { CliCommandCard } from '@/components/ai/CliCommandCard';
import { hubSkillInstallCommand } from '@/lib/hub-skill-utils';
import type { HubSkillDetail } from '@/types/hub-skill';

/** Read-only metadata and download of exactly the published Hub version. */
export function HubSkillVersionInfo({ detail, onDownload, downloading }: {
  detail: HubSkillDetail;
  onDownload: () => void;
  downloading: boolean;
}) {
  const { t } = useTranslation();
  const installCommand = hubSkillInstallCommand(detail);
  return <aside className="w-full space-y-4 lg:w-[320px] lg:shrink-0">
    <CliCommandCard commands={installCommand ? [{ label: t('common.cliUsage.cliInstall'), command: installCommand }] : []}
      onDownload={onDownload} downloadDisabled={downloading}
      downloadFileName={`${detail.displayName}-${detail.sharedVersion}.zip`} />
    <InfoCard title={t('skill.basicInfo')} icon={<Wand2 className="h-4 w-4" />}>
      <dl className="grid grid-cols-2 [&>*:nth-child(n+3)]:border-t [&>*:nth-child(even)]:border-l border-border">
        <InfoCell label={t('skill.author')} icon={<Globe className="h-3.5 w-3.5" />}>
          {detail.author || '-'}
        </InfoCell>
        <InfoCell label={t('hub.downloads')} icon={<Download className="h-3.5 w-3.5" />}>
          {detail.downloadCount ?? 0}
        </InfoCell>
        <InfoCell label={t('skill.commitMsg')} icon={<MessageSquare className="h-3.5 w-3.5" />} wide>
          <span className="text-xs font-normal font-sans text-muted-foreground whitespace-pre-wrap">{detail.commitMsg?.trim() || '-'}</span>
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
  return <div className={`flex min-w-0 items-center gap-2.5 px-4 py-2.5 ${wide ? 'col-span-2' : ''}`}>
    <span className="text-muted-foreground/60 shrink-0">{icon}</span>
    <div className="min-w-0 flex-1">
      <dt className="text-[11px] text-muted-foreground leading-none mb-1">{label}</dt>
      <dd className="text-[13px] font-medium break-all">{children}</dd>
    </div>
  </div>;
}

function Tags({ tags, empty }: { tags?: string[] | null; empty: string }) {
  return tags?.length ? <div className="flex flex-wrap gap-1.5">
    {tags.map(tag => <DetailTagChip key={tag} label={tag} className="max-w-full h-auto min-h-6 py-1 whitespace-normal break-all" />)}
  </div> : <p className="text-xs text-muted-foreground">{empty}</p>;
}
