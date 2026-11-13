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
import { Layers3 } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { DetailTagChip } from '@/components/ai/DetailTagChip';
import { ReadOnlyVersionLabelsCard } from './ReadOnlyVersionLabelsCard';
import { getProtocols, formatProtocolLabel, declaredEndpointsOf, endpointSourceOrderLabelKey } from '@/pages/newAgent/agent-console-model';
import type { AgentSummary, AgentVersionDetail } from '@/types/agent';

export function AgentDetailHeaderFrame({ children }: { children: ReactNode }) {
  return <div className="relative overflow-hidden rounded-xl border bg-card">
    <div className="absolute inset-0 bg-gradient-to-br from-violet-500/[0.04] via-transparent to-fuchsia-500/[0.03]" />
    <div className="relative p-5">{children}</div>
  </div>;
}

export function AgentDetailLayout({ children, sidebar }: { children: ReactNode; sidebar: ReactNode }) {
  return <div className="grid grid-cols-1 xl:grid-cols-3 gap-5">
    <div className="xl:col-span-2 space-y-5">{children}</div>
    <div className="space-y-5">{sidebar}</div>
  </div>;
}

export function AgentCallInterfaces({ currentVersion, selectedProtocol, setSelectedProtocol }: {
  currentVersion?: AgentVersionDetail | null; selectedProtocol: string; setSelectedProtocol: (value: string) => void;
}) {
  const { t } = useTranslation();
  const protocols = getProtocols(currentVersion?.callInterfaces || []);
  const selectedInterface = currentVersion?.callInterfaces.find(item => item.protocol === selectedProtocol);
  const sourceOrderLabelKey = endpointSourceOrderLabelKey(selectedInterface?.endpointSourceOrder);
  return <>
          <Card className="py-0 gap-0 overflow-hidden">
            <div className="px-5 py-3.5 border-b bg-muted/30 flex items-center justify-between">
              <h2 className="flex items-center gap-2 text-sm font-semibold">
                <Layers3 className="h-4 w-4" />
                {t('agent.supportedProtocols')}
              </h2>
              <span className="text-xs text-muted-foreground">
                {protocols.length}
              </span>
            </div>
            <CardContent className="p-5 space-y-4">
              {currentVersion ? (
                <>
                  {protocols.length === 0 ? (
                    <p className="text-sm text-muted-foreground">{t('agent.noCallInterfaces')}</p>
                  ) : (
                      <Tabs value={selectedProtocol} onValueChange={setSelectedProtocol}>
                        <div className="overflow-x-auto pb-1">
                          <TabsList className="w-max min-w-full justify-start">
                          {protocols.map((protocol) => (
                            <TabsTrigger key={protocol} value={protocol}>
                              {formatProtocolLabel(protocol)}
                            </TabsTrigger>
                          ))}
                          </TabsList>
                        </div>
                      {selectedInterface && (
                        <TabsContent value={selectedProtocol} className="mt-4 space-y-4">
                          <div className="rounded-lg border bg-muted/15 p-4">
                          <div className="grid grid-cols-1 gap-3 text-sm md:grid-cols-2 xl:grid-cols-4">
                            <Info
                              label={t('agent.protocol')}
                              value={formatProtocolLabel(selectedInterface.protocol)}
                            />
                            <Info
                              label={t('agent.protocolVersion')}
                              value={selectedInterface.protocolVersion || '-'}
                            />
                            <Info
                              label={t('agent.descriptorMediaType')}
                              value={selectedInterface.descriptorMediaType || '-'}
                            />
                            <Info
                              label={t('agent.sourceOrder')}
                              value={sourceOrderLabelKey ? t(sourceOrderLabelKey) : '-'}
                            />
                          </div>
                          </div>
                          <div className="space-y-2">
                            <div className="flex items-center justify-between">
                              <h3 className="text-xs font-medium">
                                {t('agent.declaredEndpoints')}
                              </h3>
                              <span className="text-xs text-muted-foreground">
                                {t('agent.declaredEndpointCount', {
                                  count: declaredEndpointsOf(selectedInterface).length,
                                })}
                              </span>
                            </div>
                            {declaredEndpointsOf(selectedInterface).length === 0 ? (
                              <div className="rounded-lg border border-dashed p-3 text-xs text-muted-foreground">
                                {t('agent.noDeclaredEndpoints')}
                              </div>
                            ) : (
                              <div className="space-y-2">
                                {declaredEndpointsOf(selectedInterface).map((endpoint) => (
                                  <div
                                    key={`${endpoint.uri}@@${endpoint.transport}`}
                                    className="flex flex-col gap-1 rounded-lg border bg-muted/10 p-3 sm:flex-row sm:items-center sm:justify-between"
                                  >
                                    <span className="break-all font-mono text-xs">
                                      {endpoint.uri}
                                    </span>
                                    <Badge variant="outline">{endpoint.transport}</Badge>
                                  </div>
                                ))}
                              </div>
                            )}
                          </div>
                          <h3 className="text-xs font-medium">
                            {t('agent.nativeDescriptorTitle')}
                          </h3>
                          <pre className="max-h-80 overflow-auto rounded-lg bg-muted/40 p-3 text-xs">
                            {JSON.stringify(selectedInterface.nativeDescriptor, null, 2)}
                          </pre>
                        </TabsContent>
                      )}
                      </Tabs>
                  )}
                </>
              ) : (
                <p className="text-sm text-muted-foreground">{t('agent.selectVersion')}</p>
              )}
            </CardContent>
          </Card>
  </>;
}

export function AgentDetailSidebar({ agent, labelsText, readOnlyLabels, onLabelsChange, onSave }: {
  agent: Pick<AgentSummary, 'owner' | 'provider' | 'tags' | 'versionInfo'>;
  labelsText: string; onLabelsChange?: (value: string) => void; onSave?: () => void;
  readOnlyLabels?: Record<string, string>;
}) {
  const { t } = useTranslation();
  return <>
          <Card className="py-0 gap-0 overflow-hidden">
            <div className="px-5 py-3.5 border-b bg-muted/30">
              <h2 className="text-sm font-semibold">{t('agent.metadata')}</h2>
            </div>
            <CardContent className="p-5 space-y-3">
              <Info label={t('agent.owner')} value={agent.owner || '-'} />
              <Info label={t('agent.provider')} value={agent.provider?.name || '-'} />
              <div>
                <p className="text-xs text-muted-foreground">{t('agent.tags')}</p>
                {(agent.tags || []).length > 0 ? (
                  <div className="mt-1.5 flex flex-wrap gap-1.5">
                    {agent.tags?.map((tag) => <DetailTagChip key={tag} label={tag} />)}
                  </div>
                ) : (
                  <p className="text-sm">-</p>
                )}
              </div>
              <Info
                label={t('agent.latestVersion')}
                value={agent.versionInfo?.labels?.latest || '-'}
              />
              <Info
                label={t('agent.onlineVersions')}
                value={String(agent.versionInfo?.onlineVersions?.length || 0)}
              />
            </CardContent>
          </Card>

          {readOnlyLabels ? <ReadOnlyVersionLabelsCard title={t('agent.customLabels')} labels={Object.keys(readOnlyLabels)} /> : <Card className="py-0 gap-0 overflow-hidden">
            <div className="px-5 py-3.5 border-b bg-muted/30">
              <h2 className="text-sm font-semibold">{t('agent.customLabels')}</h2>
            </div>
            <CardContent className="p-5 space-y-3">
              <Textarea
                value={labelsText}
                rows={5}
                className="font-mono text-xs"
                readOnly={!onLabelsChange}
                onChange={(event) => onLabelsChange?.(event.target.value)}
              />
              {onSave && <Button size="sm" onClick={onSave}>{t('common.save')}</Button>}
              <p className="text-xs text-muted-foreground">{t('agent.latestLabelManaged')}</p>
            </CardContent>
          </Card>}
  </>;
}

function Info({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="text-sm break-words">{value}</p>
    </div>
  );
}
