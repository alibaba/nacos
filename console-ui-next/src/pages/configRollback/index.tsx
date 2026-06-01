import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { ArrowLeft, AlertTriangle } from 'lucide-react';

import { useHistoryStore } from '@/stores/history-store';
import { configApi } from '@/api/config';
import { MonacoEditor } from '@/components/config/MonacoEditor';
import type { ConfigType } from '@/types/config';

import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';

export default function ConfigRollbackPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const dataId = searchParams.get('dataId') || '';
  const group = searchParams.get('group') || '';
  const nid = searchParams.get('nid') || '';
  const namespace = searchParams.get('namespace') || '';

  const { currentHistory, detailLoading, fetchHistoryDetail, clearCurrentHistory } =
    useHistoryStore();

  const [confirmOpen, setConfirmOpen] = useState(false);
  const [rollbackLoading, setRollbackLoading] = useState(false);

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

  const getRollbackWarning = () => {
    if (!currentHistory) return '';
    const trimmed = currentHistory.opType?.trim();
    switch (trimmed) {
      case 'I': return t('history.rollbackInsertWarn');
      case 'U': return t('history.rollbackUpdateWarn');
      case 'D': return t('history.rollbackDeleteWarn');
      default: return '';
    }
  };

  const handleRollback = async () => {
    if (!currentHistory) return;

    setRollbackLoading(true);
    try {
      const opType = currentHistory.opType?.trim();

      if (opType === 'I') {
        // Rollback insert = delete the config
        await configApi.delete({
          dataId: currentHistory.dataId,
          groupName: currentHistory.groupName,
          namespaceId: namespace,
        });
      } else {
        // Rollback update/delete = publish with history content
        // Extract extra info from extInfo if available
        let type: ConfigType = currentHistory.type || 'text';
        let desc = '';
        let configTags = '';
        try {
          const extInfo = JSON.parse(currentHistory.extInfo || '{}');
          if (extInfo.type) type = extInfo.type;
          if (extInfo.config_tags) configTags = extInfo.config_tags;
          if (extInfo.c_desc) desc = extInfo.c_desc;
        } catch { /* ignore */ }

        await configApi.publish({
          dataId: currentHistory.dataId,
          groupName: currentHistory.groupName,
          content: currentHistory.content,
          namespaceId: namespace,
          type,
          appName: currentHistory.appName,
          desc: desc || undefined,
          configTags: configTags || undefined,
        });
      }

      toast.success(t('history.rollbackSuccess'));
      navigate('/configurationManagement');
    } catch {
      toast.error(t('history.rollbackFailed'));
    } finally {
      setRollbackLoading(false);
      setConfirmOpen(false);
    }
  };

  const renderMetadataItem = (label: string, value: React.ReactNode) => (
    <div className="flex flex-col gap-1">
      <span className="text-sm text-muted-foreground">{label}</span>
      <span className="text-sm font-medium">{value || '-'}</span>
    </div>
  );

  if (detailLoading) {
    return (
      <div className="flex flex-col gap-6">
        <div className="flex items-center gap-4">
          <Skeleton className="h-9 w-9" />
          <Skeleton className="h-8 w-48" />
        </div>
        <Skeleton className="h-24 w-full" />
        <Card>
          <CardContent className="p-6">
            <div className="grid grid-cols-3 gap-6">
              {Array.from({ length: 4 }).map((_, i) => (
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

  return (
    <div className="flex flex-col gap-6">
      {/* Header */}
      <div className="flex items-center gap-4">
        <Button variant="outline" size="icon" onClick={handleBack}>
          <ArrowLeft className="h-4 w-4" />
        </Button>
        <h1 className="text-2xl font-semibold">{t('history.rollbackTitle')}</h1>
      </div>

      {/* Warning */}
      <div className="flex items-center gap-3 rounded-lg border border-yellow-200 bg-yellow-50 p-4 dark:border-yellow-900 dark:bg-yellow-950">
        <AlertTriangle className="h-5 w-5 text-yellow-600 dark:text-yellow-400 shrink-0" />
        <p className="text-sm text-yellow-800 dark:text-yellow-200">
          {getRollbackWarning()}
        </p>
      </div>

      {/* Metadata Card */}
      <Card>
        <CardHeader>
          <CardTitle>{t('history.rollbackTitle')}</CardTitle>
        </CardHeader>
        <CardContent className="p-6 pt-0">
          <div className="grid grid-cols-3 gap-6">
            {renderMetadataItem(t('config.dataId'), currentHistory.dataId)}
            {renderMetadataItem(t('config.group'), currentHistory.groupName)}
            {renderMetadataItem(t('history.opType'), getOpTypeLabel(currentHistory.opType))}
            {renderMetadataItem(t('config.md5'), currentHistory.md5)}
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

      {/* Action Bar */}
      <div className="flex items-center gap-4">
        <Button variant="outline" onClick={handleBack}>
          {t('common.back')}
        </Button>
        <Button onClick={() => setConfirmOpen(true)}>
          {t('history.rollback')}
        </Button>
      </div>

      {/* Confirmation Dialog */}
      <Dialog open={confirmOpen} onOpenChange={setConfirmOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('history.rollback')}</DialogTitle>
            <DialogDescription>{t('history.rollbackConfirm')}</DialogDescription>
          </DialogHeader>
          <div className="text-sm space-y-1">
            <p>Data ID: <span className="font-medium">{currentHistory.dataId}</span></p>
            <p>Group: <span className="font-medium">{currentHistory.groupName}</span></p>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setConfirmOpen(false)} disabled={rollbackLoading}>
              {t('common.cancel')}
            </Button>
            <Button onClick={handleRollback} disabled={rollbackLoading}>
              {rollbackLoading ? t('common.loading') : t('common.confirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
