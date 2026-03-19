import { useEffect, useCallback, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import {
  ArrowLeft,
  Pencil,
  Package,
  Hash,
  Clock,
  Tag,
  Globe,
  FileText,
} from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { useAgentSpecStore } from '@/stores/agentspec-store';
import { useNamespaceStore } from '@/stores/namespace-store';
import { agentSpecApi } from '@/api/agentspec';
import { cn } from '@/lib/utils';
import dayjs from 'dayjs';

import { VersionTimeline } from '../agentSpecManagement/components/VersionTimeline';
import { ResourceViewer } from '../agentSpecManagement/components/ResourceViewer';
import { LabelEditor } from '../agentSpecManagement/components/LabelEditor';

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

  const loadDetail = useCallback(
    (version?: string) => {
      if (agentSpecName) {
        fetchDetail(namespaceId, agentSpecName, version);
      }
    },
    [fetchDetail, namespaceId, agentSpecName],
  );

  useEffect(() => {
    loadDetail();
    return () => {
      clearDetail();
      clearError();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [agentSpecName, namespaceId]);

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

  const handleSaveLabels = async (labels: Record<string, string>) => {
    try {
      await agentSpecApi.updateLabels({
        namespaceId,
        agentSpecName,
        labels: JSON.stringify(labels),
      });
      toast.success(t('agentSpec.labelsUpdateSuccess'));
      loadDetail();
    } catch {
      // axios interceptor handles toast
    }
  };

  const handleEdit = () => {
    if (!currentDetail) return;
    const params = new URLSearchParams({
      mode: 'edit',
      name: agentSpecName,
      namespaceId,
    });
    navigate(`/agentspec/new?${params}`);
  };

  // ===== Loading skeleton =====
  if (detailLoading && !currentDetail) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-32 w-full rounded-xl" />
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
          <div className="lg:col-span-2 space-y-4">
            <Skeleton className="h-48 w-full" />
            <Skeleton className="h-64 w-full" />
          </div>
          <div className="space-y-4">
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
  const spec = detail.agentSpec;
  const versions = detail.versions || [];

  return (
    <div className="space-y-5">
      {/* ===== Hero Header ===== */}
      <div className="relative rounded-xl border bg-card overflow-hidden">
        <div className="absolute inset-0 bg-gradient-to-br from-indigo-500/[0.04] via-transparent to-cyan-500/[0.03]" />
        <div className="absolute top-0 right-0 w-64 h-64 bg-gradient-to-bl from-indigo-500/[0.06] to-transparent rounded-full -translate-y-1/2 translate-x-1/3" />

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
              {detail.editingVersion && (
                <Button size="sm" className="h-7 text-xs" onClick={handleEdit}>
                  <Pencil className="mr-1 h-3 w-3" />
                  {t('common.edit')}
                </Button>
              )}
            </div>
          </div>

          {/* Identity */}
          <div className="flex items-start gap-4">
            <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-indigo-500 to-cyan-400 shadow-lg shadow-indigo-500/20">
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
                {detail.version && (
                  <span className="text-xs text-muted-foreground font-mono bg-muted/60 px-1.5 py-0.5 rounded">
                    v{detail.version}
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
                {detail.onlineCnt > 0 && (
                  <span className="inline-flex items-center gap-1">
                    <Globe className="h-3 w-3" />
                    {t('agentSpec.onlineCount', { count: detail.onlineCnt })}
                  </span>
                )}
                {detail.updateTime > 0 && (
                  <span className="inline-flex items-center gap-1">
                    <Clock className="h-3 w-3" />
                    {dayjs(detail.updateTime).format('YYYY-MM-DD HH:mm')}
                  </span>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* ===== Content Grid ===== */}
      <div className={cn('grid grid-cols-1 lg:grid-cols-3 gap-5', (detailLoading || actionLoading) && 'opacity-50 pointer-events-none')}>
        {/* Left column - 2/3 */}
        <div className="lg:col-span-2 space-y-5">
          {/* Version Timeline */}
          <Card className="overflow-hidden py-0 gap-0">
            <div className="px-5 py-3.5 border-b bg-muted/30">
              <h2 className="text-sm font-semibold flex items-center gap-2">
                <Hash className="h-4 w-4 text-muted-foreground" />
                {t('agentSpec.versionHistory')}
                {versions.length > 0 && (
                  <span className="inline-flex items-center justify-center h-5 min-w-5 rounded-full bg-indigo-100 dark:bg-indigo-900/40 text-indigo-700 dark:text-indigo-300 text-[11px] font-semibold px-1.5">
                    {versions.length}
                  </span>
                )}
              </h2>
            </div>
            <CardContent className="p-4">
              <VersionTimeline
                versions={versions}
                currentVersion={detail.version}
                onSelectVersion={handleSelectVersion}
                onCreateDraft={handleCreateDraft}
                onSubmit={handleSubmit}
                onPublish={handlePublish}
                onOnline={handleOnline}
                onOffline={handleOffline}
              />
            </CardContent>
          </Card>

          {/* Resource Viewer */}
          <Card className="overflow-hidden py-0 gap-0">
            <div className="px-5 py-3.5 border-b bg-muted/30">
              <h2 className="text-sm font-semibold flex items-center gap-2">
                <FileText className="h-4 w-4 text-muted-foreground" />
                {t('agentSpec.resources')}
              </h2>
            </div>
            <CardContent className="p-4">
              <div className="h-[400px]">
                <ResourceViewer
                  resources={spec.resource || {}}
                  content={spec.content || '{}'}
                  editable={false}
                />
              </div>
            </CardContent>
          </Card>
        </div>

        {/* Right column - 1/3 */}
        <div className="space-y-5">
          {/* Labels */}
          <Card className="overflow-hidden py-0 gap-0">
            <div className="px-5 py-3.5 border-b bg-muted/30">
              <h2 className="text-sm font-semibold flex items-center gap-2">
                <Tag className="h-4 w-4 text-muted-foreground" />
                {t('agentSpec.labels')}
              </h2>
            </div>
            <CardContent className="p-4">
              <LabelEditor
                labels={detail.labels || {}}
                onSave={handleSaveLabels}
              />
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}
