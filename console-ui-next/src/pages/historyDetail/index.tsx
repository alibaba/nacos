import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ArrowLeft, Copy, Check, FileText, Hash, AppWindow, User, Globe, Zap, Clock, Shield } from 'lucide-react';

import { useHistoryStore } from '@/stores/history-store';
import { MonacoEditor } from '@/components/config/MonacoEditor';
import type { ConfigType } from '@/types/config';

import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';

export default function HistoryDetailPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const dataId = searchParams.get('dataId') || '';
  const group = searchParams.get('group') || '';
  const nid = searchParams.get('nid') || '';
  const namespace = searchParams.get('namespace') || '';

  const { currentHistory, detailLoading, fetchHistoryDetail, clearCurrentHistory } =
    useHistoryStore();

  const [copiedField, setCopiedField] = useState<string | null>(null);

  const handleCopy = (text: string, field: string) => {
    navigator.clipboard.writeText(text);
    setCopiedField(field);
    setTimeout(() => setCopiedField(null), 1500);
  };

  useEffect(() => {
    if (nid && dataId && group) {
      fetchHistoryDetail(nid, dataId, group, namespace);
    }
    return () => {
      clearCurrentHistory();
    };
  }, [nid, dataId, group, namespace, fetchHistoryDetail, clearCurrentHistory]);

  const handleBack = () => {
    navigate(
      `/historyRollback?dataId=${encodeURIComponent(dataId)}&group=${encodeURIComponent(group)}&namespace=${encodeURIComponent(namespace)}`
    );
  };

  const getOpTypeLabel = (opType: string) => {
    const trimmed = opType?.trim();
    switch (trimmed) {
      case 'I': return t('history.opInsert');
      case 'U': return t('history.opUpdate');
      case 'D': return t('history.opDelete');
      default: return opType;
    }
  };

  const getOpTypeBadgeClass = (opType: string): string => {
    const trimmed = opType?.trim();
    switch (trimmed) {
      case 'I': return 'bg-emerald-100 text-emerald-800 border-emerald-200 dark:bg-emerald-900/30 dark:text-emerald-300 dark:border-emerald-800';
      case 'U': return 'bg-amber-100 text-amber-800 border-amber-200 dark:bg-amber-900/30 dark:text-amber-300 dark:border-amber-800';
      case 'D': return 'bg-rose-100 text-rose-800 border-rose-200 dark:bg-rose-900/30 dark:text-rose-300 dark:border-rose-800';
      default: return 'bg-gray-100 text-gray-700 border-gray-200 dark:bg-gray-800/50 dark:text-gray-300 dark:border-gray-700';
    }
  };

  const getPublishTypeDisplay = () => {
    if (!currentHistory) return '-';
    if (currentHistory.publishType === 'gray') {
      return t('history.gray');
    }
    return t('history.formal');
  };

  const getPublishTypeBadgeClass = (publishType: string): string => {
    if (publishType === 'gray') {
      return 'bg-violet-100 text-violet-800 border-violet-200 dark:bg-violet-900/30 dark:text-violet-300 dark:border-violet-800';
    }
    return 'bg-sky-100 text-sky-800 border-sky-200 dark:bg-sky-900/30 dark:text-sky-300 dark:border-sky-800';
  };

  const getGrayRule = (): string | null => {
    if (!currentHistory || currentHistory.publishType !== 'gray') return null;
    try {
      const extInfo = JSON.parse(currentHistory.extInfo || '{}');
      const grayRule = extInfo.gray_rule;
      if (typeof grayRule === 'string') {
        const parsed = JSON.parse(grayRule);
        return parsed.expr || grayRule;
      }
      if (grayRule?.expr) return grayRule.expr;
      return null;
    } catch {
      return null;
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
      <div className="flex flex-col gap-6">
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

  if (!currentHistory) {
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

  const grayRule = getGrayRule();

  return (
    <div className="flex flex-col gap-6">
      {/* Header */}
      <div className="flex items-center gap-4">
        <Button variant="outline" size="icon" onClick={handleBack}>
          <ArrowLeft className="h-4 w-4" />
        </Button>
        <h1 className="text-2xl font-semibold">{t('history.detailTitle')}</h1>
      </div>

      {/* Metadata Card */}
      <Card className="overflow-hidden">
        <CardContent className="p-0">
          {/* Hero identity banner — Data ID + Group prominently displayed with op-type-tinted bg */}
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
                    <CopyableText text={currentHistory.dataId} field="dataId" />
                  </div>
                </div>
                {/* Group */}
                <div className="min-w-0">
                  <div className="flex items-center gap-2 mb-1">
                    <Hash className="h-3.5 w-3.5 text-muted-foreground shrink-0" />
                    <span className="text-xs font-medium uppercase tracking-wider text-muted-foreground">{t('config.group')}</span>
                  </div>
                  <div className="text-base font-semibold tracking-tight break-all">
                    <CopyableText text={currentHistory.groupName} field="group" />
                  </div>
                </div>
              </div>
              {/* Op Type badge — visually prominent pill */}
              <span className={`shrink-0 inline-flex items-center rounded-full border px-3.5 py-1 text-xs font-bold tracking-wide shadow-sm ${getOpTypeBadgeClass(currentHistory.opType)}`}>
                {getOpTypeLabel(currentHistory.opType)}
              </span>
            </div>
          </div>

          {/* Metadata grid */}
          <div className="px-6 py-5 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
            {/* Namespace */}
            <div className="flex items-start gap-3">
              <Globe className="h-4 w-4 text-muted-foreground mt-0.5 shrink-0" />
              <div className="min-w-0">
                <div className="text-xs font-medium text-muted-foreground mb-0.5">{t('common.selectNamespace')}</div>
                <div className="text-sm">{namespace || 'public'}</div>
              </div>
            </div>

            {/* App Name */}
            <div className="flex items-start gap-3">
              <AppWindow className="h-4 w-4 text-muted-foreground mt-0.5 shrink-0" />
              <div className="min-w-0">
                <div className="text-xs font-medium text-muted-foreground mb-0.5">{t('config.appName')}</div>
                <div className="text-sm">{currentHistory.appName || '-'}</div>
              </div>
            </div>

            {/* Publish Type */}
            <div className="flex items-start gap-3">
              <Shield className="h-4 w-4 text-muted-foreground mt-0.5 shrink-0" />
              <div className="min-w-0">
                <div className="text-xs font-medium text-muted-foreground mb-1">{t('history.publishType')}</div>
                <span className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold ${getPublishTypeBadgeClass(currentHistory.publishType)}`}>
                  {getPublishTypeDisplay()}
                </span>
              </div>
            </div>

            {/* Divider */}
            <div className="sm:col-span-2 lg:col-span-3 border-t border-dashed border-border/60" />

            {/* Operator */}
            <div className="flex items-start gap-3">
              <User className="h-4 w-4 text-muted-foreground mt-0.5 shrink-0" />
              <div className="min-w-0">
                <div className="text-xs font-medium text-muted-foreground mb-0.5">{t('history.operator')}</div>
                <div className="text-sm">{currentHistory.srcUser || '-'}</div>
              </div>
            </div>

            {/* Source IP */}
            <div className="flex items-start gap-3">
              <Globe className="h-4 w-4 text-muted-foreground mt-0.5 shrink-0" />
              <div className="min-w-0">
                <div className="text-xs font-medium text-muted-foreground mb-0.5">{t('history.sourceIp')}</div>
                <div className="text-sm font-mono text-[13px] tracking-tight">{currentHistory.srcIp || '-'}</div>
              </div>
            </div>

            {/* MD5 */}
            <div className="flex items-start gap-3">
              <Hash className="h-4 w-4 text-muted-foreground mt-0.5 shrink-0" />
              <div className="min-w-0 overflow-hidden">
                <div className="text-xs font-medium text-muted-foreground mb-0.5">{t('config.md5')}</div>
                <div className="text-sm">
                  <CopyableText text={currentHistory.md5} field="md5" mono />
                </div>
              </div>
            </div>

            {/* Modified Time */}
            {currentHistory.modifyTime && (
              <div className="flex items-start gap-3">
                <Clock className="h-4 w-4 text-muted-foreground mt-0.5 shrink-0" />
                <div className="min-w-0">
                  <div className="text-xs font-medium text-muted-foreground mb-0.5">{t('config.modifyTime')}</div>
                  <div className="text-sm tabular-nums">{formatTime(currentHistory.modifyTime)}</div>
                </div>
              </div>
            )}

            {/* Gray Rule — conditional, spans full width */}
            {grayRule && (
              <>
                <div className="sm:col-span-2 lg:col-span-3 border-t border-dashed border-border/60" />
                <div className="flex items-start gap-3 sm:col-span-2 lg:col-span-3">
                  <Zap className="h-4 w-4 text-muted-foreground mt-0.5 shrink-0" />
                  <div className="min-w-0">
                    <div className="text-xs font-medium text-muted-foreground mb-0.5">{t('history.grayRule')}</div>
                    <div className="text-sm font-mono text-[13px] tracking-tight break-all">{grayRule}</div>
                  </div>
                </div>
              </>
            )}
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
            value={currentHistory.content || ''}
            language={(currentHistory.type as ConfigType) || 'text'}
            readOnly
            height="350px"
          />
        </CardContent>
      </Card>
    </div>
  );
}
