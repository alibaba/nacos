import { Fragment, useEffect, useCallback, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import {
  ArrowLeft,
  Pencil,
  Plus,
  Cpu,
  Globe,
  ExternalLink,
  Shield,
  Package,
  Network,
  Zap,
  Lock,
  Eye,
  EyeOff,
  Server,
  GitBranch,
  Hash,
  RefreshCw,
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
  UserRound,
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
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetDescription,
} from '@/components/ui/sheet';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Separator } from '@/components/ui/separator';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { McpToolList } from '@/components/ai/mcp/McpToolList';
import { VisibilityAuthorizationDialog } from '@/components/ai/VisibilityAuthorizationDialog';
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
import { McpClientConfigCard } from './McpClientConfigCard';
import { buildMcpClientConfigurations } from './mcp-client-config';
import {
  getMcpVersionActions,
  isMcpLifecycleUnavailable,
  type McpVersionAction,
} from './mcp-lifecycle';

const PROTOCOL_STYLES: Record<string, { bg: string; text: string; dot: string }> = {
  stdio: { bg: 'bg-purple-50 dark:bg-purple-950/40', text: 'text-purple-700 dark:text-purple-300', dot: 'bg-purple-500' },
  'mcp-sse': { bg: 'bg-blue-50 dark:bg-blue-950/40', text: 'text-blue-700 dark:text-blue-300', dot: 'bg-blue-500' },
  'mcp-streamable': { bg: 'bg-cyan-50 dark:bg-cyan-950/40', text: 'text-cyan-700 dark:text-cyan-300', dot: 'bg-cyan-500' },
  http: { bg: 'bg-orange-50 dark:bg-orange-950/40', text: 'text-orange-700 dark:text-orange-300', dot: 'bg-orange-500' },
  dubbo: { bg: 'bg-green-50 dark:bg-green-950/40', text: 'text-green-700 dark:text-green-300', dot: 'bg-green-500' },
};

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
  const { globalAdmin } = useAuthStore();

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
          if (remaining.length === 0) {
            navigate('/mcpServerManagement');
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
  const isStdio = protocolLabel === 'stdio';
  const isRestToMcp = mcp.protocol === 'http' || mcp.protocol === 'https';
  const tools = mcp.toolSpec?.tools || [];
  const securitySchemes = mcp.toolSpec?.securitySchemes || [];
  const packages = mcp.packages || [];
  const backendEndpoints = mcp.backendEndpoints || [];
  const frontendEndpoints = mcp.frontendEndpoints || [];
  const legacyVersions = mcp.allVersions || [];
  const versionOptions = lifecycleAvailable
    ? versionSummaries
    : legacyVersions.map((item) => ({
        version: item.version,
        status: 'online' as const,
        latest: item.is_latest,
      }));
  const clientConfigurations = buildMcpClientConfigurations(mcp);
  const protocolStyle = PROTOCOL_STYLES[protocolLabel];
  const currentStatus = lifecycleDetail?.status;
  const currentPipelineInfo = parsePipelineInfo(lifecycleDetail?.publishPipelineInfo);
  const lifecycleActions = currentStatus
    ? getMcpVersionActions(currentStatus, currentPipelineInfo, globalAdmin)
    : [];
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
                        v{v.version} · {t(`mcp.versionStatus.${v.status}`)}
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
                {t('mcp.versionHistory')}
              </Button>

              <Separator orientation="vertical" className="h-5" />

              <Button
                variant="outline"
                size="sm"
                className="h-7 text-xs"
                onClick={handleNewVersion}
                disabled={!lifecycleAvailable}
              >
                <Plus className="mr-1 h-3 w-3" />
                {t('mcp.newVersion')}
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
                    v{mcp.versionDetail?.version || mcp.version}
                  </span>
                )}
                {(lifecycleDetail?.latest || mcp.versionDetail?.is_latest) && (
                  <Badge className="bg-emerald-100 text-emerald-700 dark:bg-emerald-950/50 dark:text-emerald-300 text-[10px] px-1.5 py-0 border-0">
                    Latest
                  </Badge>
                )}
              </div>
              <div className="flex flex-wrap items-center gap-2 mt-1.5 mb-2">
                <Badge variant="outline" className="text-[10px]">
                  {lifecycleDetail?.resourceStatus === 'disable' || !mcp.enabled
                    ? t('mcp.disabled')
                    : t('mcp.enabled')}
                </Badge>
                {currentStatus && (
                  <Badge variant="secondary" className="text-[10px]">
                    {t(`mcp.versionStatus.${currentStatus}`)}
                  </Badge>
                )}
              </div>
              {mcp.description && (
                <p className="text-sm text-muted-foreground leading-relaxed max-w-2xl">
                  {mcp.description}
                </p>
              )}

              {/* Version lifecycle action buttons */}
              {lifecycleAvailable && lifecycleActions.length > 0 && (
                <VersionLifecycleActionBar>
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

      {/* ===== Content Grid ===== */}
      <div className={cn('grid grid-cols-1 lg:grid-cols-3 gap-5', detailLoading && 'opacity-50 pointer-events-none')}>
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
                      <button
                        className="inline-flex items-center gap-1 text-primary hover:underline cursor-pointer"
                        onClick={() => {
                          const ref = mcp.remoteServerConfig!.serviceRef!;
                          const params = new URLSearchParams({
                            serviceName: ref.serviceName,
                            groupName: ref.groupName,
                            namespace: namespaceId,
                          });
                          navigate(`/serviceDetail?${params.toString()}`);
                        }}
                      >
                        <span>{mcp.remoteServerConfig.serviceRef.groupName}@@{mcp.remoteServerConfig.serviceRef.serviceName}</span>
                        <ExternalLink className="h-3 w-3" />
                      </button>
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

        </div>
      </div>

      {lifecycleAvailable && lifecycleDetail && (
        <Card className="overflow-hidden py-0 gap-0">
          <div className="flex flex-wrap items-center justify-between gap-3 px-5 py-3.5 border-b bg-muted/30">
            <h2 className="text-sm font-semibold flex items-center gap-2">
              <Shield className="h-4 w-4 text-muted-foreground" />
              {t('mcp.governance')}
            </h2>
            <Button variant="outline" size="sm" onClick={() => setVisibilityOpen(true)}>
              <UserRound className="mr-1.5 h-3.5 w-3.5" />
              {t('mcp.visibilityAuthorization')}
            </Button>
          </div>
          <CardContent className="p-5 grid grid-cols-1 lg:grid-cols-2 gap-5">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <InfoCell label={t('mcp.owner')} value={lifecycleDetail.owner || '-'} compact />
              <InfoCell label={t('mcp.scope')} value={lifecycleDetail.scope || '-'} compact />
              <InfoCell
                label={t('mcp.editingVersion')}
                value={lifecycleDetail.editingVersion || '-'}
                compact
              />
              <InfoCell
                label={t('mcp.reviewingVersion')}
                value={lifecycleDetail.reviewingVersion || '-'}
                compact
              />
              <InfoCell
                label={t('mcp.onlineCount')}
                value={String(lifecycleDetail.onlineCount ?? 0)}
                compact
              />
              <InfoCell
                label={t('mcp.latestVersion')}
                value={lifecycleDetail.labels?.latest || '-'}
                compact
              />
            </div>
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <p className="text-xs font-medium flex items-center gap-1.5">
                  <Tags className="h-3.5 w-3.5" />
                  {t('mcp.customLabels')}
                </p>
                <Button size="sm" className="h-7 text-xs" onClick={updateLabels}
                  disabled={labelsLoading}>
                  {labelsLoading && <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" />}
                  {t('common.save')}
                </Button>
              </div>
              <Textarea
                value={labelsText}
                onChange={(event) => setLabelsText(event.target.value)}
                rows={6}
                className="font-mono text-xs"
                placeholder='{"stable":"1.0.0"}'
              />
              <p className="text-[11px] text-muted-foreground">{t('mcp.latestLabelManaged')}</p>
            </div>
          </CardContent>
        </Card>
      )}

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

function InfoCell({
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
