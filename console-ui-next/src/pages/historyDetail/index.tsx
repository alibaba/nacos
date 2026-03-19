import { useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ArrowLeft } from 'lucide-react';

import { useHistoryStore } from '@/stores/history-store';
import { MonacoEditor } from '@/components/config/MonacoEditor';
import type { ConfigType } from '@/types/config';

import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
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

  useEffect(() => {
    if (nid && dataId && group) {
      fetchHistoryDetail(nid, dataId, group);
    }
    return () => {
      clearCurrentHistory();
    };
  }, [nid, dataId, group]);

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

  const getPublishTypeDisplay = () => {
    if (!currentHistory) return '-';
    if (currentHistory.publishType === 'gray') {
      return t('history.gray');
    }
    return t('history.formal');
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
      <Card>
        <CardHeader>
          <CardTitle>{t('history.detailTitle')}</CardTitle>
        </CardHeader>
        <CardContent className="p-6 pt-0">
          <div className="grid grid-cols-3 gap-6">
            {renderMetadataItem(t('common.selectNamespace'), namespace || 'public')}
            {renderMetadataItem(t('config.dataId'), currentHistory.dataId)}
            {renderMetadataItem(t('config.group'), currentHistory.groupName)}
            {renderMetadataItem(t('config.appName'), currentHistory.appName)}
            {renderMetadataItem(
              t('history.publishType'),
              <Badge variant={currentHistory.publishType === 'gray' ? 'secondary' : 'default'}>
                {getPublishTypeDisplay()}
              </Badge>
            )}
            {renderMetadataItem(t('history.operator'), currentHistory.srcUser)}
            {renderMetadataItem(t('history.sourceIp'), currentHistory.srcIp)}
            {renderMetadataItem(
              t('history.opType'),
              getOpTypeLabel(currentHistory.opType)
            )}
            {renderMetadataItem(t('config.md5'), currentHistory.md5)}
          </div>
          {grayRule && (
            <div className="mt-4">
              {renderMetadataItem(t('history.grayRule'), grayRule)}
            </div>
          )}
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
