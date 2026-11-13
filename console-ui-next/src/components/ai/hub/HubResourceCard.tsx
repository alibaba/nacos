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
import { Clock, Download, ExternalLink, UserRound, type LucideIcon } from 'lucide-react';
import dayjs from 'dayjs';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardFooter } from '@/components/ui/card';

interface Props {
  name: string;
  version: string;
  sourceDisplayName?: string | null;
  description?: string | null;
  author?: string | null;
  tags?: string[] | null;
  downloadCount?: number | null;
  updatedAt?: number | null;
  icon: LucideIcon;
  gradient: string;
  onOpen: () => void;
}

/** Shared directory card using the established Skill Hub layout for all resource types. */
export function HubResourceCard({ name, version, sourceDisplayName, description, author, tags,
  downloadCount, updatedAt, icon: Icon, gradient, onOpen }: Props) {
  const { t } = useTranslation();
  const source = sourceDisplayName || t('hub.unknownNamespace');
  const time = updatedAt == null ? null : dayjs(updatedAt);
  return <Card role="button" tabIndex={0} aria-label={`${t('hub.preview')}: ${name} @ ${version}`}
    onKeyDown={event => {
      if (event.target === event.currentTarget && (event.key === 'Enter' || event.key === ' ')) {
        event.preventDefault(); onOpen();
      }
    }}
    className="group relative flex flex-col py-0 gap-0 transition-all duration-200 hover:shadow-sm hover:border-primary/20 cursor-pointer overflow-hidden"
    onClick={onOpen}>
    <div className="flex items-start gap-3 px-4 pt-3.5 pb-2 relative">
      <div className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br shadow-sm ${gradient}`}>
        <Icon className="h-5 w-5 text-white" />
      </div>
      <div className="flex-1 min-w-0">
        <div className="flex min-w-0 items-start justify-between gap-2">
          <h3 className="min-w-0 flex-1 font-semibold text-sm truncate leading-tight" title={name}>{name}</h3>
          <Badge variant="outline" title={source}
            className="max-w-[55%] shrink-0 rounded-full border-blue-200 bg-blue-50 px-1.5 py-0 h-4 text-[10px] font-medium text-blue-600 dark:border-blue-500/30 dark:bg-blue-500/10 dark:text-blue-400">
            <span className="truncate">@{source}</span>
          </Badge>
        </div>
        <div className="flex items-center gap-1.5 mt-1">
          <span className="text-[10px] text-muted-foreground font-mono bg-muted/60 px-1 py-0.5 rounded">{version}</span>
        </div>
      </div>
    </div>
    <div className="px-4 pb-2 flex-1">
      <p className="text-xs text-muted-foreground line-clamp-2 leading-relaxed">{description || t('skill.noDescription')}</p>
      <div className="flex items-center gap-1.5 mt-2 flex-wrap">
        {(tags || []).slice(0, 2).map(tag => <span key={tag}
          className="inline-flex items-center rounded-md bg-slate-100 dark:bg-slate-900/70 px-1.5 py-0.5 text-[10px] font-medium text-slate-700 dark:text-slate-300">{tag}</span>)}
        <span className="inline-flex min-w-0 max-w-[50%] items-center gap-1 text-[10px] text-muted-foreground"
          title={`${t('hub.owner')}: ${author || '—'}`}>
          <UserRound className="h-2.5 w-2.5 shrink-0" /><span className="truncate">{author || '—'}</span>
        </span>
        <span className="inline-flex items-center gap-1 rounded-md bg-muted/60 px-1.5 py-0.5 text-[10px] font-medium text-muted-foreground ml-auto"
          title={t('hub.downloads')} aria-label={`${t('hub.downloads')}: ${downloadCount ?? 0}`}>
          <Download className="h-2.5 w-2.5" />{downloadCount ?? 0}
        </span>
      </div>
    </div>
    <CardFooter className="px-4 py-1.5 border-t bg-muted/20 flex items-center justify-between [.border-t]:pt-1.5">
      <span className="inline-flex items-center gap-1 text-[10px] text-muted-foreground">
        <Clock className="h-3 w-3" />{time?.isValid() ? time.format('YYYY-MM-DD HH:mm') : '—'}
      </span>
      <div className="flex items-center -mr-1" onClick={event => event.stopPropagation()}>
        <Button variant="ghost" size="icon" className="h-6 w-6" title={t('common.detail')}
          aria-label={t('common.detail')} onClick={onOpen}><ExternalLink className="h-3 w-3" /></Button>
      </div>
    </CardFooter>
  </Card>;
}
