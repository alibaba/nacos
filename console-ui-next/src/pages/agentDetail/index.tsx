import { Fragment, useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import {
  AlertTriangle,
  ArrowLeft,
  Bot,
  CheckCircle2,
  Clock,
  ExternalLink,
  FilePenLine,
  Globe,
  Layers3,
  Pencil,
  Plus,
  Power,
  PowerOff,
  RefreshCw,
  Send,
  Server,
  ShieldAlert,
  Trash2,
} from 'lucide-react';
import { toast } from 'sonner';
import { agentApi } from '@/api/agent';
import { AiResourceStatusControls } from '@/components/ai/AiResourceStatusControls';
import { DetailTagChip } from '@/components/ai/DetailTagChip';
import {
  VersionLifecycleActionBar,
  VersionLifecycleActionDivider,
} from '@/components/ai/VersionLifecycleActionBar';
import { VisibilityAuthorizationDialog } from '@/components/ai/VisibilityAuthorizationDialog';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Textarea } from '@/components/ui/textarea';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useAgentStore } from '@/stores/agent-store';
import { useAuthStore } from '@/stores/auth-store';
import { useNamespaceStore } from '@/stores/namespace-store';
import type {
  AgentVersionActionData,
  AgentVersionStatus,
  AgentVersionSummary,
} from '@/types/agent';
import { parsePipelineInfo } from '@/types/pipeline';
import {
  type AgentVersionAction,
  buildAgentStatusUpdateData,
  endpointSourceOrderLabelKey,
  formatProtocolLabel,
  getProtocols,
  getVersionActions,
  namingDetailPath,
  runtimeCacheKey,
  usesRuntimeSource,
} from '../newAgent/agent-console-model';

type LifecycleAction = 'submit' | 'publish' | 'forcePublish' | 'redraft' | 'online' | 'offline';

const VERSION_STATUSES: AgentVersionStatus[] = [
  'draft',
  'reviewing',
  'reviewed',
  'online',
  'offline',
];

function formatTime(value?: number): string {
  return value ? new Date(value).toLocaleString() : '-';
}

const ACTION_LABEL_KEYS: Record<AgentVersionAction, string> = {
  submit: 'agent.actionSubmit',
  publish: 'agent.actionPublish',
  forcePublish: 'agent.actionForcePublish',
  redraft: 'agent.actionRedraft',
  online: 'agent.actionOnline',
  offline: 'agent.actionOffline',
  editDraft: 'agent.actionEditDraft',
  deleteDraft: 'agent.actionDeleteDraft',
};

const STATUS_LABEL_KEYS: Record<AgentVersionStatus, string> = {
  draft: 'agent.statusDraft',
  reviewing: 'agent.statusReviewing',
  reviewed: 'agent.statusReviewed',
  online: 'agent.statusOnline',
  offline: 'agent.statusOffline',
};

function actionLabel(t: TFunction, action: AgentVersionAction): string {
  return t(ACTION_LABEL_KEYS[action]);
}

function versionStatusLabel(t: TFunction, status: AgentVersionStatus): string {
  return t(STATUS_LABEL_KEYS[status]);
}

function ActionIcon({ action }: { action: AgentVersionAction }) {
  switch (action) {
    case 'editDraft':
    case 'redraft':
      return <Pencil className="h-3.5 w-3.5" />;
    case 'submit':
      return <Send className="h-3.5 w-3.5" />;
    case 'publish':
      return <CheckCircle2 className="h-3.5 w-3.5" />;
    case 'forcePublish':
      return <ShieldAlert className="h-3.5 w-3.5" />;
    case 'online':
      return <Power className="h-3.5 w-3.5" />;
    case 'offline':
      return <PowerOff className="h-3.5 w-3.5" />;
    case 'deleteDraft':
      return <Trash2 className="h-3.5 w-3.5" />;
  }
}

export default function AgentDetailPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { currentNamespace } = useNamespaceStore();
  const { globalAdmin, username } = useAuthStore();
  const namespaceId = searchParams.get('namespaceId') || currentNamespace || 'public';
  const agentName = searchParams.get('name') || '';
  const requestedVersion = searchParams.get('version') || '';
  const {
    currentOverview,
    currentVersion,
    versionPage,
    runtimeCache,
    detailLoading,
    runtimeLoading,
    error,
    fetchOverview,
    fetchVersionPage,
    fetchVersion,
    fetchRuntime,
    clearDetail,
  } = useAgentStore();
  const [selectedVersion, setSelectedVersion] = useState(requestedVersion);
  const [selectedProtocol, setSelectedProtocol] = useState('');
  const [versionStatus, setVersionStatus] = useState<AgentVersionStatus | 'ALL'>('ALL');
  const [versionPageNo, setVersionPageNo] = useState(1);
  const [actionLoading, setActionLoading] = useState(false);
  const [enableToggling, setEnableToggling] = useState(false);
  const [visibilityDialogOpen, setVisibilityDialogOpen] = useState(false);
  const [labelsText, setLabelsText] = useState('{}');

  const loadOverview = useCallback(async () => {
    if (!agentName) {
      return null;
    }
    const overview = await fetchOverview(namespaceId, agentName);
    if (overview) {
      const labels = { ...(overview.agent.versionInfo?.labels || {}) };
      delete labels.latest;
      setLabelsText(JSON.stringify(labels, null, 2));
      const fallback = overview.agent.versionCatalog?.latestVersion
        || overview.agent.versionInfo?.editingVersion
        || overview.agent.versionInfo?.reviewingVersion
        || overview.versionPage.pageItems[0]?.version
        || '';
      setSelectedVersion((current) => current || fallback);
    }
    return overview;
  }, [agentName, fetchOverview, namespaceId]);

  useEffect(() => {
    loadOverview();
    return clearDetail;
  }, [clearDetail, loadOverview]);

  useEffect(() => {
    if (agentName) {
      fetchVersionPage(
        namespaceId,
        agentName,
        versionStatus === 'ALL' ? undefined : versionStatus,
        versionPageNo,
        20,
      );
    }
  }, [agentName, namespaceId, versionStatus, versionPageNo, fetchVersionPage]);

  useEffect(() => {
    if (agentName && selectedVersion) {
      fetchVersion(namespaceId, agentName, selectedVersion);
    }
  }, [agentName, namespaceId, selectedVersion, fetchVersion]);

  const protocols = useMemo(
    () => getProtocols(currentVersion?.callInterfaces || []),
    [currentVersion],
  );

  useEffect(() => {
    setSelectedProtocol((current) => protocols.includes(current) ? current : protocols[0] || '');
  }, [protocols]);

  useEffect(() => {
    if (currentVersion && selectedProtocol) {
      fetchRuntime(
        namespaceId,
        agentName,
        currentVersion.version,
        selectedProtocol,
      );
    }
  }, [currentVersion, selectedProtocol, namespaceId, agentName, fetchRuntime]);

  const selectedInterface = currentVersion?.callInterfaces.find(
    (item) => item.protocol === selectedProtocol,
  );
  const runtimeView = currentVersion && selectedProtocol
    ? runtimeCache[runtimeCacheKey(currentVersion.version, selectedProtocol)]
    : undefined;

  const editPath = (mode: string, version?: string, basedOnVersion?: string) => {
    const params = new URLSearchParams({ namespaceId, name: agentName, mode });
    if (version) {
      params.set('version', version);
    }
    if (basedOnVersion) {
      params.set('basedOnVersion', basedOnVersion);
    }
    navigate(`/newAgent?${params.toString()}`);
  };

  const runLifecycleAction = async (action: LifecycleAction) => {
    if (!currentVersion) {
      return;
    }
    const identity: AgentVersionActionData = {
      namespaceId,
      agentName,
      version: currentVersion.version,
    };
    setActionLoading(true);
    try {
      switch (action) {
        case 'submit':
          await agentApi.submit(identity);
          break;
        case 'publish':
          await agentApi.publish(identity);
          break;
        case 'forcePublish':
          await agentApi.forcePublish(identity);
          break;
        case 'redraft':
          await agentApi.redraft(identity);
          break;
        case 'online':
          await agentApi.online(identity);
          break;
        case 'offline':
          await agentApi.offline(identity);
          break;
      }
      toast.success(t('agent.actionSuccess'));
      await loadOverview();
      await fetchVersionPage(
        namespaceId,
        agentName,
        versionStatus === 'ALL' ? undefined : versionStatus,
        versionPageNo,
        20,
      );
      await fetchVersion(namespaceId, agentName, currentVersion.version);
    } finally {
      setActionLoading(false);
    }
  };

  const deleteDraft = async () => {
    if (!currentVersion) {
      return;
    }
    setActionLoading(true);
    try {
      await agentApi.deleteDraft({
        namespaceId,
        agentName,
        version: currentVersion.version,
      });
      toast.success(t('agent.deleteDraftSuccess'));
      setSelectedVersion('');
      await loadOverview();
    } finally {
      setActionLoading(false);
    }
  };

  const updateLabels = async () => {
    try {
      const parsed = JSON.parse(labelsText);
      if (parsed === null || Array.isArray(parsed) || typeof parsed !== 'object') {
        throw new Error(t('agent.labelsObjectRequired'));
      }
      if ('latest' in parsed) {
        throw new Error(t('agent.latestLabelManaged'));
      }
      await agentApi.updateLabels({
        namespaceId,
        agentName,
        labels: JSON.stringify(parsed),
      });
      toast.success(t('agent.updateSuccess'));
      await loadOverview();
    } catch (error) {
      if (error instanceof SyntaxError || (error instanceof Error && !('response' in error))) {
        toast.error(error instanceof SyntaxError ? t('agent.jsonFormatError') : error.message);
      }
    }
  };

  const toggleAgentEnable = async (enabled: boolean) => {
    const agent = currentOverview?.agent;
    if (!agent || agent.status === (enabled ? 'enable' : 'disable')) {
      return;
    }
    setEnableToggling(true);
    try {
      await agentApi.updateAgent(buildAgentStatusUpdateData(namespaceId, agent, enabled));
      toast.success(t('agent.updateSuccess'));
      await loadOverview();
    } finally {
      setEnableToggling(false);
    }
  };

  if (detailLoading && !currentOverview) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-32 w-full" />
        <Skeleton className="h-96 w-full" />
      </div>
    );
  }

  if (!currentOverview) {
    return (
      <div className="flex flex-col items-center justify-center py-24 gap-3">
        <p className="text-sm text-destructive">{error || t('agent.loadFailed')}</p>
        <Button variant="outline" onClick={() => navigate('/agentManagement')}>
          {t('agent.backToList')}
        </Button>
      </div>
    );
  }

  const agent = currentOverview.agent;
  const currentPipelineInfo = parsePipelineInfo(currentVersion?.publishPipelineInfo);
  const actions = currentVersion
    ? getVersionActions(currentVersion.status, currentPipelineInfo, globalAdmin)
    : [];
  const canManageVisibility = globalAdmin || Boolean(username && agent.owner === username);
  const onlineVersionCount = agent.versionCatalog?.onlineVersions?.length
    ?? agent.versionInfo?.onlineCnt
    ?? 0;
  const latestVersion = agent.versionCatalog?.latestVersion;
  const canCreateDraftFrom = currentVersion?.status === 'online'
    || currentVersion?.status === 'offline';
  const hasUnpublishedVersion = Boolean(
    agent.versionInfo?.editingVersion || agent.versionInfo?.reviewingVersion,
  );

  return (
    <div className="space-y-5">
      <div className="relative overflow-hidden rounded-xl border bg-card">
        <div className="absolute inset-0 bg-gradient-to-br from-violet-500/[0.04] via-transparent to-fuchsia-500/[0.03]" />
        <div className="relative p-5">
          <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <Button
              variant="ghost"
              size="sm"
              className="-ml-2 h-7 w-fit text-muted-foreground hover:text-foreground"
              onClick={() => navigate('/agentManagement')}
            >
              <ArrowLeft className="mr-1.5 h-3.5 w-3.5" />
              {t('agent.backToList')}
            </Button>
            <div className="flex flex-wrap items-center gap-2">
              <Select value={selectedVersion} onValueChange={setSelectedVersion}>
                <SelectTrigger className="h-7 w-[160px] bg-background/80 text-xs">
                  <SelectValue placeholder={t('agent.selectVersion')} />
                </SelectTrigger>
                <SelectContent>
                  {(versionPage?.pageItems || []).map((version) => {
                    const pipelineInfo = parsePipelineInfo(version.publishPipelineInfo);
                    const pendingPublish = (version.status === 'reviewed'
                      && pipelineInfo?.status !== 'REJECTED')
                      || (version.status === 'reviewing'
                        && pipelineInfo?.status === 'APPROVED');
                    const rejected = version.status === 'reviewed'
                      && pipelineInfo?.status === 'REJECTED';
                    return (
                      <SelectItem key={version.version} value={version.version}>
                        <span className="flex items-center gap-2">
                          <span>{version.version}</span>
                          {latestVersion === version.version && (
                            <Badge className="border-0 bg-emerald-100 px-1 py-0 text-[10px] text-emerald-700 dark:bg-emerald-950/50 dark:text-emerald-300">
                              {t('agent.latestBadge')}
                            </Badge>
                          )}
                          {version.status === 'draft' && (
                            <Badge className="border-0 bg-amber-100 px-1 py-0 text-[10px] text-amber-700 dark:bg-amber-950/50 dark:text-amber-300">
                              {t('agent.statusDraft')}
                            </Badge>
                          )}
                          {rejected && (
                            <Badge className="border-0 bg-red-100 px-1 py-0 text-[10px] text-red-700 dark:bg-red-950/50 dark:text-red-300">
                              {t('agent.statusRejected')}
                            </Badge>
                          )}
                          {!rejected
                            && (version.status === 'reviewing'
                              || version.status === 'reviewed') && (
                            <Badge className={pendingPublish
                              ? 'border-0 bg-teal-100 px-1 py-0 text-[10px] text-teal-700 dark:bg-teal-950/50 dark:text-teal-300'
                              : 'border-0 bg-blue-100 px-1 py-0 text-[10px] text-blue-700 dark:bg-blue-950/50 dark:text-blue-300'}
                            >
                              {t(pendingPublish
                                ? 'agent.statusPendingPublish'
                                : 'agent.statusReviewing')}
                            </Badge>
                          )}
                        </span>
                      </SelectItem>
                    );
                  })}
                </SelectContent>
              </Select>
              <Button
                variant="outline"
                size="sm"
                className="h-7 gap-1.5 text-xs"
                onClick={() => editPath('metadata')}
              >
                <Pencil className="mr-1.5 h-3.5 w-3.5" />
                {t('agent.editMetadata')}
              </Button>
              {currentOverview?.versionPage.totalCount === 0 && (
                <Button
                  size="sm"
                  className="h-7 gap-1.5 text-xs"
                  onClick={() => editPath('draft-create')}
                >
                  <FilePenLine className="mr-1.5 h-3.5 w-3.5" />
                  {t('agent.createDraft')}
                </Button>
              )}
            </div>
          </div>
          <div className="flex items-start gap-4">
            <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-violet-500 to-fuchsia-400 shadow-lg shadow-violet-500/20">
              <Bot className="h-7 w-7 text-white" />
            </div>
            <div className="min-w-0 flex-1">
              <div className="mb-1 flex items-center gap-2.5">
                <h1 className="text-xl font-bold tracking-tight">
                  {agent.displayName || agent.agentName}
                </h1>
                {currentVersion && (
                  <span className="rounded bg-muted/60 px-1.5 py-0.5 font-mono text-xs text-muted-foreground">
                    {currentVersion.version}
                  </span>
                )}
              </div>
              <AiResourceStatusControls
                enabled={agent.status === 'enable'}
                scope={agent.scope}
                enabledLabel={t('agent.enabled')}
                disabledLabel={t('agent.disabled')}
                publicLabel={t('agent.publicScope')}
                privateLabel={t('agent.privateScope')}
                enableDisabled={enableToggling}
                scopeDisabled
                onEnabledChange={toggleAgentEnable}
                visibilityLabel={canManageVisibility
                  ? t('common.visibilityAuthorization.entry')
                  : undefined}
                visibilityTooltip={t('common.visibilityAuthorization.title')}
                onVisibilityClick={canManageVisibility
                  ? () => setVisibilityDialogOpen(true)
                  : undefined}
              />
              <p className="mt-1 font-mono text-xs text-muted-foreground">{agent.agentName}</p>
              <p className="mt-2 max-w-2xl text-sm leading-relaxed text-muted-foreground">
                {agent.description || '-'}
              </p>
              <div className="mt-2 flex flex-wrap items-center gap-4 text-xs text-muted-foreground">
                <span className="inline-flex items-center gap-1">
                  <Globe className="h-3 w-3" />
                  {t('agent.onlineVersions')}: {onlineVersionCount}
                </span>
                {!!agent.updateTime && agent.updateTime > 0 && (
                  <span className="inline-flex items-center gap-1">
                    <Clock className="h-3 w-3" />
                    {formatTime(agent.updateTime)}
                  </span>
                )}
                {currentVersion?.changeDescription && (
                  <span className="inline-flex items-center gap-1">
                    <FilePenLine className="h-3 w-3" />
                    {currentVersion.changeDescription}
                  </span>
                )}
              </div>
              {currentVersion && (actions.length > 0 || canCreateDraftFrom) && (
                <VersionLifecycleActionBar>
                  {actions.map((action) => (
                    <Fragment key={action}>
                      {currentVersion.status === 'draft' && action === 'submit' && (
                        <VersionLifecycleActionDivider />
                      )}
                      <Button
                        size="sm"
                        variant={action === 'submit'
                          || action === 'publish'
                          || action === 'online'
                          ? 'default'
                          : 'outline'}
                        className={action === 'forcePublish'
                          ? 'h-7 gap-1.5 border-destructive/40 text-xs text-destructive hover:bg-destructive/10 hover:text-destructive'
                          : action === 'deleteDraft'
                            ? 'h-7 gap-1.5 text-xs text-destructive hover:bg-destructive/10 hover:text-destructive'
                            : 'h-7 gap-1.5 text-xs'}
                        disabled={actionLoading || (action === 'publish'
                          && !!currentPipelineInfo
                          && currentPipelineInfo.status !== 'APPROVED')}
                        onClick={() => {
                          if (action === 'editDraft') {
                            editPath('draft-edit', currentVersion.version);
                          } else if (action === 'deleteDraft') {
                            deleteDraft();
                          } else {
                            runLifecycleAction(action);
                          }
                        }}
                      >
                        <ActionIcon action={action} />
                        {actionLabel(t, action)}
                      </Button>
                    </Fragment>
                  ))}
                  {canCreateDraftFrom && (
                    <Button
                      variant="outline"
                      size="sm"
                      className="h-7 gap-1.5 text-xs"
                      disabled={actionLoading || hasUnpublishedVersion}
                      title={hasUnpublishedVersion ? t('agent.draftExistsTip') : undefined}
                      onClick={() => editPath(
                        'draft-create',
                        undefined,
                        currentVersion.version,
                      )}
                    >
                      <Plus className="h-3.5 w-3.5" />
                      {t('agent.createDraftFrom')}
                    </Button>
                  )}
                </VersionLifecycleActionBar>
              )}
            </div>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-3 gap-5">
        <div className="xl:col-span-2 space-y-5">
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
                              value={selectedInterface.descriptorMediaType}
                            />
                            <Info
                              label={t('agent.sourceOrder')}
                              value={t(endpointSourceOrderLabelKey(
                                selectedInterface.endpointSourceOrder,
                              ))}
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
                                  count: selectedInterface.declaredEndpoints?.length || 0,
                                })}
                              </span>
                            </div>
                            {(selectedInterface.declaredEndpoints || []).length === 0 ? (
                              <div className="rounded-lg border border-dashed p-3 text-xs text-muted-foreground">
                                {t('agent.noDeclaredEndpoints')}
                              </div>
                            ) : (
                              <div className="space-y-2">
                                {selectedInterface.declaredEndpoints?.map((endpoint) => (
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

          {currentVersion && selectedProtocol && (
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
                  onClick={() => fetchRuntime(
                    namespaceId,
                    agentName,
                    currentVersion.version,
                    selectedProtocol,
                    true,
                  )}
                >
                  <RefreshCw className="mr-1.5 h-3.5 w-3.5" />
                  {t('common.refresh')}
                </Button>
              </div>
              <CardContent className="p-5 space-y-4">
                {!usesRuntimeSource(selectedInterface) && (
                  <div className="flex gap-2 rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm text-amber-800">
                    <AlertTriangle className="h-4 w-4 shrink-0 mt-0.5" />
                    {t('agent.runtimeSourceDisabled')}
                  </div>
                )}
                {runtimeLoading && !runtimeView ? (
                  <Skeleton className="h-24 w-full" />
                ) : (runtimeView?.runtimeEndpointSnapshot.items || []).length === 0 ? (
                  <p className="text-sm text-muted-foreground">{t('agent.noRuntimeEndpoints')}</p>
                ) : (
                  <div className="space-y-2">
                    {runtimeView?.runtimeEndpointSnapshot.items.map((item) => (
                      <div key={`${item.endpoint.uri}@@${item.endpoint.transport}`} className="rounded-lg border p-3">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="font-mono text-sm break-all">{item.endpoint.uri}</span>
                          <Badge>{item.state}</Badge>
                          <Badge variant="outline">{item.endpoint.transport}</Badge>
                        </div>
                        <p className="text-xs text-muted-foreground mt-2">
                          {item.bindings.map(
                            (binding) => `${binding.runtimeVersion} → ${binding.versionRange}`,
                          ).join(', ')}
                        </p>
                        <p className="text-xs text-muted-foreground mt-1">
                          {formatTime(item.lastUpdatedTime)}
                        </p>
                      </div>
                    ))}
                  </div>
                )}
                {runtimeView?.namingServiceRef && (
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => navigate(namingDetailPath(runtimeView.namingServiceRef))}
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
            </Card>
          )}
        </div>

        <div className="space-y-5">
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
                value={agent.versionCatalog?.latestVersion || '-'}
              />
              <Info
                label={t('agent.onlineVersions')}
                value={String(agent.versionInfo?.onlineCnt || 0)}
              />
            </CardContent>
          </Card>

          <Card className="py-0 gap-0 overflow-hidden">
            <div className="px-5 py-3.5 border-b bg-muted/30">
              <h2 className="text-sm font-semibold">{t('agent.customLabels')}</h2>
            </div>
            <CardContent className="p-5 space-y-3">
              <Textarea
                value={labelsText}
                rows={5}
                className="font-mono text-xs"
                onChange={(event) => setLabelsText(event.target.value)}
              />
              <Button size="sm" onClick={updateLabels}>{t('common.save')}</Button>
              <p className="text-xs text-muted-foreground">{t('agent.latestLabelManaged')}</p>
            </CardContent>
          </Card>

          <Card className="py-0 gap-0 overflow-hidden">
            <div className="px-5 py-3.5 border-b bg-muted/30">
              <h2 className="text-sm font-semibold">{t('agent.versionHistory')}</h2>
            </div>
            <CardContent className="p-5 space-y-3">
              <Select
                value={versionStatus}
                onValueChange={(value) => {
                  setVersionStatus(value as AgentVersionStatus | 'ALL');
                  setVersionPageNo(1);
                }}
              >
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">{t('common.all')}</SelectItem>
                  {VERSION_STATUSES.map((status) => (
                    <SelectItem key={status} value={status}>
                      {versionStatusLabel(t, status)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {(versionPage?.pageItems || []).map((version: AgentVersionSummary) => (
                <button
                  key={version.version}
                  className="w-full rounded-lg border p-3 text-left hover:bg-muted/40"
                  onClick={() => setSelectedVersion(version.version)}
                >
                  <div className="flex justify-between gap-2">
                    <span className="font-mono text-sm">{version.version}</span>
                    <Badge variant="outline">
                      {versionStatusLabel(t, version.status)}
                    </Badge>
                  </div>
                  <p className="text-xs text-muted-foreground mt-1">
                    {version.changeDescription || '-'}
                  </p>
                </button>
              ))}
              {(versionPage?.pagesAvailable || 0) > 1 && (
                <div className="flex items-center justify-between">
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={versionPageNo <= 1}
                    onClick={() => setVersionPageNo((value) => value - 1)}
                  >
                    {t('common.previous')}
                  </Button>
                  <span className="text-xs">{versionPageNo}/{versionPage?.pagesAvailable}</span>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={versionPageNo >= (versionPage?.pagesAvailable || 1)}
                    onClick={() => setVersionPageNo((value) => value + 1)}
                  >
                    {t('common.next')}
                  </Button>
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      </div>
      <VisibilityAuthorizationDialog
        open={visibilityDialogOpen}
        namespaceId={namespaceId}
        resourceType="agent"
        resourceName={agentName}
        onOpenChange={setVisibilityDialogOpen}
        onSuccess={async () => {
          await loadOverview();
        }}
      />
    </div>
  );
}

function Info({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="text-sm break-words">{value}</p>
    </div>
  );
}
