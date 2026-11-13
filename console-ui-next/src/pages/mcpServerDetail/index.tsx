import { useResourceLifecycleConfirmation } from '@/components/ai/hub/ResourceLifecycleConfirmation';
import { McpGovernanceInfo } from '@/components/ai/mcp/McpGovernanceInfo';
import { ResourceHubActions } from '@/components/ai/hub/ResourceHubActions';
import { Fragment, useEffect, useCallback, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import {
  ArrowLeft,
  Pencil,
  Plus,
  Cpu,
  History,
  AlertTriangle,
  CheckCircle2,
  Loader2,
  Power,
  PowerOff,
  Send,
  ShieldAlert,
  Tags,
  Trash2,
} from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { Textarea } from '@/components/ui/textarea';
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
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { VisibilityAuthorizationDialog } from '@/components/ai/VisibilityAuthorizationDialog';
import { AiResourceStatusControls } from '@/components/ai/AiResourceStatusControls';
import { AiVersionSelectOption } from '@/components/ai/AiVersionSelectOption';
import { CreateDraftFromVersionButton } from '@/components/ai/CreateDraftFromVersionButton';
import {
  VersionLifecycleActionBar,
  VersionLifecycleActionDivider,
} from '@/components/ai/VersionLifecycleActionBar';
import { useMcpStore } from '@/stores/mcp-store';
import { useNamespaceStore } from '@/stores/namespace-store';
import { useAuthStore } from '@/stores/auth-store';
import { cn } from '@/lib/utils';
import { mcpApi } from '@/api/mcp';
import { parsePipelineInfo } from '@/types/pipeline';
import type {
  McpServerVersionDetail,
  McpServerVersionSummary,
} from '@/types/mcp';
import { PipelineStatusDisplay } from '@/pages/skillManagement/components/PipelineStatusDisplay';
import { McpDetailContent, PROTOCOL_STYLES } from '@/components/ai/mcp/McpDetailContent';
import {
  getMcpVersionActions,
  isMcpLifecycleUnavailable,
  type McpVersionAction,
} from './mcp-lifecycle';



function LifecycleActionIcon({ action }: { action: McpVersionAction }) {
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

export default function McpServerDetailPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { currentNamespace } = useNamespaceStore();
  const { globalAdmin, username } = useAuthStore();

  const mcpName = searchParams.get('mcpName') || '';
  const namespaceId = searchParams.get('namespaceId') || currentNamespace || 'public';
  const requestedVersion = searchParams.get('version') || '';

  const {
    currentMcp,
    detailLoading,
    selectedVersion,
    error,
    fetchMcpDetail,
    setSelectedVersion,
    clearError,
  } = useMcpStore();

  const [versionSheetOpen, setVersionSheetOpen] = useState(false);
  const hubLifecycle = useResourceLifecycleConfirmation('mcp', namespaceId, mcpName, selectedVersion);
  const [versionSummaries, setVersionSummaries] = useState<McpServerVersionSummary[]>([]);
  const [lifecycleDetail, setLifecycleDetail] = useState<McpServerVersionDetail | null>(null);
  const [lifecycleAvailable, setLifecycleAvailable] = useState<boolean | null>(null);
  const [lifecycleSyncing, setLifecycleSyncing] = useState(false);
  const [lifecycleMessage, setLifecycleMessage] = useState('');
  const [actionLoading, setActionLoading] = useState(false);
  const [confirmAction, setConfirmAction] = useState<McpVersionAction | null>(null);
  const [labelsText, setLabelsText] = useState('{}');
  const [labelsLoading, setLabelsLoading] = useState(false);
  const [visibilityOpen, setVisibilityOpen] = useState(false);
  const [enableToggling, setEnableToggling] = useState(false);
  const [scopeToggling, setScopeToggling] = useState(false);

  const loadExactVersion = useCallback(async (version: string) => {
    if (!mcpName || !version) return;
    await Promise.all([
      fetchMcpDetail(namespaceId, mcpName, version),
      mcpApi.getVersion({ namespaceId, mcpName, version }).then((response) => {
        setLifecycleDetail(response.data);
        const labels = { ...(response.data.labels || {}) };
        delete labels.latest;
        setLabelsText(JSON.stringify(labels, null, 2));
      }),
    ]);
  }, [fetchMcpDetail, namespaceId, mcpName]);

  const loadLifecycle = useCallback(async (preferredVersion?: string) => {
    if (!mcpName) return;
    try {
      const response = await mcpApi.listVersions({
        namespaceId,
        mcpName,
        pageNo: 1,
        pageSize: 200,
      });
      const items = response.data.pageItems || [];
      setVersionSummaries(items);
      setLifecycleAvailable(true);
      setLifecycleSyncing(false);
      setLifecycleMessage('');
      const selected = preferredVersion
        || requestedVersion
        || items.find((item) => item.latest)?.version
        || items[0]?.version
        || '';
      if (selected) {
        setSelectedVersion(selected);
        await loadExactVersion(selected);
      } else {
        setSelectedVersion('');
        setLifecycleDetail(null);
        await fetchMcpDetail(namespaceId, mcpName);
      }
    } catch (error) {
      const syncing = isMcpLifecycleUnavailable(error);
      setLifecycleDetail(null);
      setVersionSummaries([]);
      setLifecycleAvailable(false);
      setLifecycleSyncing(syncing);
      setLifecycleMessage(t(syncing
        ? 'mcp.lifecycleSyncingMessage'
        : 'mcp.lifecycleLoadFailed'));
      await fetchMcpDetail(
        namespaceId,
        mcpName,
        preferredVersion || requestedVersion || undefined,
      );
    }
  }, [fetchMcpDetail, loadExactVersion, mcpName, namespaceId, requestedVersion,
    setSelectedVersion, t]);

  useEffect(() => {
    loadLifecycle();
    return () => {
      clearError();
    };
  }, [clearError, loadLifecycle]);

  const handleVersionChange = async (version: string) => {
    setSelectedVersion(version);
    if (lifecycleAvailable) {
      await loadExactVersion(version);
    } else {
      await fetchMcpDetail(namespaceId, mcpName, version);
    }
    setVersionSheetOpen(false);
  };

  const handleEdit = () => {
    if (!selectedVersion) return;
    const params = new URLSearchParams({
      mode: 'draft-edit',
      mcpName,
      namespaceId,
      version: selectedVersion,
    });
    navigate(`/newMcpServer?${params}`);
  };

  const handleNewVersion = () => {
    const params = new URLSearchParams({ mode: 'draft-create', mcpName, namespaceId });
    if (selectedVersion) params.set('version', selectedVersion);
    navigate(`/newMcpServer?${params}`);
  };

  const runLifecycleAction = async (action: McpVersionAction) => {
    if (!selectedVersion || !lifecycleAvailable) return;
    if (action === 'editDraft') {
      handleEdit();
      return;
    }
    const identity = { namespaceId, mcpName, version: selectedVersion };
    setActionLoading(true);
    try {
      switch (action) {
        case 'submit':
          await mcpApi.submit(identity);
          break;
        case 'publish':
          await mcpApi.publish(identity);
          break;
        case 'forcePublish':
          await mcpApi.forcePublish(identity);
          break;
        case 'redraft':
          await mcpApi.redraft(identity);
          break;
        case 'online':
          await mcpApi.online(identity);
          break;
        case 'offline':
          await mcpApi.offline(identity);
          break;
        case 'deleteDraft':
        {
          await mcpApi.deleteDraft(identity);
          toast.success(t('mcp.deleteDraftSuccess'));
          setConfirmAction(null);
          const remaining = versionSummaries.filter((item) => item.version !== selectedVersion);
          setVersionSummaries(remaining);
          if (remaining.length === 0) {
            setSelectedVersion('');
            setLifecycleDetail(null);
            await fetchMcpDetail(namespaceId, mcpName);
          } else {
            await loadLifecycle(remaining[0].version);
          }
          return;
        }
      }
      toast.success(t('mcp.lifecycleActionSuccess'));
      setConfirmAction(null);
      await loadLifecycle(selectedVersion);
    } finally {
      setActionLoading(false);
    }
  };

  const handleLifecycleAction = (action: McpVersionAction) => {
    if (action === 'forcePublish' || action === 'deleteDraft' || action === 'offline'
      || action === 'redraft') {
      setConfirmAction(action);
      return;
    }
    runLifecycleAction(action);
  };

  const updateLabels = async () => {
    setLabelsLoading(true);
    try {
      const labels = JSON.parse(labelsText) as unknown;
      if (!labels || Array.isArray(labels) || typeof labels !== 'object') {
        throw new Error(t('mcp.labelsObjectRequired'));
      }
      if ('latest' in labels) {
        throw new Error(t('mcp.latestLabelManaged'));
      }
      await mcpApi.updateLabels({
        namespaceId,
        mcpName,
        labels: JSON.stringify(labels),
      });
      toast.success(t('mcp.labelsUpdateSuccess'));
      if (selectedVersion) await loadExactVersion(selectedVersion);
    } catch (error) {
      if (error instanceof SyntaxError) {
        toast.error(t('mcp.invalidJson'));
      } else if (error instanceof Error && !('response' in error)) {
        toast.error(error.message);
      }
    } finally {
      setLabelsLoading(false);
    }
  };

  const handleToggleEnable = async (enabled: boolean) => {
    if (!enabled && !await hubLifecycle.confirmLifecycle('disable')) return;
    setEnableToggling(true);
    try {
      await mcpApi.updateStatus({ namespaceId, mcpName, enabled });
      toast.success(t(enabled ? 'mcp.enableSuccess' : 'mcp.disableSuccess'));
      if (selectedVersion) await loadExactVersion(selectedVersion);
    } finally {
      setEnableToggling(false);
    }
  };

  const handleToggleScope = async (isPublic: boolean) => {
    if (!isPublic && !await hubLifecycle.confirmLifecycle('private')) return;
    setScopeToggling(true);
    try {
      await mcpApi.updateScope({
        namespaceId,
        mcpName,
        scope: isPublic ? 'PUBLIC' : 'PRIVATE',
      });
      toast.success(t('mcp.scopeUpdateSuccess'));
      if (selectedVersion) await loadExactVersion(selectedVersion);
    } finally {
      setScopeToggling(false);
    }
  };

  // Loading skeleton
  if (detailLoading && !currentMcp) {
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
  if (error && !currentMcp) {
    return (
      <div className="flex flex-col items-center justify-center py-24 space-y-4">
        <p className="text-sm text-destructive">{t('mcp.loadFailed')}</p>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => navigate('/mcpServerManagement')}>
            {t('mcp.backToList')}
          </Button>
          <Button onClick={() => loadLifecycle()}>
            {t('mcp.retry')}
          </Button>
        </div>
      </div>
    );
  }

  if (!currentMcp) return null;

  const mcp = currentMcp;
  const protocolLabel = mcp.frontProtocol || mcp.protocol || 'unknown';
  const legacyVersions = mcp.allVersions || [];
  const versionOptions: McpServerVersionSummary[] = lifecycleAvailable
    ? versionSummaries
    : legacyVersions.map((item) => ({
        version: item.version,
        status: 'online' as const,
        latest: item.is_latest,
      }));
  const protocolStyle = PROTOCOL_STYLES[protocolLabel];
  const currentStatus = lifecycleDetail?.status;
  const currentPipelineInfo = parsePipelineInfo(lifecycleDetail?.publishPipelineInfo);
  const lifecycleActions = currentStatus
    ? getMcpVersionActions(currentStatus, currentPipelineInfo, globalAdmin)
    : [];
  const canWriteResource = lifecycleDetail?.writable ?? false;
  const canManageVisibility = globalAdmin || lifecycleDetail?.owner === username;
  const resourceEnabled = lifecycleDetail
    ? lifecycleDetail.resourceStatus !== 'disable'
    : mcp.enabled;
  const actionLabel = (action: McpVersionAction) => {
    if (action === 'submit'
      && (currentStatus === 'reviewed' || currentPipelineInfo?.status === 'REJECTED')) {
      return t('mcp.lifecycleAction.resubmit');
    }
    return t(`mcp.lifecycleAction.${action}`);
  };
  const renderLifecycleAction = (action: McpVersionAction) => {
    const primaryAction = action === 'submit' || action === 'publish' || action === 'online';
    const destructiveAction = action === 'forcePublish' || action === 'deleteDraft';
    const publishBlocked = action === 'publish'
      && !!currentPipelineInfo
      && currentPipelineInfo.status !== 'APPROVED';
    return (
      <Button
        key={action}
        size="sm"
        variant={primaryAction ? 'default' : 'outline'}
        className={cn(
          'h-7 gap-1.5 text-xs',
          destructiveAction
            && 'text-destructive hover:bg-destructive/10 hover:text-destructive',
          action === 'forcePublish' && 'border-destructive/40',
        )}
        disabled={actionLoading || publishBlocked}
        onClick={() => handleLifecycleAction(action)}
      >
        {actionLoading
          ? <Loader2 className="h-3 w-3 animate-spin" />
          : <LifecycleActionIcon action={action} />}
        {actionLabel(action)}
      </Button>
    );
  };

  return (
    <div className="space-y-5">
      {/* ===== Hero Header ===== */}
      <div className="relative rounded-xl border bg-card overflow-hidden">
        {/* Decorative gradient background */}
        <div className="absolute inset-0 bg-gradient-to-br from-primary/[0.04] via-transparent to-blue-500/[0.03]" />
        <div className="absolute top-0 right-0 w-64 h-64 bg-gradient-to-bl from-primary/[0.06] to-transparent rounded-full -translate-y-1/2 translate-x-1/3" />

        <div className="relative px-5 py-4">
          {/* Top bar: back + actions */}
          <div className="flex items-center justify-between mb-4">
            <Button
              variant="ghost"
              size="sm"
              className="h-7 gap-1.5 text-muted-foreground hover:text-foreground -ml-2"
              onClick={() => navigate('/mcpServerManagement')}
            >
              <ArrowLeft className="h-3.5 w-3.5" />
              {t('mcp.backToList')}
            </Button>

            <div className="flex items-center gap-2">
              {/* Version selector */}
              {versionOptions.length > 0 && (
                <Select
                  value={selectedVersion || mcp.versionDetail?.version || ''}
                  onValueChange={handleVersionChange}
                >
                  <SelectTrigger className="w-[140px] h-7 text-xs bg-background/80">
                    <SelectValue placeholder={t('mcp.selectVersion')} />
                  </SelectTrigger>
                  <SelectContent>
                    {versionOptions.map((v) => (
                      <SelectItem key={v.version} value={v.version}>
                        <AiVersionSelectOption
                          version={v.version}
                          status={v.status}
                          latest={v.latest}
                          publishPipelineInfo={v.publishPipelineInfo}
                          labels={{
                            latest: t('mcp.latestVersion'),
                            draft: t('mcp.versionStatus.draft'),
                            reviewing: t('mcp.versionStatus.reviewing'),
                            pendingPublish: t('mcp.versionStatus.pendingPublish'),
                            rejected: t('mcp.versionStatus.rejected'),
                          }}
                        />
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}

              <Button variant="outline" size="sm" className="h-7 text-xs" onClick={() => setVersionSheetOpen(true)}>
                <History className="mr-1 h-3 w-3" />
                {t('mcp.versionHistory')}
              </Button>
            </div>
          </div>

          {/* Server identity */}
          <div className="flex items-start gap-4">
            {/* Large icon */}
            <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-blue-500 to-cyan-400 shadow-lg shadow-blue-500/20">
              {mcp.icons?.[0]?.url ? (
                <img
                  src={mcp.icons[0].url}
                  alt={mcp.name}
                  className="h-8 w-8 object-contain"
                  onError={(e) => {
                    (e.target as HTMLImageElement).style.display = 'none';
                    (e.target as HTMLImageElement).nextElementSibling?.classList.remove('hidden');
                  }}
                />
              ) : null}
              <Cpu className={cn('h-7 w-7 text-white', mcp.icons?.[0]?.url && 'hidden')} />
            </div>

            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2.5 mb-1">
                <h1 className="text-xl font-bold tracking-tight">{mcp.name}</h1>
                {/* Protocol badge */}
                <span className={cn(
                  'inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium',
                  protocolStyle?.bg || 'bg-gray-100 dark:bg-gray-800',
                  protocolStyle?.text || 'text-gray-600 dark:text-gray-400'
                )}>
                  <span className={cn('h-1.5 w-1.5 rounded-full', protocolStyle?.dot || 'bg-gray-400')} />
                  {protocolLabel}
                </span>
                {/* Version */}
                {(mcp.versionDetail?.version || mcp.version) && (
                  <span className="text-xs text-muted-foreground font-mono bg-muted/60 px-1.5 py-0.5 rounded">
                    {mcp.versionDetail?.version || mcp.version}
                  </span>
                )}
              </div>
              <AiResourceStatusControls
                enabled={resourceEnabled}
                scope={lifecycleDetail?.scope}
                enabledLabel={t('mcp.enabled')}
                disabledLabel={t('mcp.disabled')}
                publicLabel={t('mcp.scopePublic')}
                privateLabel={t('mcp.scopePrivate')}
                enableDisabled={enableToggling || !canWriteResource}
                scopeDisabled={scopeToggling || !canWriteResource}
                onEnabledChange={handleToggleEnable}
                onScopeChange={handleToggleScope}
                visibilityLabel={canManageVisibility
                  ? t('common.visibilityAuthorization.entry')
                  : undefined}
                visibilityTooltip={t('common.visibilityAuthorization.title')}
                onVisibilityClick={canManageVisibility
                  ? () => setVisibilityOpen(true)
                  : undefined}
              />
              {mcp.description && (
                <p className="text-sm text-muted-foreground leading-relaxed max-w-2xl">
                  {mcp.description}
                </p>
              )}

              {/* Version lifecycle action buttons */}
              {lifecycleAvailable && canWriteResource
                && currentStatus && lifecycleActions.length > 0 && (
                <VersionLifecycleActionBar warning={!resourceEnabled ? (
                    <p className="flex items-center gap-1 text-[11px] text-amber-600 dark:text-amber-400 mb-2">
                      <AlertTriangle className="h-3 w-3 shrink-0" />
                      {t('mcp.serverDisabledWarning')}
                    </p>
                  ) : undefined}
                >
                  {lifecycleActions.filter((action) => action !== 'forcePublish').map((action) => (
                    <Fragment key={action}>
                      {currentStatus === 'draft' && action === 'submit' && (
                        <VersionLifecycleActionDivider />
                      )}
                      {renderLifecycleAction(action)}
                    </Fragment>
                  ))}
                  {currentPipelineInfo?.status === 'APPROVED'
                    && (currentStatus === 'reviewing' || currentStatus === 'reviewed') && (
                    <PipelineStatusDisplay
                      pipelineInfo={currentPipelineInfo}
                      compact
                    />
                  )}
                  {currentPipelineInfo?.status === 'REJECTED'
                    && (currentStatus === 'draft'
                      || lifecycleActions.includes('forcePublish')) && (
                    <PipelineStatusDisplay
                      pipelineInfo={currentPipelineInfo}
                      compact
                    />
                  )}
                  {lifecycleActions.includes('forcePublish')
                    && renderLifecycleAction('forcePublish')}
                  {currentStatus === 'online' && (() => {
                    const hasWorkingVersion = !!(
                      lifecycleDetail?.editingVersion || lifecycleDetail?.reviewingVersion
                    );
                    return (
                      <CreateDraftFromVersionButton
                        label={t('mcp.createDraftFrom')}
                        blocked={hasWorkingVersion}
                        blockedMessage={t('mcp.draftExistsTip')}
                        disabled={actionLoading}
                        divider
                        onClick={handleNewVersion}
                      />
                    );
                  })()}
                </VersionLifecycleActionBar>
              )}

              {lifecycleAvailable && versionOptions.length === 0 && (
                <VersionLifecycleActionBar>
                  <Button
                    size="sm"
                    className="h-7 text-xs gap-1.5"
                    disabled={actionLoading}
                    onClick={handleNewVersion}
                  >
                    <Plus className="h-3 w-3" />
                    {t('mcp.newVersion')}
                  </Button>
                </VersionLifecycleActionBar>
              )}
            </div>
          </div>
        </div>
      </div>

      {lifecycleAvailable === false && (
        <div className="flex gap-3 rounded-lg border border-destructive/30 bg-destructive/10 p-4 text-destructive">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
          <div className="space-y-1">
            <p className="text-sm font-semibold">
              {t(lifecycleSyncing
                ? 'mcp.lifecycleSyncingTitle'
                : 'mcp.lifecycleUnavailableTitle')}
            </p>
            <p className="text-sm">{lifecycleMessage}</p>
          </div>
        </div>
      )}

      <ResourceHubActions type="mcp" namespaceId={namespaceId} name={mcpName} version={selectedVersion || lifecycleDetail?.version} status={currentStatus} scope={lifecycleDetail?.scope} enabled={resourceEnabled} canWrite={canWriteResource} onShareStateChange={hubLifecycle.onShareStateChange} />
      {hubLifecycle.lifecycleDialog}

      <McpDetailContent mcp={mcp} namespaceId={namespaceId} loading={detailLoading} management={<>
          {lifecycleAvailable && lifecycleDetail && (
            <>
              <McpGovernanceInfo detail={lifecycleDetail} />

              <Card className="overflow-hidden py-0 gap-0">
                <div className="px-4 py-3 border-b bg-muted/30">
                  <h2 className="text-sm font-semibold flex items-center gap-2">
                    <Tags className="h-4 w-4 text-muted-foreground" />
                    {t('mcp.customLabels')}
                  </h2>
                </div>
                <CardContent className="p-4 space-y-3">
                  <Textarea
                    value={labelsText}
                    onChange={(event) => setLabelsText(event.target.value)}
                    disabled={!canWriteResource}
                    rows={5}
                    className="font-mono text-xs"
                    placeholder='{"stable":"1.0.0"}'
                  />
                  <Button
                    size="sm"
                    className="h-7 text-xs"
                    onClick={updateLabels}
                    disabled={labelsLoading || !canWriteResource}
                  >
                    {labelsLoading && <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" />}
                    {t('common.save')}
                  </Button>
                  <p className="text-[11px] text-muted-foreground">
                    {t('mcp.latestLabelManaged')}
                  </p>
                </CardContent>
              </Card>
            </>
          )}
      </>} />

      {/* ===== Version History Sheet ===== */}
      <Sheet open={versionSheetOpen} onOpenChange={setVersionSheetOpen}>
        <SheetContent className="flex flex-col p-0 sm:max-w-md">
          <SheetHeader className="px-6 pt-6 pb-4 border-b shrink-0">
            <SheetTitle className="flex items-center gap-2">
              <History className="h-4.5 w-4.5 text-blue-500" />
              {t('mcp.versionHistory')}
            </SheetTitle>
            <SheetDescription>
              {t('mcp.totalVersions', { count: versionOptions.length })}
            </SheetDescription>
          </SheetHeader>

          <ScrollArea className="flex-1">
            <div className="p-4 space-y-2">
              {versionOptions.length === 0 ? (
                <p className="text-sm text-muted-foreground text-center py-12">{t('mcp.noVersions', { defaultValue: '暂无版本历史' })}</p>
              ) : (
                versionOptions.map((v) => {
                  const isCurrent = v.version === (selectedVersion || mcp.versionDetail?.version);
                  return (
                    <div
                      key={v.version}
                      className={cn(
                        'rounded-lg border px-4 py-3 cursor-pointer transition-colors hover:bg-muted/50',
                        isCurrent && 'border-blue-500/50 bg-blue-50/50 dark:bg-blue-950/20'
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
                            <Badge variant="outline" className="text-[10px] px-1.5 py-0 border-blue-500/50 text-blue-600 dark:text-blue-400">
                              {t('mcp.currentVersion')}
                            </Badge>
                          )}
                          <Badge variant="secondary" className="text-[10px] px-1.5 py-0">
                            {t(`mcp.versionStatus.${v.status}`)}
                          </Badge>
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

      <VisibilityAuthorizationDialog
        open={visibilityOpen}
        onOpenChange={setVisibilityOpen}
        namespaceId={namespaceId}
        resourceType="mcp"
        resourceName={mcpName}
        onSuccess={() => selectedVersion ? loadExactVersion(selectedVersion) : undefined}
      />

      <Dialog open={confirmAction !== null} onOpenChange={(open) => !open && setConfirmAction(null)}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{confirmAction ? actionLabel(confirmAction) : ''}</DialogTitle>
            <DialogDescription>
              {t('mcp.lifecycleConfirm', {
                action: confirmAction ? actionLabel(confirmAction) : '',
                version: selectedVersion,
              })}
              {confirmAction === 'offline' && hubLifecycle.warns(selectedVersion || undefined) &&
                <span className="mt-2 block text-amber-600">{hubLifecycle.warningText}</span>}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setConfirmAction(null)}
              disabled={actionLoading}>
              {t('common.cancel')}
            </Button>
            <Button
              variant={confirmAction === 'deleteDraft' ? 'destructive' : 'default'}
              onClick={() => confirmAction && runLifecycleAction(confirmAction)}
              disabled={actionLoading}
            >
              {actionLoading && <Loader2 className="mr-1.5 h-4 w-4 animate-spin" />}
              {t('common.confirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

// ===== Sub-components =====

