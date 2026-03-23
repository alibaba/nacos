import { useEffect, useCallback, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import {
  ArrowLeft,
  History,
  Plus,
  Package,
  Hash,
  Clock,
  Tag,
  Globe,
  FileText,
  Send,
  CheckCircle2,
  GitBranch,
  Power,
  PowerOff,
  Trash2,
} from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
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
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet';
import { useAgentSpecStore } from '@/stores/agentspec-store';
import { useNamespaceStore } from '@/stores/namespace-store';
import { agentSpecApi } from '@/api/agentspec';
import type { AgentSpecDocument } from '@/types/agentspec';
import { parsePipelineInfo } from '@/types/agentspec';
import { cn } from '@/lib/utils';
import dayjs from 'dayjs';

import { VersionTimeline } from '../agentSpecManagement/components/VersionTimeline';
import { ResourceViewer } from '../agentSpecManagement/components/ResourceViewer';
import { sortVersionsDescending } from '../agentSpecManagement/components/version-utils';
import { VersionLabelEditor } from '@/components/ai/VersionLabelEditor';
import { PipelineStatusDisplay } from '../skillManagement/components/PipelineStatusDisplay';

export default function AgentSpecDetailPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { name: routeName } = useParams<{ name: string }>();
  const agentSpecName = routeName ? decodeURIComponent(routeName) : '';
  const { currentNamespace } = useNamespaceStore();
  const namespaceId = currentNamespace || 'public';

  const {
    currentDetail,
    detailLoading,
    error,
    fetchDetail,
    clearDetail,
    clearError,
  } = useAgentSpecStore();

  const [actionLoading, setActionLoading] = useState(false);
  const [versionSheetOpen, setVersionSheetOpen] = useState(false);
  const [detailDocument, setDetailDocument] = useState<AgentSpecDocument | null>(null);
  const [labelsSaving, setLabelsSaving] = useState(false);
  const autoLoadedVersionRef = useRef<string | null>(null);

  const loadDetail = useCallback(
    (version?: string) => {
      if (agentSpecName) {
        fetchDetail(namespaceId, agentSpecName, version);
      }
    },
    [fetchDetail, namespaceId, agentSpecName],
  );

  useEffect(() => {
    autoLoadedVersionRef.current = null;
    setDetailDocument(null);
    loadDetail();
    return () => {
      autoLoadedVersionRef.current = null;
      setDetailDocument(null);
      clearDetail();
      clearError();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [agentSpecName, namespaceId]);

  useEffect(() => {
    if (!currentDetail || detailLoading || currentDetail.version) {
      return;
    }

    const fallbackVersion = sortVersionsDescending(currentDetail.versions || [])[0]?.version;
    if (fallbackVersion && autoLoadedVersionRef.current !== fallbackVersion) {
      autoLoadedVersionRef.current = fallbackVersion;
      loadDetail(fallbackVersion);
    }
  }, [currentDetail, detailLoading, loadDetail]);

  useEffect(() => {
    if (!currentDetail) {
      setDetailDocument(null);
      return;
    }

    if (currentDetail.agentSpec) {
      setDetailDocument(currentDetail.agentSpec);
      return;
    }

    const fallbackVersion = currentDetail.version
      || sortVersionsDescending(currentDetail.versions || [])[0]?.version;
    if (!fallbackVersion) {
      setDetailDocument(null);
      return;
    }

    let cancelled = false;
    setDetailDocument(null);

    agentSpecApi.getVersion({
      namespaceId,
      agentSpecName,
      version: fallbackVersion,
    }).then((response) => {
      if (!cancelled) {
        setDetailDocument(response.data);
      }
    }).catch(() => {
      if (!cancelled) {
        setDetailDocument(null);
      }
    });

    return () => {
      cancelled = true;
    };
  }, [currentDetail, namespaceId, agentSpecName]);

  // ===== Version lifecycle handlers =====

  const handleCreateDraft = async (basedOnVersion?: string) => {
    setActionLoading(true);
    try {
      await agentSpecApi.createDraft({
        namespaceId,
        agentSpecName,
        basedOnVersion,
      });
      toast.success(t('agentSpec.createDraftSuccess'));
      loadDetail();
    } catch {
      // axios interceptor handles toast
      loadDetail(); // refresh to show latest state
    } finally {
      setActionLoading(false);
    }
  };

  const handleSubmit = async (version: string) => {
    setActionLoading(true);
    try {
      await agentSpecApi.submit({ namespaceId, agentSpecName, version });
      toast.success(t('agentSpec.submitSuccess'));
      loadDetail();
    } catch {
      loadDetail();
    } finally {
      setActionLoading(false);
    }
  };

  const handleDeleteDraft = async () => {
    setActionLoading(true);
    try {
      await agentSpecApi.deleteDraft({ namespaceId, agentSpecName });
      toast.success(t('agentSpec.deleteDraftSuccess'));
      loadDetail();
    } catch {
      loadDetail();
    } finally {
      setActionLoading(false);
    }
  };

  const handlePublish = async (version: string) => {
    setActionLoading(true);
    try {
      await agentSpecApi.publish({
        namespaceId,
        agentSpecName,
        version,
        updateLatestLabel: true,
      });
      toast.success(t('agentSpec.publishSuccess'));
      loadDetail();
    } catch {
      loadDetail();
    } finally {
      setActionLoading(false);
    }
  };

  const handleOnline = async (version: string) => {
    setActionLoading(true);
    try {
      await agentSpecApi.online({ namespaceId, agentSpecName, version });
      toast.success(t('agentSpec.onlineSuccess'));
      loadDetail();
    } catch {
      loadDetail();
    } finally {
      setActionLoading(false);
    }
  };

  const handleOffline = async (version: string) => {
    setActionLoading(true);
    try {
      await agentSpecApi.offline({ namespaceId, agentSpecName, version });
      toast.success(t('agentSpec.offlineSuccess'));
      loadDetail();
    } catch {
      loadDetail();
    } finally {
      setActionLoading(false);
    }
  };

  const handleSelectVersion = (version: string) => {
    loadDetail(version);
  };

  const handleSaveLabels = async (newLabels: Record<string, string>) => {
    setLabelsSaving(true);
    try {
      const latestValue = detail?.labels?.latest;
      const merged = latestValue ? { ...newLabels, latest: latestValue } : newLabels;
      await agentSpecApi.updateLabels({
        namespaceId,
        agentSpecName,
        labels: JSON.stringify(merged),
      });
      toast.success(t('common.versionLabels.updateSuccess'));
      loadDetail();
    } catch {
      // handled by interceptor
    } finally {
      setLabelsSaving(false);
    }
  };

  const handleNewVersion = () => {
    if (!currentDetail?.version) {
      return;
    }

    navigate(
      `/agentspec/new?${buildAgentSpecEditorSearch({
        mode: 'version',
        name: agentSpecName,
        namespaceId,
        sourceVersion: currentDetail.version,
      })}`,
    );
  };

  // ===== Loading skeleton =====
  if (detailLoading && !currentDetail) {
    return (
      <div className="space-y-5">
        <Skeleton className="h-32 w-full rounded-xl" />
        <div className="grid grid-cols-1 gap-5 lg:grid-cols-[minmax(0,1fr)_320px]">
          <div className="space-y-5">
            <Skeleton className="h-48 w-full" />
            <Skeleton className="h-64 w-full" />
          </div>
          <div className="space-y-5">
            <Skeleton className="h-48 w-full" />
          </div>
        </div>
      </div>
    );
  }

  // ===== Error state =====
  if (error && !currentDetail) {
    return (
      <div className="flex flex-col items-center justify-center py-24 space-y-4">
        <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-destructive/10 mb-2">
          <Package className="h-8 w-8 text-destructive/50" />
        </div>
        <p className="text-sm text-destructive">{error}</p>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => navigate('/agentspec')}>
            {t('agentSpec.backToList')}
          </Button>
          <Button onClick={() => loadDetail()}>
            {t('common.retry')}
          </Button>
        </div>
      </div>
    );
  }

  if (!currentDetail) return null;

  const detail = currentDetail;
  const spec = detail.agentSpec ?? detailDocument ?? {
    namespaceId,
    name: agentSpecName,
    description: '',
    content: '{}',
    resource: {},
  };
  const versions = sortVersionsDescending(detail.versions || []);
  const latestVersion = detail.labels?.latest;
  const selectedVersion = detail.version || versions[0]?.version || '';
  const displayVersion = selectedVersion || '-';
  const versionOptions = (() => {
    const seen = new Set<string>();
    const result = [] as typeof versions;

    if (selectedVersion) {
      seen.add(selectedVersion);
      const current = versions.find((item) => item.version === selectedVersion);
      result.push(
        current ?? {
          version: selectedVersion,
          status: detail.versionStatus,
          author: '',
          description: spec.description || '',
          createTime: detail.updateTime,
          updateTime: detail.updateTime,
          publishPipelineInfo: null,
        },
      );
    }

    for (const item of versions) {
      if (!seen.has(item.version)) {
        seen.add(item.version);
        result.push(item);
      }
    }

    return result;
  })();
  const currentVersionSummary = versionOptions.find((item) => item.version === selectedVersion);
  const currentVersionStatus = currentVersionSummary?.status || detail.versionStatus;
  const currentPipelineInfo = parsePipelineInfo(currentVersionSummary?.publishPipelineInfo);
  const currentVersionStatusLabel = currentVersionStatus
    ? t(`agentSpec.versionStatus.${currentVersionStatus}`)
    : '-';
  const currentVersionCreateTime = currentVersionSummary?.createTime || 0;
  const onlineVersionCountLabel = t('agentSpec.onlineCount', { count: detail.onlineCnt ?? 0 });
  const labelEntries = Object.entries(detail.labels || {}).filter(([key]) => key !== 'latest');

  return (
    <div className="flex h-[calc(100vh-88px)] min-h-0 flex-col gap-5 overflow-hidden">
      {/* ===== Hero Header ===== */}
      <div className="relative rounded-xl border bg-card overflow-hidden">
        <div className="absolute inset-0 bg-gradient-to-br from-primary/[0.04] via-transparent to-blue-500/[0.03]" />
        <div className="absolute top-0 right-0 w-64 h-64 bg-gradient-to-bl from-primary/[0.06] to-transparent rounded-full -translate-y-1/2 translate-x-1/3" />

        <div className="relative px-5 py-4">
          {/* Top bar */}
          <div className="flex items-center justify-between mb-4">
            <Button
              variant="ghost"
              size="sm"
              className="h-7 gap-1.5 text-muted-foreground hover:text-foreground -ml-2"
              onClick={() => navigate('/agentspec')}
            >
              <ArrowLeft className="h-3.5 w-3.5" />
              {t('agentSpec.backToList')}
            </Button>

            <div className="flex items-center gap-2">
              {selectedVersion && (
                <Select value={selectedVersion} onValueChange={handleSelectVersion}>
                  <SelectTrigger className="w-[140px] h-7 text-xs bg-background/80">
                    <SelectValue placeholder={t('agentSpec.selectVersion')} />
                  </SelectTrigger>
                  <SelectContent>
                    {versionOptions.map((version) => (
                      <SelectItem key={version.version} value={version.version}>
                        <span className="flex items-center gap-2">
                          <span>{version.version}</span>
                          {latestVersion === version.version && (
                            <Badge className="bg-emerald-100 text-emerald-700 dark:bg-emerald-950/50 dark:text-emerald-300 text-[10px] px-1 py-0 border-0">
                              {t('agentSpec.latestVersion')}
                            </Badge>
                          )}
                        </span>
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}

              <Button
                variant="outline"
                size="sm"
                className="h-7 text-xs"
                onClick={() => setVersionSheetOpen(true)}
              >
                <History className="mr-1 h-3 w-3" />
                {t('agentSpec.versionHistory')}
              </Button>

              {detail.version && (
                <Button
                  variant="outline"
                  size="sm"
                  className="h-7 text-xs"
                  onClick={handleNewVersion}
                >
                  <Plus className="mr-1 h-3 w-3" />
                  {t('agentSpec.newVersion')}
                </Button>
              )}

            </div>
          </div>

          {/* Identity */}
          <div className="flex items-start gap-4">
            <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-blue-500 to-cyan-400 shadow-lg shadow-blue-500/20">
              <Package className="h-7 w-7 text-white" />
            </div>

            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2.5 mb-1">
                <h1 className="text-xl font-bold tracking-tight">{spec.name}</h1>
                <Badge
                  className={cn(
                    'text-[10px] px-1.5 py-0 h-4 font-medium border-0',
                    detail.enable
                      ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300'
                      : 'bg-gray-100 text-gray-500 dark:bg-gray-800 dark:text-gray-400',
                  )}
                >
                  {detail.enable ? t('agentSpec.enabled') : t('agentSpec.disabled')}
                </Badge>
                {selectedVersion && (
                  <span className="text-xs text-muted-foreground font-mono bg-muted/60 px-1.5 py-0.5 rounded">
                    {selectedVersion}
                  </span>
                )}
              </div>
              {spec.description && (
                <p className="text-sm text-muted-foreground leading-relaxed max-w-2xl">
                  {spec.description}
                </p>
              )}

              {/* Meta row */}
              <div className="flex items-center gap-4 mt-2 text-xs text-muted-foreground">
                <span className="inline-flex items-center gap-1">
                  <Globe className="h-3 w-3" />
                  {onlineVersionCountLabel}
                </span>
                {detail.updateTime > 0 && (
                  <span className="inline-flex items-center gap-1">
                    <Clock className="h-3 w-3" />
                    {dayjs(detail.updateTime).format('YYYY-MM-DD HH:mm')}
                  </span>
                )}
              </div>

              {/* Version lifecycle action buttons */}
              {selectedVersion && currentVersionStatus && (
                <div className="flex items-center gap-2 mt-3 pt-3 border-t border-border/40">
                  {/* Draft actions */}
                  {currentVersionStatus === 'draft' && (
                    <>
                      <Button
                        size="sm"
                        className="h-7 text-xs gap-1.5"
                        disabled={actionLoading}
                        onClick={() => handleSubmit(selectedVersion)}
                      >
                        <Send className="h-3 w-3" />
                        {t('agentSpec.submit')}
                      </Button>
                      <Button
                        variant="outline"
                        size="sm"
                        className="h-7 text-xs gap-1.5 text-destructive hover:text-destructive hover:bg-destructive/10"
                        disabled={actionLoading}
                        onClick={handleDeleteDraft}
                      >
                        <Trash2 className="h-3 w-3" />
                        {t('agentSpec.deleteDraft')}
                      </Button>
                    </>
                  )}

                  {/* Reviewing actions */}
                  {currentVersionStatus === 'reviewing' && (
                    <Button
                      size="sm"
                      className="h-7 text-xs gap-1.5"
                      disabled={actionLoading}
                      onClick={() => handlePublish(selectedVersion)}
                    >
                      <CheckCircle2 className="h-3 w-3" />
                      {t('agentSpec.publish')}
                    </Button>
                  )}

                  {/* Online actions */}
                  {currentVersionStatus === 'online' && (
                    <Button
                      variant="outline"
                      size="sm"
                      className="h-7 text-xs gap-1.5"
                      disabled={actionLoading}
                      onClick={() => handleOffline(selectedVersion)}
                    >
                      <PowerOff className="h-3 w-3" />
                      {t('agentSpec.offline')}
                    </Button>
                  )}

                  {/* Offline actions */}
                  {currentVersionStatus === 'offline' && (
                    <Button
                      size="sm"
                      className="h-7 text-xs gap-1.5"
                      disabled={actionLoading}
                      onClick={() => handleOnline(selectedVersion)}
                    >
                      <Power className="h-3 w-3" />
                      {t('agentSpec.online')}
                    </Button>
                  )}

                  {/* Create new draft (when viewing online/offline and no editing/reviewing version) */}
                  {(currentVersionStatus === 'online' || currentVersionStatus === 'offline') &&
                    !detail.editingVersion && !detail.reviewingVersion && (
                    <Button
                      variant="outline"
                      size="sm"
                      className="h-7 text-xs gap-1.5"
                      disabled={actionLoading}
                      onClick={() => handleCreateDraft(selectedVersion)}
                    >
                      <Plus className="h-3 w-3" />
                      {t('agentSpec.createDraftFrom')}
                    </Button>
                  )}
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* ===== Content Grid ===== */}
      <div className={cn('grid min-h-0 flex-1 grid-cols-1 gap-5 overflow-hidden lg:grid-cols-[minmax(0,1fr)_320px]', (detailLoading || actionLoading) && 'opacity-50 pointer-events-none')}>
        {/* Left column */}
        <div className="flex min-h-0 flex-col">
          {/* Resource viewer card */}
          <Card className="flex min-h-0 flex-1 flex-col overflow-hidden py-0 gap-0">
            <div className="px-5 py-3.5 border-b bg-muted/30">
              <h2 className="text-sm font-semibold flex items-center gap-2">
                <FileText className="h-4 w-4 text-muted-foreground" />
                {t('agentSpec.resources')}
              </h2>
            </div>
            <CardContent className="flex-1 min-h-0 p-0">
              <ResourceViewer
                resources={spec.resource || {}}
                content={spec.content || '{}'}
                editable={false}
                className="h-full min-h-0"
              />
            </CardContent>
          </Card>
        </div>

        {/* Right column */}
        <div className="space-y-4 lg:w-[320px]">
          {/* Basic info card */}
          <Card className="overflow-hidden py-0 gap-0">
            <div className="px-4 py-3 border-b bg-muted/30">
              <h2 className="text-sm font-semibold flex items-center gap-2">
                <Package className="h-4 w-4 text-muted-foreground" />
                {t('agentSpec.basicInfo')}
              </h2>
            </div>
            <CardContent className="p-0">
              <div className="grid grid-cols-1 divide-y divide-border">
                <InfoCell compact label={t('agentSpec.agentSpecName')} value={spec.name || '-'} icon={<Package className="h-3.5 w-3.5" />} />
                <InfoCell compact label={t('agentSpec.version')} value={displayVersion} icon={<Hash className="h-3.5 w-3.5" />} />
                <InfoCell
                  compact
                  label={t('agentSpec.status')}
                  value={<StatusBadge status={currentVersionStatus} label={currentVersionStatusLabel} />}
                  icon={<Tag className="h-3.5 w-3.5" />}
                />
                <InfoCell compact label={t('agentSpec.createTime')} value={currentVersionCreateTime > 0 ? dayjs(currentVersionCreateTime).format('YYYY-MM-DD HH:mm') : '-'} icon={<Clock className="h-3.5 w-3.5" />} />
                <InfoCell compact label={t('agentSpec.updateTime')} value={detail.updateTime > 0 ? dayjs(detail.updateTime).format('YYYY-MM-DD HH:mm') : '-'} icon={<Clock className="h-3.5 w-3.5" />} />
              </div>
              {spec.description && (
                <div className="border-t px-4 py-2.5">
                  <p className="text-[11px] text-muted-foreground leading-none mb-1">{t('agentSpec.description')}</p>
                  <div className="text-sm text-foreground whitespace-pre-wrap break-words">{spec.description}</div>
                </div>
              )}
            </CardContent>
          </Card>

          {currentVersionStatus === 'reviewing' && (
            <Card className="overflow-hidden py-0 gap-0">
              <div className="px-4 py-3 border-b bg-muted/30">
                <h2 className="text-sm font-semibold flex items-center gap-2">
                  <GitBranch className="h-4 w-4 text-muted-foreground" />
                  {t('agentSpec.pipelineStatus')}
                </h2>
              </div>
              <CardContent className="p-3.5">
                <PipelineStatusDisplay
                  pipelineInfo={currentPipelineInfo}
                  translationPrefix="agentSpec"
                />
              </CardContent>
            </Card>
          )}

          {/* Labels card (editable) */}
          <Card className="overflow-hidden py-0 gap-0">
            <div className="px-4 py-3 border-b bg-muted/30">
              <h2 className="text-sm font-semibold flex items-center gap-2">
                <Tag className="h-4 w-4 text-muted-foreground" />
                {t('common.versionLabels.title')}
              </h2>
            </div>
            <CardContent className="p-3.5">
              <VersionLabelEditor
                labels={Object.fromEntries(labelEntries)}
                availableVersions={versions.map((v) => v.version)}
                onSave={handleSaveLabels}
                isSaving={labelsSaving}
              />
            </CardContent>
          </Card>
        </div>
      </div>

      <Sheet open={versionSheetOpen} onOpenChange={setVersionSheetOpen}>
        <SheetContent className="flex flex-col p-0 sm:max-w-md">
          <SheetHeader className="px-6 pt-6 pb-4 border-b shrink-0">
            <SheetTitle className="flex items-center gap-2">
              <History className="h-4.5 w-4.5 text-blue-500" />
              {t('agentSpec.versionHistory')}
            </SheetTitle>
            <SheetDescription>
              {t('agentSpec.totalVersions', { count: versions.length })}
            </SheetDescription>
          </SheetHeader>
          <div className="flex-1 overflow-y-auto px-4 py-4">
            <VersionTimeline
              versions={versions}
              currentVersion={detail.version}
              onSelectVersion={(version) => {
                handleSelectVersion(version);
                setVersionSheetOpen(false);
              }}
              onCreateDraft={handleCreateDraft}
              onDeleteDraft={handleDeleteDraft}
              onSubmit={handleSubmit}
              onPublish={handlePublish}
              onOnline={handleOnline}
              onOffline={handleOffline}
              showCreateDraftButton={false}
              allLabels={detail.labels}
              onSaveLabels={handleSaveLabels}
            />
          </div>
        </SheetContent>
      </Sheet>
    </div>
  );
}

function InfoCell({
  label,
  value,
  icon,
  compact = false,
}: {
  label: string;
  value: React.ReactNode;
  icon?: React.ReactNode;
  compact?: boolean;
}) {
  return (
    <div className={cn('flex items-center gap-3 px-5 py-3', compact && 'gap-2.5 px-4 py-2.5')}>
      {icon && (
        <span className="text-muted-foreground/60 shrink-0">{icon}</span>
      )}
      <div className="min-w-0 flex-1">
        <p className="text-[11px] text-muted-foreground leading-none mb-1">{label}</p>
        <div className={cn('text-sm font-medium break-all', compact && 'text-[13px]')}>{value || '-'}</div>
      </div>
    </div>
  );
}

function StatusBadge({
  status,
  label,
}: {
  status?: 'draft' | 'reviewing' | 'online' | 'offline';
  label: string;
}) {
  const statusStyles: Record<string, string> = {
    draft: 'bg-amber-50 text-amber-700 dark:bg-amber-950/40 dark:text-amber-300',
    reviewing: 'bg-blue-50 text-blue-700 dark:bg-blue-950/40 dark:text-blue-300',
    online: 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300',
    offline: 'bg-gray-100 text-gray-500 dark:bg-gray-800 dark:text-gray-400',
  };

  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium',
        status ? statusStyles[status] : statusStyles.offline,
      )}
    >
      {label}
    </span>
  );
}
