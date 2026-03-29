import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { ArrowLeft, Pencil, History, Trash2, Copy, Check, Clock, FileText, Hash, Tag, AppWindow, AlignLeft } from 'lucide-react';

import { useConfigStore } from '@/stores/config-store';
import { configApi } from '@/api/config';
import { MonacoEditor } from '@/components/config/MonacoEditor';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import type { ConfigType } from '@/types/config';

export default function ConfigDetailPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { t } = useTranslation();
  const { currentConfig, detailLoading, fetchConfig, clearCurrentConfig } = useConfigStore();

  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [copiedField, setCopiedField] = useState<string | null>(null);

  const handleCopy = (text: string, field: string) => {
    navigator.clipboard.writeText(text);
    setCopiedField(field);
    setTimeout(() => setCopiedField(null), 1500);
  };

  const dataId = searchParams.get('dataId') || '';
  const group = searchParams.get('group') || '';
  const namespace = searchParams.get('namespace') || '';

  useEffect(() => {
    if (dataId && group) {
      fetchConfig(dataId, group, namespace);
    }

    return () => {
      clearCurrentConfig();
    };
  }, [dataId, group, namespace, fetchConfig, clearCurrentConfig]);

  const handleBack = () => {
    navigate('/configurationManagement');
  };

  const handleEdit = () => {
    navigate(`/configeditor?dataId=${encodeURIComponent(dataId)}&group=${encodeURIComponent(group)}&namespace=${encodeURIComponent(namespace)}`);
  };

  const handleHistory = () => {
    navigate(`/historyRollback?dataId=${encodeURIComponent(dataId)}&group=${encodeURIComponent(group)}&namespace=${encodeURIComponent(namespace)}`);
  };

  const handleDelete = async () => {
    setDeleting(true);
    try {
      await configApi.delete({
        dataId,
        groupName: group,
        namespaceId: namespace,
      });
      toast.success(t('config.deleteSuccess'));
      navigate('/configurationManagement');
    } catch (error) {
      toast.error(t('common.failed'));
    } finally {
      setDeleting(false);
      setDeleteDialogOpen(false);
    }
  };

  const getTypeBadgeClass = (type: string): string => {
    switch (type) {
      case 'json': return 'bg-amber-100 text-amber-800 border-amber-200 dark:bg-amber-900/30 dark:text-amber-300 dark:border-amber-800';
      case 'yaml': case 'yml': return 'bg-emerald-100 text-emerald-800 border-emerald-200 dark:bg-emerald-900/30 dark:text-emerald-300 dark:border-emerald-800';
      case 'xml': return 'bg-sky-100 text-sky-800 border-sky-200 dark:bg-sky-900/30 dark:text-sky-300 dark:border-sky-800';
      case 'properties': return 'bg-violet-100 text-violet-800 border-violet-200 dark:bg-violet-900/30 dark:text-violet-300 dark:border-violet-800';
      case 'html': return 'bg-rose-100 text-rose-800 border-rose-200 dark:bg-rose-900/30 dark:text-rose-300 dark:border-rose-800';
      case 'toml': return 'bg-orange-100 text-orange-800 border-orange-200 dark:bg-orange-900/30 dark:text-orange-300 dark:border-orange-800';
      default: return 'bg-gray-100 text-gray-700 border-gray-200 dark:bg-gray-800/50 dark:text-gray-300 dark:border-gray-700';
    }
  };

  /** Format timestamp to readable date string */
  const formatTime = (time: string | undefined): string => {
    if (!time) return '-';
    const n = Number(time);
    if (!isNaN(n) && n > 1e11) {
      const d = new Date(n);
      const pad = (v: number) => String(v).padStart(2, '0');
      return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
    }
    return time;
  };

  /** Inline copyable text with refined hover interaction */
  const CopyableText = ({ text, field, mono }: { text: string; field: string; mono?: boolean }) => (
    <span className="group/copy inline-flex items-center gap-1.5 max-w-full">
      <span className={`truncate ${mono ? 'font-mono text-[13px] tracking-tight' : ''}`}>{text}</span>
      <button
        onClick={() => handleCopy(text, field)}
        className="shrink-0 opacity-0 group-hover/copy:opacity-100 transition-all duration-200 p-1 rounded-md hover:bg-primary/10 dark:hover:bg-primary/20"
        aria-label="Copy"
      >
        {copiedField === field
          ? <Check className="h-3.5 w-3.5 text-emerald-500" />
          : <Copy className="h-3.5 w-3.5 text-muted-foreground" />}
      </button>
    </span>
  );

  if (detailLoading) {
    return (
      <div className="space-y-6">
        <div className="flex items-center gap-4">
          <Skeleton className="h-9 w-9" />
          <Skeleton className="h-8 w-48" />
        </div>
        <Card>
          <CardContent className="p-6">
            <div className="grid grid-cols-3 gap-6">
              {Array.from({ length: 9 }).map((_, i) => (
                <div key={i} className="space-y-2">
                  <Skeleton className="h-4 w-20" />
                  <Skeleton className="h-5 w-32" />
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
        <Skeleton className="h-[350px] w-full" />
      </div>
    );
  }

  if (!currentConfig) {
    return (
      <div className="flex flex-col items-center justify-center h-[400px] gap-4">
        <p className="text-muted-foreground">{t('common.noData')}</p>
        <Button variant="outline" onClick={handleBack}>
          <ArrowLeft className="h-4 w-4 mr-2" />
          {t('common.back')}
        </Button>
      </div>
    );
  }

  return (
    <div className="space-y-6 p-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Button variant="outline" size="icon" onClick={handleBack}>
            <ArrowLeft className="h-4 w-4" />
          </Button>
          <h1 className="text-2xl font-semibold">{t('config.configDetail')}</h1>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" onClick={handleHistory}>
            <History className="h-4 w-4 mr-2" />
            {t('config.history')}
          </Button>
          <Button onClick={handleEdit}>
            <Pencil className="h-4 w-4 mr-2" />
            {t('common.edit')}
          </Button>
          <Button variant="destructive" onClick={() => setDeleteDialogOpen(true)}>
            <Trash2 className="h-4 w-4 mr-2" />
            {t('common.delete')}
          </Button>
        </div>
      </div>

      {/* Info Card */}
      <Card className="overflow-hidden">
        <CardContent className="p-0">
          {/* Hero identity banner — Data ID prominently displayed with type-tinted bg */}
          <div className="px-6 py-5 border-b border-border/60">
            <div className="flex items-start justify-between gap-4">
              <div className="min-w-0 flex-1 flex flex-wrap items-start gap-x-10 gap-y-3">
                {/* Data ID */}
                <div className="min-w-0">
                  <div className="flex items-center gap-2 mb-1">
                    <FileText className="h-3.5 w-3.5 text-muted-foreground shrink-0" />
                    <span className="text-xs font-medium uppercase tracking-wider text-muted-foreground">{t('config.dataId')}</span>
                  </div>
                  <div className="text-base font-semibold tracking-tight break-all leading-snug">
                    <CopyableText text={currentConfig.dataId} field="dataId" />
                  </div>
                </div>
                {/* Group */}
                <div className="min-w-0">
                  <div className="flex items-center gap-2 mb-1">
                    <Hash className="h-3.5 w-3.5 text-muted-foreground shrink-0" />
                    <span className="text-xs font-medium uppercase tracking-wider text-muted-foreground">{t('config.group')}</span>
                  </div>
                  <div className="text-base font-semibold tracking-tight break-all">
                    <CopyableText text={currentConfig.groupName} field="group" />
                  </div>
                </div>
              </div>
              {/* Type badge — visually prominent pill */}
              <span className={`shrink-0 inline-flex items-center rounded-full border px-3.5 py-1 text-xs font-bold tracking-wide shadow-sm ${getTypeBadgeClass(currentConfig.type)}`}>
                {currentConfig.type?.toUpperCase() || 'TEXT'}
              </span>
            </div>
          </div>

          {/* Metadata grid — structured two-column layout */}
          <div className="px-6 py-5 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
            {/* App Name */}
            <div className="flex items-start gap-3">
              <AppWindow className="h-4 w-4 text-muted-foreground mt-0.5 shrink-0" />
              <div className="min-w-0">
                <div className="text-xs font-medium text-muted-foreground mb-0.5">{t('config.appName')}</div>
                <div className="text-sm">{currentConfig.appName || '-'}</div>
              </div>
            </div>

            {/* Tags */}
            <div className="flex items-start gap-3">
              <Tag className="h-4 w-4 text-muted-foreground mt-0.5 shrink-0" />
              <div className="min-w-0">
                <div className="text-xs font-medium text-muted-foreground mb-1">{t('config.tags')}</div>
                <div className="flex flex-wrap gap-1.5">
                  {currentConfig.configTags
                    ? currentConfig.configTags.split(',').filter(Boolean).map((tag, i) => (
                        <span key={i} className="inline-flex items-center rounded-full bg-secondary px-2.5 py-0.5 text-xs font-medium text-secondary-foreground ring-1 ring-inset ring-border/50">
                          {tag.trim()}
                        </span>
                      ))
                    : <span className="text-sm text-muted-foreground">-</span>
                  }
                </div>
              </div>
            </div>

            {/* Description */}
            <div className="flex items-start gap-3 sm:col-span-2 lg:col-span-1">
              <AlignLeft className="h-4 w-4 text-muted-foreground mt-0.5 shrink-0" />
              <div className="min-w-0">
                <div className="text-xs font-medium text-muted-foreground mb-0.5">{t('config.description')}</div>
                <div className="text-sm leading-relaxed">{currentConfig.desc || '-'}</div>
              </div>
            </div>

            {/* Divider spanning full width */}
            <div className="sm:col-span-2 lg:col-span-3 border-t border-dashed border-border/60" />

            {/* MD5 */}
            <div className="flex items-start gap-3">
              <Hash className="h-4 w-4 text-muted-foreground mt-0.5 shrink-0" />
              <div className="min-w-0 overflow-hidden">
                <div className="text-xs font-medium text-muted-foreground mb-0.5">{t('config.md5')}</div>
                <div className="text-sm">
                  <CopyableText text={currentConfig.md5} field="md5" mono />
                </div>
              </div>
            </div>

            {/* Create Time */}
            <div className="flex items-start gap-3">
              <Clock className="h-4 w-4 text-muted-foreground mt-0.5 shrink-0" />
              <div className="min-w-0">
                <div className="text-xs font-medium text-muted-foreground mb-0.5">{t('config.createTime')}</div>
                <div className="text-sm tabular-nums">{formatTime(currentConfig.createTime)}</div>
              </div>
            </div>

            {/* Modify Time */}
            <div className="flex items-start gap-3">
              <Clock className="h-4 w-4 text-muted-foreground mt-0.5 shrink-0" />
              <div className="min-w-0">
                <div className="text-xs font-medium text-muted-foreground mb-0.5">{t('config.modifyTime')}</div>
                <div className="text-sm tabular-nums">{formatTime(currentConfig.modifyTime)}</div>
              </div>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Content Card */}
      <Card>
        <CardHeader>
          <CardTitle>{t('config.content')}</CardTitle>
        </CardHeader>
        <CardContent className="p-6 pt-0">
          <MonacoEditor
            value={currentConfig.content || ''}
            language={(currentConfig.type as ConfigType) || 'text'}
            readOnly
            height="350px"
          />
        </CardContent>
      </Card>

      {/* Delete Confirmation Dialog */}
      <Dialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('common.delete')}</DialogTitle>
            <DialogDescription>{t('config.deleteConfirm')}</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteDialogOpen(false)} disabled={deleting}>
              {t('common.cancel')}
            </Button>
            <Button variant="destructive" onClick={handleDelete} disabled={deleting}>
              {deleting ? t('common.loading') : t('common.confirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
