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
import { Shield } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { InfoCell } from './McpDetailContent';
import type { McpServerVersionDetail } from '@/types/mcp';
export function McpGovernanceInfo({ detail }: { detail: McpServerVersionDetail }) {
  const { t } = useTranslation();
  return (
              <Card className="overflow-hidden py-0 gap-0">
                <div className="px-4 py-3 border-b bg-muted/30">
                  <h2 className="text-sm font-semibold flex items-center gap-2">
                    <Shield className="h-4 w-4 text-muted-foreground" />
                    {t('mcp.governance')}
                  </h2>
                </div>
                <CardContent className="p-4 space-y-1">
                  <InfoCell label={t('mcp.owner')} value={detail.owner || '-'} compact />
                  <InfoCell label={t('mcp.scope')} value={detail.scope || '-'} compact />
                  <InfoCell
                    label={t('mcp.editingVersion')}
                    value={detail.editingVersion || '-'}
                    compact
                  />
                  <InfoCell
                    label={t('mcp.reviewingVersion')}
                    value={detail.reviewingVersion || '-'}
                    compact
                  />
                  <InfoCell
                    label={t('mcp.onlineCount')}
                    value={String(detail.onlineCount ?? 0)}
                    compact
                  />
                  <InfoCell
                    label={t('mcp.latestVersion')}
                    value={detail.labels?.latest || '-'}
                    compact
                  />
                </CardContent>
              </Card>
  );
}
