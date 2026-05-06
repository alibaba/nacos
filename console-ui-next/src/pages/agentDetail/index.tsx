import { useEffect, useCallback, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

import {
  ArrowLeft,
  Pencil,
  Bot,
  ExternalLink,
  Zap,
  Radio,
  History,
  Server,
  Hash,
  Shield,
  Network,
  Link2,
  FileText,
  Image,
  ShieldCheck,
} from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { Switch } from '@/components/ui/switch';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetDescription,
} from '@/components/ui/sheet';
import { ScrollArea } from '@/components/ui/scroll-area';
import { useAgentStore } from '@/stores/agent-store';
import { useNamespaceStore } from '@/stores/namespace-store';
import { cn } from '@/lib/utils';
import type { AgentSkill } from '@/types/agent';
import { mapAgentCardForDisplay } from './agent-card-display';

export default function AgentDetailPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { currentNamespace } = useNamespaceStore();

  const agentName = searchParams.get('name') || '';
  const namespaceId = searchParams.get('namespaceId') || currentNamespace || 'public';
  const versionParam = searchParams.get('version') || undefined;

  const {
    currentAgent,
    detailLoading,
    versionList,
    error,
    fetchAgentDetail,
    fetchVersionList,
    clearError,
  } = useAgentStore();

  const [selectedVersion, setSelectedVersion] = useState<string | undefined>(versionParam);
  const [iconError, setIconError] = useState(false);
  const [versionSheetOpen, setVersionSheetOpen] = useState(false);

  const loadDetail = useCallback(
    (version?: string) => {
      if (agentName) {
        fetchAgentDetail(namespaceId, agentName, version);
      }
    },
    [fetchAgentDetail, namespaceId, agentName]
  );

  useEffect(() => {
    loadDetail(versionParam);
    if (agentName) {
      fetchVersionList(namespaceId, agentName);
    }
    return () => {
      clearError();
    };
  }, [agentName, namespaceId]);

  const handleVersionChange = (version: string) => {
    setSelectedVersion(version);
    loadDetail(version);
    setVersionSheetOpen(false);
  };

  const handleEdit = () => {
    const params = new URLSearchParams({ mode: 'edit', name: agentName, namespaceId });
    navigate(`/newAgent?${params}`);
  };

  // Loading skeleton
  if (detailLoading && !currentAgent) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-32 w-full rounded-xl" />
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
          <div className="lg:col-span-2 space-y-4">
            <Skeleton className="h-48 w-full" />
            <Skeleton className="h-64 w-full" />
          </div>
          <div className="space-y-4">
            <Skeleton className="h-32 w-full" />
            <Skeleton className="h-48 w-full" />
          </div>
        </div>
      </div>
    );
  }

  // Error state
  if (error && !currentAgent) {
    return (
      <div className="flex flex-col items-center justify-center py-24 space-y-4">
        <p className="text-sm text-destructive">{t('agent.loadFailed')}</p>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => navigate('/agentManagement')}>
            {t('agent.backToList')}
          </Button>
          <Button onClick={() => loadDetail(selectedVersion)}>
            {t('mcp.retry')}
          </Button>
        </div>
      </div>
    );
  }

  if (!currentAgent) return null;

  const agent = currentAgent;
  const displayModel = mapAgentCardForDisplay(agent);
  const capabilities = agent.capabilities || {};
  const skills = agent.skills || [];

  return (
    <div className="space-y-5">
      {/* ===== Hero Header ===== */}
      <div className="relative rounded-xl border bg-card overflow-hidden">
        <div className="absolute inset-0 bg-gradient-to-br from-violet-500/[0.04] via-transparent to-fuchsia-500/[0.03]" />
        <div className="absolute top-0 right-0 w-64 h-64 bg-gradient-to-bl from-violet-500/[0.06] to-transparent rounded-full -translate-y-1/2 translate-x-1/3" />

        <div className="relative px-5 py-4">
          {/* Top bar */}
          <div className="flex items-center justify-between mb-4">
            <Button
              variant="ghost"
              size="sm"
              className="h-7 gap-1.5 text-muted-foreground hover:text-foreground -ml-2"
              onClick={() => navigate('/agentManagement')}
            >
              <ArrowLeft className="h-3.5 w-3.5" />
              {t('agent.backToList')}
            </Button>

            <div className="flex items-center gap-2">
              {/* Version selector */}
              {versionList.length > 0 && (
                <Select
                  value={selectedVersion || agent.version || ''}
                  onValueChange={handleVersionChange}
                >
                  <SelectTrigger className="w-[140px] h-7 text-xs bg-background/80">
                    <SelectValue placeholder={t('agent.selectVersion')} />
                  </SelectTrigger>
                  <SelectContent>
                    {versionList.map((v) => (
                      <SelectItem key={v.version} value={v.version}>
                        v{v.version}
                        {v.latest && (
                          <Badge className="ml-2 bg-emerald-100 text-emerald-700 dark:bg-emerald-950/50 dark:text-emerald-300 text-[10px] px-1 py-0 border-0">
                            {t('mcp.latestVersion')}
                          </Badge>
                        )}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}

              <Button variant="outline" size="sm" className="h-7 text-xs" onClick={() => setVersionSheetOpen(true)}>
                <History className="mr-1 h-3 w-3" />
                {t('agent.versionHistory')}
              </Button>

              <Button size="sm" className="h-7 text-xs" onClick={handleEdit}>
                <Pencil className="mr-1 h-3 w-3" />
                {t('common.edit')}
              </Button>
            </div>
          </div>

          {/* Agent identity */}
          <div className="flex items-start gap-4">
            <div className={cn(
              'flex h-14 w-14 shrink-0 items-center justify-center rounded-xl shadow-lg overflow-hidden',
              agent.iconUrl && !iconError
                ? 'bg-white dark:bg-muted border border-border/60 shadow-sm'
                : 'bg-gradient-to-br from-violet-500 to-fuchsia-400 shadow-violet-500/20'
            )}>
              {agent.iconUrl && !iconError ? (
                <img
                  src={agent.iconUrl}
                  alt={agent.name}
                  className="h-full w-full object-contain p-2"
                  onError={() => setIconError(true)}
                />
              ) : (
                <Bot className="h-7 w-7 text-white" />
              )}
            </div>

            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2.5 mb-1">
                <h1 className="text-xl font-bold tracking-tight">{agent.name}</h1>
                {agent.version && (
                  <span className="text-xs text-muted-foreground font-mono bg-muted/60 px-1.5 py-0.5 rounded">
                    v{agent.version}
                  </span>
                )}
              </div>
              {agent.description && (
                <p className="text-sm text-muted-foreground leading-relaxed max-w-2xl">
                  {agent.description}
                </p>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* ===== Content Grid ===== */}
      <div className={cn('grid grid-cols-1 lg:grid-cols-3 gap-5', detailLoading && 'opacity-50 pointer-events-none')}>
        {/* Left column - 2/3 */}
        <div className="lg:col-span-2 space-y-5">
          {/* Basic Info */}
          <Card className="overflow-hidden py-0 gap-0">
            <div className="px-5 py-3.5 border-b bg-muted/30">
              <h2 className="text-sm font-semibold flex items-center gap-2">
                <Server className="h-4 w-4 text-muted-foreground" />
                {t('agent.basicInfo')}
              </h2>
            </div>
            <CardContent className="p-0">
              <div className="divide-y divide-border">
                <InfoCell label={t('agent.agentName')} value={agent.name} icon={<Bot className="h-3.5 w-3.5" />} />
                <InfoCell label={t('agent.version')} value={agent.version || '-'} icon={<Hash className="h-3.5 w-3.5" />} />
              </div>
              {/* Links row */}
              {(agent.documentationUrl || agent.iconUrl) && (
                <div className="border-t px-5 py-3 flex items-center gap-4">
                  {agent.documentationUrl && (
                    <a
                      href={agent.documentationUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="inline-flex items-center gap-1.5 text-xs text-primary hover:underline"
                    >
                      <FileText className="h-3.5 w-3.5" />
                      {t('agent.documentation')}
                      <ExternalLink className="h-3 w-3" />
                    </a>
                  )}
                  {agent.iconUrl && (
                    <a
                      href={agent.iconUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="inline-flex items-center gap-1.5 text-xs text-primary hover:underline"
                    >
                      <Image className="h-3.5 w-3.5" />
                      {t('agent.iconUrl')}
                      <ExternalLink className="h-3 w-3" />
                    </a>
                  )}
                </div>
              )}
            </CardContent>
          </Card>

          {/* Skills */}
          <Card className="overflow-hidden py-0 gap-0">
            <div className="px-5 py-3.5 border-b bg-muted/30">
              <h2 className="text-sm font-semibold flex items-center gap-2">
                <Zap className="h-4 w-4 text-amber-500" />
                {t('agent.skills')}
                {skills.length > 0 && (
                  <span className="inline-flex items-center justify-center h-5 min-w-5 rounded-full bg-amber-100 dark:bg-amber-900/40 text-amber-700 dark:text-amber-300 text-[11px] font-semibold px-1.5">
                    {skills.length}
                  </span>
                )}
              </h2>
            </div>
            <CardContent className="p-4">
              {skills.length === 0 ? (
                <p className="text-sm text-muted-foreground text-center py-6">{t('agent.noSkills')}</p>
              ) : (
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  {skills.map((skill, index) => (
                    <SkillCard key={skill.id || index} skill={skill} />
                  ))}
                </div>
              )}
            </CardContent>
          </Card>

          {/* Security Schemes */}
          {!!agent.securitySchemes && (
            <Card className="overflow-hidden py-0 gap-0">
              <div className="px-5 py-3.5 border-b bg-muted/30">
                <h2 className="text-sm font-semibold flex items-center gap-2">
                  <Shield className="h-4 w-4 text-rose-500" />
                  {t('agent.securitySchemes')}
                </h2>
              </div>
              <CardContent className="p-4">
                <pre className="text-xs font-mono bg-muted/40 rounded-lg p-3 overflow-auto max-h-64 whitespace-pre-wrap break-all">
                  {typeof agent.securitySchemes === 'object'
                    ? JSON.stringify(agent.securitySchemes, null, 2)
                    : String(agent.securitySchemes)}
                </pre>
              </CardContent>
            </Card>
          )}

          {/* Security */}
          {!!agent.security && (
            <Card className="overflow-hidden py-0 gap-0">
              <div className="px-5 py-3.5 border-b bg-muted/30">
                <h2 className="text-sm font-semibold flex items-center gap-2">
                  <Shield className="h-4 w-4 text-muted-foreground" />
                  {t('agent.security')}
                </h2>
              </div>
              <CardContent className="p-4">
                <pre className="text-xs font-mono bg-muted/40 rounded-lg p-3 overflow-auto max-h-64 whitespace-pre-wrap break-all">
                  {typeof agent.security === 'object'
                    ? JSON.stringify(agent.security, null, 2)
                    : String(agent.security)}
                </pre>
              </CardContent>
            </Card>
          )}

          {/* Additional Interfaces */}
          {displayModel.interfaceList.length > 0 && (
            <Card className="overflow-hidden py-0 gap-0">
              <div className="px-5 py-3.5 border-b bg-muted/30">
                <h2 className="text-sm font-semibold flex items-center gap-2">
                  <Link2 className="h-4 w-4 text-muted-foreground" />
                  {t('agent.supportedInterfaces')}
                </h2>
              </div>
              <CardContent className="p-4">
                <div className="space-y-2">
                  {displayModel.interfaceList.map((iface, i) => (
                    <div key={i} className="rounded-lg border bg-muted/20 p-3 space-y-1">
                      <div className="flex items-center gap-2">
                        <p className="text-sm font-medium">{t('agent.interfaceItem', { index: i + 1 })}</p>
                        {i === 0 && (
                          <Badge variant="secondary" className="text-[10px] px-1.5 py-0">
                            {t('agent.preferredInterface')}
                          </Badge>
                        )}
                      </div>
                      {iface.url && (
                        <p className="text-xs text-muted-foreground">{t('agent.serviceUrl')}: {iface.url}</p>
                      )}
                      {!!iface.protocolBinding && (
                        <p className="text-xs text-muted-foreground">{t('agent.transport')}: {iface.protocolBinding}</p>
                      )}
                      {!!iface.protocolVersion && (
                        <p className="text-xs text-muted-foreground">{t('agent.protocolVersion')}: {iface.protocolVersion}</p>
                      )}
                      {!!iface.tenant && (
                        <p className="text-xs text-muted-foreground">{t('agent.tenant')}: {iface.tenant}</p>
                      )}
                    </div>
                  ))}
                </div>
              </CardContent>
            </Card>
          )}
        </div>

        {/* Right column - 1/3 */}
        <div className="space-y-5">
          {/* Capabilities */}
          <Card className="overflow-hidden py-0 gap-0">
            <div className="px-5 py-3.5 border-b bg-muted/30">
              <h2 className="text-sm font-semibold flex items-center gap-2">
                <Zap className="h-4 w-4 text-muted-foreground" />
                {t('agent.capabilities')}
              </h2>
            </div>
            <CardContent className="p-4">
              <div className="space-y-2">
                <CapabilityRow
                  icon={<Zap className="h-4 w-4 text-amber-500" />}
                  label={t('agent.streaming')}
                  description={t('agent.streamingDesc')}
                  enabled={!!capabilities.streaming}
                />
                <CapabilityRow
                  icon={<Radio className="h-4 w-4 text-blue-500" />}
                  label={t('agent.pushNotifications')}
                  description={t('agent.pushNotificationsDesc')}
                  enabled={!!capabilities.pushNotifications}
                />
                <CapabilityRow
                  icon={<History className="h-4 w-4 text-emerald-500" />}
                  label={t('agent.stateHistory')}
                  description={t('agent.stateHistoryDesc')}
                  enabled={!!capabilities.stateTransitionHistory}
                />
              </div>
            </CardContent>
          </Card>

          {/* Input/Output Modes */}
          {(agent.defaultInputModes?.length || agent.defaultOutputModes?.length) ? (
            <Card className="overflow-hidden py-0 gap-0">
              <div className="px-5 py-3.5 border-b bg-muted/30">
                <h2 className="text-sm font-semibold flex items-center gap-2">
                  <Network className="h-4 w-4 text-muted-foreground" />
                  {t('agent.ioModes')}
                </h2>
              </div>
              <CardContent className="p-4 space-y-3">
                {agent.defaultInputModes && agent.defaultInputModes.length > 0 && (
                  <div>
                    <p className="text-xs font-medium text-muted-foreground mb-1.5">{t('agent.inputModes')}</p>
                    <div className="flex flex-wrap gap-1">
                      {agent.defaultInputModes.map((mode) => (
                        <Badge key={mode} variant="outline" className="text-xs">{mode}</Badge>
                      ))}
                    </div>
                  </div>
                )}
                {agent.defaultOutputModes && agent.defaultOutputModes.length > 0 && (
                  <div>
                    <p className="text-xs font-medium text-muted-foreground mb-1.5">{t('agent.outputModes')}</p>
                    <div className="flex flex-wrap gap-1">
                      {agent.defaultOutputModes.map((mode) => (
                        <Badge key={mode} variant="secondary" className="text-xs">{mode}</Badge>
                      ))}
                    </div>
                  </div>
                )}
              </CardContent>
            </Card>
          ) : null}

          {/* Provider */}
          {agent.provider && (agent.provider.organization || agent.provider.url) && (
            <Card className="overflow-hidden py-0 gap-0">
              <div className="px-5 py-3.5 border-b bg-muted/30">
                <h2 className="text-sm font-semibold flex items-center gap-2">
                  <Server className="h-4 w-4 text-muted-foreground" />
                  {t('agent.provider')}
                </h2>
              </div>
              <CardContent className="p-4 space-y-2">
                {agent.provider.organization && (
                  <div>
                    <p className="text-[11px] text-muted-foreground mb-0.5">{t('agent.providerName')}</p>
                    <p className="text-sm font-medium">{agent.provider.organization}</p>
                  </div>
                )}
                {agent.provider.url && (
                  <div>
                    <p className="text-[11px] text-muted-foreground mb-0.5">{t('agent.providerUrl')}</p>
                    <a
                      href={agent.provider.url}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="inline-flex items-center gap-1 text-xs text-primary hover:underline break-all"
                    >
                      {agent.provider.url}
                      <ExternalLink className="h-3 w-3 shrink-0" />
                    </a>
                  </div>
                )}
              </CardContent>
            </Card>
          )}

          {/* Extended Card Support */}
          {agent.capabilities?.extendedAgentCard !== undefined && (
            <Card className="overflow-hidden py-0 gap-0">
              <CardContent className="p-4">
                <div className="flex items-center gap-3 rounded-lg border px-3.5 py-2.5 bg-muted/30 border-border/60">
                  <div className="flex items-center justify-center h-8 w-8 rounded-md bg-background">
                    <ShieldCheck className="h-4 w-4 text-indigo-500" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium">{t('agent.extendedCardSupport')}</p>
                    <p className="text-[11px] text-muted-foreground">{t('agent.extendedCardSupportDesc')}</p>
                  </div>
                  <Switch checked={displayModel.extendedCardSupported} disabled className="scale-[0.75]" />
                </div>
              </CardContent>
            </Card>
          )}
        </div>
      </div>

      {/* ===== Version History Sheet ===== */}
      <Sheet open={versionSheetOpen} onOpenChange={setVersionSheetOpen}>
        <SheetContent className="flex flex-col p-0 sm:max-w-md">
          <SheetHeader className="px-6 pt-6 pb-4 border-b shrink-0">
            <SheetTitle className="flex items-center gap-2">
              <History className="h-4.5 w-4.5 text-violet-500" />
              {t('agent.versionHistory')}
            </SheetTitle>
            <SheetDescription>
              {t('agent.totalVersions', { count: versionList.length })}
            </SheetDescription>
          </SheetHeader>

          <ScrollArea className="flex-1">
            <div className="p-4 space-y-2">
              {versionList.length === 0 ? (
                <p className="text-sm text-muted-foreground text-center py-12">{t('agent.noVersions', { defaultValue: '暂无版本历史' })}</p>
              ) : (
                versionList.map((v) => {
                  const isCurrent = v.version === (selectedVersion || agent.version);
                  return (
                    <div
                      key={v.version}
                      className={cn(
                        'rounded-lg border px-4 py-3 cursor-pointer transition-colors hover:bg-muted/50',
                        isCurrent && 'border-violet-500/50 bg-violet-50/50 dark:bg-violet-950/20'
                      )}
                      onClick={() => handleVersionChange(v.version)}
                    >
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <span className="text-sm font-mono font-semibold">v{v.version}</span>
                          {v.latest && (
                            <Badge className="bg-emerald-100 text-emerald-700 dark:bg-emerald-950/50 dark:text-emerald-300 text-[10px] px-1.5 py-0 border-0">
                              Latest
                            </Badge>
                          )}
                          {isCurrent && (
                            <Badge variant="outline" className="text-[10px] px-1.5 py-0 border-violet-500/50 text-violet-600 dark:text-violet-400">
                              {t('agent.currentVersion', { defaultValue: '当前版本' })}
                            </Badge>
                          )}
                        </div>
                      </div>
                    </div>
                  );
                })
              )}
            </div>
          </ScrollArea>
        </SheetContent>
      </Sheet>
    </div>
  );
}

// ===== Sub-components =====

function InfoCell({
  label,
  value,
  icon,
}: {
  label: string;
  value: React.ReactNode;
  icon?: React.ReactNode;
}) {
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

function CapabilityRow({
  icon,
  label,
  description,
  enabled,
}: {
  icon: React.ReactNode;
  label: string;
  description: string;
  enabled: boolean;
}) {
  return (
    <div className={cn(
      'flex items-center gap-3 rounded-lg border px-3.5 py-2.5',
      enabled
        ? 'bg-primary/5 border-primary/20'
        : 'bg-muted/30 border-border/60'
    )}>
      <div className="flex items-center justify-center h-8 w-8 rounded-md bg-background">
        {icon}
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-sm font-medium">{label}</p>
        <p className="text-[11px] text-muted-foreground">{description}</p>
      </div>
      <Switch checked={enabled} disabled className="scale-[0.75]" />
    </div>
  );
}

function SkillCard({ skill }: { skill: AgentSkill }) {
  const tags = skill.tags || [];
  const inputModes = skill.inputModes || [];
  const outputModes = skill.outputModes || [];

  return (
    <div className="rounded-lg border bg-muted/20 p-3 space-y-2">
      <div>
        <p className="text-sm font-semibold">{skill.name}</p>
        {skill.id && (
          <p className="text-[10px] text-muted-foreground font-mono">{skill.id}</p>
        )}
      </div>
      {skill.description && (
        <p className="text-xs text-muted-foreground line-clamp-2">{skill.description}</p>
      )}
      {tags.length > 0 && (
        <div className="flex flex-wrap gap-1">
          {tags.map((tag, i) => (
            <Badge key={i} variant="outline" className="text-[10px]">{tag}</Badge>
          ))}
        </div>
      )}
      {(inputModes.length > 0 || outputModes.length > 0) && (
        <div className="flex items-center gap-2 text-[10px] text-muted-foreground">
          {inputModes.length > 0 && (
            <span>In: {inputModes.join(', ')}</span>
          )}
          {outputModes.length > 0 && (
            <span>Out: {outputModes.join(', ')}</span>
          )}
        </div>
      )}
    </div>
  );
}
