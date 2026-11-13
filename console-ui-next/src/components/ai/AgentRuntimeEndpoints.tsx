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
import { Server, RefreshCw, ExternalLink } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { namingDetailPath, runtimeEndpointStatus } from '@/pages/newAgent/agent-console-model';
import type { ConsoleRuntimeEndpointView } from '@/types/agent';

function formatTime(value?: number | null): string {
  return value ? new Date(value).toLocaleString() : '-';
}
export function AgentRuntimeEndpoints({ runtimeView, runtimeLoading = false, error, onRefresh }: {
  runtimeView: ConsoleRuntimeEndpointView | null | undefined; runtimeLoading?: boolean; error?: string;
  onRefresh: () => void;
}) {
  const { t } = useTranslation();
  return <div className="space-y-2">{error && <p role="alert" className="text-sm text-destructive">{error}</p>}
            <Card className="py-0 gap-0 overflow-hidden">
              <div className="px-5 py-3.5 border-b bg-muted/30 flex items-center justify-between">
                <h2 className="text-sm font-semibold flex items-center gap-2">
                  <Server className="h-4 w-4" />
                  {t('agent.runtimeEndpoints')}
                </h2>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={runtimeLoading}
                  onClick={onRefresh}
                >
                  <RefreshCw className="mr-1.5 h-3.5 w-3.5" />
                  {t('common.refresh')}
                </Button>
              </div>
              <CardContent className="p-5 space-y-4">
                {runtimeLoading && !runtimeView ? (
                  <Skeleton className="h-24 w-full" />
                ) : (runtimeView?.runtimeEndpointSnapshot.callInterface.endpointSets?.[0]?.endpoints || []).length === 0 ? (
                  <p className="text-sm text-muted-foreground">{t('agent.noRuntimeEndpoints')}</p>
                ) : (
                  <div className="space-y-2">
                    {runtimeView?.runtimeEndpointSnapshot.callInterface.endpointSets?.[0]?.endpoints.map((item) => (
                      <div key={`${item.uri}@@${item.transport}`} className="rounded-lg border p-3">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="font-mono text-sm break-all">{item.uri}</span>
                          <Badge>{runtimeEndpointStatus(item)}</Badge>
                          <Badge variant="outline">{item.transport}</Badge>
                        </div>
                        <p className="text-xs text-muted-foreground mt-2">
                          {(item.bindings || []).map(
                            (binding) => `${binding.runtimeVersion} → ${binding.versionRange}`,
                          ).join(', ')}
                        </p>
                        <p className="text-xs text-muted-foreground mt-1">
                          {formatTime(runtimeView?.runtimeEndpointSnapshot.callInterface.endpointSets?.[0]?.lastUpdatedTime)}
                        </p>
                      </div>
                    ))}
                  </div>
                )}
                {runtimeView?.namingServiceRef && (
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => { window.location.hash = namingDetailPath(runtimeView!.namingServiceRef!); }}
                  >
                    <ExternalLink className="mr-1.5 h-3.5 w-3.5" />
                    {runtimeView.namingServiceRef.groupName}
                    @@
                    {runtimeView.namingServiceRef.serviceName}
                  </Button>
                )}
                <p className="text-xs text-muted-foreground">
                  {t('agent.runtimeReadonly')}
                </p>
              </CardContent>
            </Card></div>;
}
