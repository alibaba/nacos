import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { ArrowLeft, Pencil, History, Trash2 } from 'lucide-react';

import { useConfigStore } from '@/stores/config-store';
import { configApi } from '@/api/config';
import { MonacoEditor } from '@/components/config/MonacoEditor';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
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
      const response = await configApi.delete({
        dataId,
        groupName: group,
        namespaceId: namespace,
      });
      if (response.data?.code === 0) {
        toast.success(t('config.deleteSuccess'));
        navigate('/configurationManagement');
      } else {
        toast.error(response.data?.message || t('common.failed'));
      }
    } catch (error) {
      toast.error(t('common.failed'));
    } finally {
      setDeleting(false);
      setDeleteDialogOpen(false);
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
      <Card>
        <CardHeader>
          <CardTitle>{t('config.configDetail')}</CardTitle>
        </CardHeader>
        <CardContent className="p-6 pt-0">
          <div className="grid grid-cols-3 gap-6">
            {renderMetadataItem(t('config.dataId'), currentConfig.dataId)}
            {renderMetadataItem(t('config.group'), currentConfig.groupName)}
            {renderMetadataItem(t('config.md5'), currentConfig.md5)}
            {renderMetadataItem(t('config.appName'), currentConfig.appName)}
            {renderMetadataItem(
              t('config.type'),
              <Badge variant="secondary">{currentConfig.type?.toUpperCase()}</Badge>
            )}
            {renderMetadataItem(t('config.tags'), currentConfig.configTags)}
            {renderMetadataItem(t('config.description'), currentConfig.desc)}
            {renderMetadataItem(t('config.createTime'), currentConfig.createTime)}
            {renderMetadataItem(t('config.modifyTime'), currentConfig.modifyTime)}
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
