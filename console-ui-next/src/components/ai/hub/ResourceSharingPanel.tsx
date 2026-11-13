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
import { AlertTriangle, Copy, RefreshCw, Share2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent } from '@/components/ui/card';
import type { HubShareState } from '@/types/hub-skill';

interface Props {
  title: string;
  version?: string | null;
  share: HubShareState | null;
  canWrite: boolean;
  eligible: boolean;
  copyable: boolean;
  eligibilityHint: string;
  copyHint: string;
  lifecycleHint: string;
  loading: boolean;
  busy: boolean;
  error: string;
  onRefresh: () => void;
  onShare: () => void;
  onUnshare: () => void;
  onCopy: () => void;
}

/** One source-detail layout; each resource retains its own eligibility and mutation contract. */
export function ResourceSharingPanel(props: Props) {
  const { t } = useTranslation();
  const { title, version, share, canWrite, eligible, copyable, loading, busy, error } = props;
  return <Card className="gap-0 overflow-hidden py-0">
    <div className="flex items-center justify-between border-b bg-muted/30 px-4 py-3">
      <h2 className="inline-flex items-center gap-2 text-sm font-semibold"><Share2 className="h-4 w-4" />{title}</h2>
      {canWrite && <Button variant="ghost" size="icon" className="h-6 w-6" disabled={loading || busy}
        aria-label={t('common.refresh')} onClick={props.onRefresh}><RefreshCw className="h-3.5 w-3.5" /></Button>}
    </div>
    <CardContent className="space-y-3 p-4">
      {canWrite && <>
        {error ? <p role="alert" className="text-xs text-destructive">{error}</p>
          : loading ? <p className="text-xs text-muted-foreground">{t('common.loading')}</p>
          : share?.active ? <div className="flex flex-wrap items-center gap-2 text-xs">
            <Badge variant="secondary">{t('hub.shared')}</Badge>
            <span>{t('hub.sharedVersion')}: <strong>{share.sharedVersion}</strong></span>
          </div> : <p className="text-xs text-muted-foreground">{t('hub.notShared')}</p>}
        {!eligible && <p className="flex gap-1 text-xs text-amber-600"><AlertTriangle className="h-3.5 w-3.5 shrink-0" />{props.eligibilityHint}</p>}
        <p className="text-xs text-muted-foreground">{props.lifecycleHint}</p>
      </>}
      <div className="flex flex-wrap items-center gap-2">
        {canWrite && eligible && !(share?.active && share.sharedVersion === version) &&
          <Button size="sm" variant="outline" disabled={loading || busy || !!error} onClick={props.onShare}>
            {t(share?.active ? 'hub.adjust' : 'hubResource.share')}
          </Button>}
        {canWrite && share?.active && <Button size="sm" variant="destructive" disabled={loading || busy || !!error}
          onClick={props.onUnshare}>{t('hub.unshare')}</Button>}
        <Button size="sm" variant="outline" disabled={busy || !copyable} onClick={props.onCopy}>
          <Copy className="mr-1.5 h-3.5 w-3.5" />{t('hubResource.copy')}
        </Button>
      </div>
      {!copyable && <p className="text-xs text-muted-foreground">{props.copyHint}</p>}
    </CardContent>
  </Card>;
}
