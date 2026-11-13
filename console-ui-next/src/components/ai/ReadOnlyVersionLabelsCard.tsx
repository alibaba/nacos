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
import { Card, CardContent } from '@/components/ui/card';
import { DetailTagChip } from './DetailTagChip';

export function ReadOnlyVersionLabelsCard({ title, labels }: { title: string; labels: string[] }) {
  const { t } = useTranslation();
  return <Card className="py-0 gap-0 overflow-hidden">
    <div className="px-5 py-3.5 border-b bg-muted/30">
      <h2 className="text-sm font-semibold">{title}</h2>
    </div>
    <CardContent className="p-5 space-y-3">
      {labels.length > 0 ? <div className="flex flex-wrap gap-1.5">
        {labels.map(label => <DetailTagChip key={label} label={label} />)}
      </div> : <p className="text-xs text-muted-foreground">{t('common.versionLabels.noLabels')}</p>}
    </CardContent>
  </Card>;
}
