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

import { useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Cpu, Globe, ExternalLink, Shield, Package, Network, Zap, Lock, Eye, EyeOff, Server, GitBranch, Hash, RefreshCw } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Separator } from '@/components/ui/separator';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { McpToolList } from '@/components/ai/mcp/McpToolList';
import { McpClientConfigCard } from '@/pages/mcpServerDetail/McpClientConfigCard';
import { buildMcpClientConfigurations } from '@/pages/mcpServerDetail/mcp-client-config';
import { cn } from '@/lib/utils';
import type { McpServerDetailInfo } from '@/types/mcp';

export const PROTOCOL_STYLES: Record<string, { bg: string; text: string; dot: string }> = {
  stdio: { bg: 'bg-purple-50 dark:bg-purple-950/40', text: 'text-purple-700 dark:text-purple-300', dot: 'bg-purple-500' },
  'mcp-sse': { bg: 'bg-blue-50 dark:bg-blue-950/40', text: 'text-blue-700 dark:text-blue-300', dot: 'bg-blue-500' },
  'mcp-streamable': { bg: 'bg-cyan-50 dark:bg-cyan-950/40', text: 'text-cyan-700 dark:text-cyan-300', dot: 'bg-cyan-500' },
  http: { bg: 'bg-orange-50 dark:bg-orange-950/40', text: 'text-orange-700 dark:text-orange-300', dot: 'bg-orange-500' },
  dubbo: { bg: 'bg-green-50 dark:bg-green-950/40', text: 'text-green-700 dark:text-green-300', dot: 'bg-green-500' },
};

/** Ordinary detail content shared by source MCP and its pinned Hub reference. */
export function McpDetailContent({ mcp, namespaceId = '', loading = false, management }: {
  mcp: McpServerDetailInfo; namespaceId?: string; loading?: boolean; management?: ReactNode;
}) {
  const { t } = useTranslation();
  const protocolLabel = mcp.frontProtocol || mcp.protocol || 'unknown';
  const protocolStyle = PROTOCOL_STYLES[protocolLabel];
  const isStdio = protocolLabel === 'stdio';
  const isRestToMcp = mcp.protocol === 'http' || mcp.protocol === 'https';
  const tools = mcp.toolSpec?.tools || [];
  const securitySchemes = mcp.toolSpec?.securitySchemes || [];
  const packages = mcp.packages || [];
  const backendEndpoints = mcp.backendEndpoints || [];
  const frontendEndpoints = mcp.frontendEndpoints || [];
  const clientConfigurations = buildMcpClientConfigurations(mcp);
  return (
      <div className={cn('grid grid-cols-1 lg:grid-cols-3 gap-5', loading && 'opacity-50 pointer-events-none')}>
        {/* Left column - 2/3 */}
        <div className="lg:col-span-2 space-y-5">
          {/* Basic Info */}
          <Card className="overflow-hidden py-0 gap-0">
            <div className="px-5 py-3.5 border-b bg-muted/30">
              <h2 className="text-sm font-semibold flex items-center gap-2">
                <Server className="h-4 w-4 text-muted-foreground" />
                {t('mcp.basicInfo')}
              </h2>
            </div>
            <CardContent className="p-0">
              <div className="grid grid-cols-1 sm:grid-cols-2 divide-y sm:divide-y-0 sm:divide-x divide-border">
                {/* Left column */}
                <div className="divide-y divide-border">
                  <InfoCell label={t('mcp.serverName')} value={mcp.name} icon={<Cpu className="h-3.5 w-3.5" />} />
                  <InfoCell label={t('mcp.version')} value={mcp.versionDetail?.version || mcp.version || '-'} icon={<Hash className="h-3.5 w-3.5" />} />
                  {mcp.remoteServerConfig?.serviceRef && (
                    <InfoCell label={t('mcp.serviceRef')} value={
                      <a
                        className="inline-flex items-center gap-1 text-primary hover:underline cursor-pointer"
                        href={`#/serviceDetail?${new URLSearchParams({
                          serviceName: mcp.remoteServerConfig!.serviceRef!.serviceName,
                          groupName: mcp.remoteServerConfig!.serviceRef!.groupName,
                          namespace: mcp.remoteServerConfig!.serviceRef!.namespaceId || namespaceId,
                        })}`}
                      >
                        <span>{mcp.remoteServerConfig.serviceRef.groupName}@@{mcp.remoteServerConfig.serviceRef.serviceName}</span>
                        <ExternalLink className="h-3 w-3" />
                      </a>
                    } icon={<Server className="h-3.5 w-3.5" />} />
                  )}
                </div>
                {/* Right column */}
                <div className="divide-y divide-border">
                  <InfoCell label={t('mcp.protocol')} value={
                    <div className="flex items-center gap-1.5 flex-wrap">
                      <span className={cn(
                        'inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium',
                        protocolStyle?.bg, protocolStyle?.text
                      )}>
                        {protocolLabel}
                      </span>
                      {isRestToMcp && (
                        <span className="inline-flex items-center gap-0.5 rounded-full bg-amber-50 dark:bg-amber-950/40 text-amber-700 dark:text-amber-300 px-1.5 py-0.5 text-[10px] font-medium">
                          <RefreshCw className="h-2.5 w-2.5" />
                          HTTP {t('mcp.restToMcp', { defaultValue: '转换' })}
                        </span>
                      )}
                    </div>
                  } icon={<Network className="h-3.5 w-3.5" />} />
                  {mcp.remoteServerConfig?.exportPath && (
                    <InfoCell label={t('mcp.exportPath')} value={
                      <span className="font-mono text-xs bg-muted/60 px-1.5 py-0.5 rounded">{mcp.remoteServerConfig.exportPath}</span>
                    } icon={<GitBranch className="h-3.5 w-3.5" />} />
                  )}
                </div>
              </div>
              {/* Links row */}
              {(mcp.websiteUrl || mcp.repository?.url) && (
                <div className="border-t px-5 py-3 flex items-center gap-4">
                  {mcp.websiteUrl && (
                    <a
                      href={mcp.websiteUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="inline-flex items-center gap-1.5 text-xs text-primary hover:underline"
                    >
                      <Globe className="h-3.5 w-3.5" />
                      {t('mcp.websiteUrl')}
                      <ExternalLink className="h-3 w-3" />
                    </a>
                  )}
                  {mcp.repository?.url && (
                    <a
                      href={mcp.repository.url}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="inline-flex items-center gap-1.5 text-xs text-primary hover:underline"
                    >
                      <GitBranch className="h-3.5 w-3.5" />
                      {mcp.repository.source || t('mcp.repository')}
                      <ExternalLink className="h-3 w-3" />
                    </a>
                  )}
                </div>
              )}
            </CardContent>
          </Card>

          {/* Tools */}
          <Card className="overflow-hidden py-0 gap-0">
            <div className="px-5 py-3.5 border-b bg-muted/30 flex items-center justify-between">
              <h2 className="text-sm font-semibold flex items-center gap-2">
                <Zap className="h-4 w-4 text-amber-500" />
                {t('mcp.tools')}
                {tools.length > 0 && (
                  <span className="inline-flex items-center justify-center h-5 min-w-5 rounded-full bg-amber-100 dark:bg-amber-900/40 text-amber-700 dark:text-amber-300 text-[11px] font-semibold px-1.5">
                    {tools.length}
                  </span>
                )}
              </h2>
            </div>
            <CardContent className="p-0">
              <McpToolList tools={tools} toolsMeta={mcp.toolSpec?.toolsMeta} className="border-0 rounded-none" />
            </CardContent>
          </Card>

          {/* Security Schemes */}
          {securitySchemes.length > 0 && (
            <Card className="overflow-hidden py-0 gap-0">
              <div className="px-5 py-3.5 border-b bg-muted/30">
                <h2 className="text-sm font-semibold flex items-center gap-2">
                  <Shield className="h-4 w-4 text-rose-500" />
                  {t('mcp.securitySchemes')}
                  <span className="inline-flex items-center justify-center h-5 min-w-5 rounded-full bg-rose-100 dark:bg-rose-900/40 text-rose-700 dark:text-rose-300 text-[11px] font-semibold px-1.5">
                    {securitySchemes.length}
                  </span>
                </h2>
              </div>
              <CardContent className="p-0">
                <Table>
                  <TableHeader>
                    <TableRow className="bg-muted/20">
                      <TableHead className="w-[120px] pl-5">ID</TableHead>
                      <TableHead>{t('mcp.securityType')}</TableHead>
                      <TableHead>{t('mcp.securitySchemeField')}</TableHead>
                      <TableHead>{t('mcp.securityIn')}</TableHead>
                      <TableHead>{t('mcp.securityDefaultCredential')}</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {securitySchemes.map((scheme) => (
                      <TableRow key={scheme.id}>
                        <TableCell className="font-mono text-xs pl-5">{scheme.id}</TableCell>
                        <TableCell>
                          <Badge variant="outline" className="text-xs">
                            {scheme.type}
                          </Badge>
                        </TableCell>
                        <TableCell className="text-sm">{scheme.scheme || '-'}</TableCell>
                        <TableCell className="text-sm">{scheme.in || '-'}</TableCell>
                        <TableCell>
                          {scheme.defaultCredential ? (
                            <CredentialCell value={scheme.defaultCredential} />
                          ) : (
                            <span className="text-muted-foreground">-</span>
                          )}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </CardContent>
            </Card>
          )}
        </div>

        {/* Right column - 1/3 */}
        <div className="space-y-5">
          <McpClientConfigCard configurations={clientConfigurations} />

          {/* Packages - stdio only */}
          {isStdio && (
            <Card className="overflow-hidden py-0 gap-0">
              <div className="px-5 py-3.5 border-b bg-muted/30">
                <h2 className="text-sm font-semibold flex items-center gap-2">
                  <Package className="h-4 w-4 text-muted-foreground" />
                  {t('mcp.packages')}
                </h2>
              </div>
              <CardContent className="p-4">
                {packages.length === 0 ? (
                  <p className="text-sm text-muted-foreground text-center py-4">
                    {t('mcp.noPackages')}
                  </p>
                ) : (
                  <div className="space-y-4">
                    {packages.map((pkg, idx) => (
                      <div key={idx} className="space-y-2">
                        {idx > 0 && <Separator />}
                        <div className="space-y-1.5">
                          {pkg.name && (
                            <InfoCell label={t('mcp.packageIdentifier')} value={pkg.name} compact />
                          )}
                          {pkg.version && (
                            <InfoCell label={t('mcp.packageVersion')} value={pkg.version} compact />
                          )}
                          {pkg.runtimeHint && (
                            <InfoCell label={t('mcp.packageRuntime')} value={pkg.runtimeHint} compact />
                          )}
                          {pkg.registryType && (
                            <InfoCell label="Registry" value={pkg.registryType} compact />
                          )}
                        </div>

                        {/* Runtime arguments */}
                        {pkg.runtimeArguments && pkg.runtimeArguments.length > 0 && (
                          <div>
                            <p className="text-xs font-medium text-muted-foreground mb-1">
                              {t('mcp.runtimeArguments')}
                            </p>
                            <div className="flex flex-wrap gap-1">
                              {pkg.runtimeArguments.map((arg, i) => (
                                <Badge key={i} variant="outline" className="text-[10px] font-mono">
                                  {arg.name ? `${arg.name}=${arg.value}` : arg.value}
                                </Badge>
                              ))}
                            </div>
                          </div>
                        )}

                        {/* Package arguments */}
                        {pkg.packageArguments && pkg.packageArguments.length > 0 && (
                          <div>
                            <p className="text-xs font-medium text-muted-foreground mb-1">
                              {t('mcp.packageArguments')}
                            </p>
                            <div className="flex flex-wrap gap-1">
                              {pkg.packageArguments.map((arg, i) => (
                                <Badge key={i} variant="outline" className="text-[10px] font-mono">
                                  {arg.name ? `${arg.name}=${arg.value}` : arg.value}
                                </Badge>
                              ))}
                            </div>
                          </div>
                        )}

                        {/* Environment variables */}
                        {pkg.environmentVariables && pkg.environmentVariables.length > 0 && (
                          <div>
                            <p className="text-xs font-medium text-muted-foreground mb-1">
                              {t('mcp.environmentVariables')}
                            </p>
                            <div className="space-y-1">
                              {pkg.environmentVariables.map((env, i) => (
                                <div
                                  key={i}
                                  className="flex items-center gap-2 text-xs bg-muted/50 rounded-md px-2.5 py-1.5"
                                >
                                  <span className="font-mono font-medium">{env.name}</span>
                                  {env.isRequired && (
                                    <Badge variant="destructive" className="text-[9px] px-1 py-0">
                                      {t('mcp.envVarRequired')}
                                    </Badge>
                                  )}
                                  {env.isSecret && (
                                    <Lock className="h-3 w-3 text-amber-500" />
                                  )}
                                  {env.description && (
                                    <span className="text-muted-foreground truncate">
                                      {env.description}
                                    </span>
                                  )}
                                </div>
                              ))}
                            </div>
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                )}
              </CardContent>
            </Card>
          )}

          {/* Endpoints - non-stdio */}
          {!isStdio && (backendEndpoints.length > 0 || frontendEndpoints.length > 0) && (
            <Card className="overflow-hidden py-0 gap-0">
              <div className="px-5 py-3.5 border-b bg-muted/30">
                <h2 className="text-sm font-semibold flex items-center gap-2">
                  <Network className="h-4 w-4 text-muted-foreground" />
                  {t('mcp.endpoints')}
                </h2>
              </div>
              <CardContent className="p-4 space-y-4">
                {backendEndpoints.length > 0 && (
                  <div>
                    <p className="text-xs font-medium text-muted-foreground mb-2">
                      {t('mcp.backendEndpoints')}
                    </p>
                    <div className="space-y-2">
                      {backendEndpoints.map((ep, i) => (
                        <EndpointItem key={i} endpoint={ep} t={t} />
                      ))}
                    </div>
                  </div>
                )}
                {frontendEndpoints.length > 0 && (
                  <div>
                    <p className="text-xs font-medium text-muted-foreground mb-2">
                      {t('mcp.frontendEndpoints')}
                    </p>
                    <div className="space-y-2">
                      {frontendEndpoints.map((ep, i) => (
                        <EndpointItem key={i} endpoint={ep} t={t} />
                      ))}
                    </div>
                  </div>
                )}
              </CardContent>
            </Card>
          )}

          {management}

        </div>
      </div>

  );
}

export function InfoCell({
  label,
  value,
  icon,
  compact,
}: {
  label: string;
  value: React.ReactNode;
  icon?: React.ReactNode;
  compact?: boolean;
}) {
  if (compact) {
    return (
      <div className="flex items-center justify-between py-1">
        <span className="text-[11px] text-muted-foreground">{label}</span>
        <span className="text-xs font-medium">{value || '-'}</span>
      </div>
    );
  }
  return (
    <div className="flex items-center gap-3 px-5 py-3">
      {icon && (
        <span className="text-muted-foreground/60 shrink-0">{icon}</span>
      )}
      <div className="min-w-0 flex-1">
        <p className="text-[11px] text-muted-foreground leading-none mb-1">{label}</p>
        <div className="text-sm font-medium break-all">{value || '-'}</div>
      </div>
    </div>
  );
}

function CredentialCell({ value }: { value: string }) {
  const [visible, setVisible] = useState(false);
  return (
    <div className="flex items-center gap-1.5">
      <span className="text-xs font-mono">
        {visible ? value : '********'}
      </span>
      <button onClick={() => setVisible(!visible)} className="text-muted-foreground hover:text-foreground transition-colors">
        {visible ? <EyeOff className="h-3 w-3" /> : <Eye className="h-3 w-3" />}
      </button>
    </div>
  );
}

function EndpointItem({
  endpoint,
  t,
}: {
  endpoint: { protocol: string; address: string; port: string; path?: string; headers?: { name: string; value?: string }[] };
  t: (key: string) => string;
}) {
  const protocol = endpoint.protocol || 'http';
  const url = `${protocol}://${endpoint.address}${endpoint.port ? ':' + endpoint.port : ''}${endpoint.path || ''}`;
  return (
    <div className="rounded-lg border bg-muted/20 p-3 space-y-2">
      <div className="flex items-center gap-2">
        <span className="inline-flex items-center rounded-md bg-primary/10 text-primary px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wider">
          {protocol}
        </span>
        <span className="text-xs font-mono text-muted-foreground truncate">{url}</span>
      </div>
      {endpoint.headers && endpoint.headers.length > 0 && (
        <div>
          <p className="text-[11px] text-muted-foreground mb-1">{t('mcp.endpointHeaders')}</p>
          <div className="flex flex-wrap gap-1">
            {endpoint.headers.map((h, i) => (
              <Badge key={i} variant="secondary" className="text-[10px] font-mono">
                {h.name}{h.value ? `=${h.value}` : ''}
              </Badge>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
