import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ArrowLeft, ChevronLeft, ChevronRight, Search, RotateCcw } from 'lucide-react';

import { useHistoryStore } from '@/stores/history-store';
import { useNamespaceStore } from '@/stores/namespace-store';
import { configApi } from '@/api/config';
import { DiffEditor } from '@/components/config/DiffEditor';
import type { ConfigHistory, ConfigType } from '@/types/config';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';

export default function HistoryRollbackPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();

  const { currentNamespace } = useNamespaceStore();

  // URL params (may come from config list or be empty from sidebar)
  const urlDataId = searchParams.get('dataId') || '';
  const urlGroup = searchParams.get('group') || '';
  const urlNamespace = searchParams.get('namespace') || '';

  // Use URL namespace if provided, otherwise use global namespace
  const activeNamespace = urlNamespace || currentNamespace;

  // Search inputs
  const [localDataId, setLocalDataId] = useState(urlDataId);
  const [localGroup, setLocalGroup] = useState(urlGroup);
  // Track the "committed" search values (what was last searched)
  const [searchedDataId, setSearchedDataId] = useState(urlDataId);
  const [searchedGroup, setSearchedGroup] = useState(urlGroup);

  const {
    historyList,
    loading,
    total,
    pageNo,
    pageSize,
    fetchHistoryList,
    setPage,
  } = useHistoryStore();

  // Diff dialog state
  const [diffOpen, setDiffOpen] = useState(false);
  const [diffLoading, setDiffLoading] = useState(false);
  const [selectedContent, setSelectedContent] = useState('');
  const [currentContent, setCurrentContent] = useState('');
  const [diffLanguage, setDiffLanguage] = useState<ConfigType>('text');

  // Auto-search on mount if URL params exist
  useEffect(() => {
    if (urlDataId && urlGroup) {
      setLocalDataId(urlDataId);
      setLocalGroup(urlGroup);
      setSearchedDataId(urlDataId);
      setSearchedGroup(urlGroup);
    }
  }, []);

  // Fetch history list when search params or page changes
  useEffect(() => {
    if (searchedDataId && searchedGroup) {
      fetchHistoryList(searchedDataId, searchedGroup, activeNamespace);
    }
  }, [searchedDataId, searchedGroup, activeNamespace, pageNo, pageSize]);

  const handleSearch = () => {
    if (!localDataId || !localGroup) return;
    setSearchedDataId(localDataId);
    setSearchedGroup(localGroup);
    setPage(1);
    // Update URL to reflect current search
    const params = new URLSearchParams();
    params.set('dataId', localDataId);
    params.set('group', localGroup);
    if (activeNamespace) params.set('namespace', activeNamespace);
    setSearchParams(params, { replace: true });
  };

  const handleReset = () => {
    setLocalDataId('');
    setLocalGroup('');
    setSearchedDataId('');
    setSearchedGroup('');
    setSearchParams({}, { replace: true });
  };

  const handleBack = () => {
    navigate('/configurationManagement');
  };

  const handleDetail = (record: ConfigHistory) => {
    navigate(
      `/historyDetail?dataId=${encodeURIComponent(record.dataId)}&group=${encodeURIComponent(record.groupName)}&nid=${encodeURIComponent(record.id)}&namespace=${encodeURIComponent(activeNamespace)}`
    );
  };

  const handleRollback = (record: ConfigHistory) => {
    navigate(
      `/configRollback?dataId=${encodeURIComponent(record.dataId)}&group=${encodeURIComponent(record.groupName)}&nid=${encodeURIComponent(record.id)}&namespace=${encodeURIComponent(activeNamespace)}`
    );
  };

  const handleCompare = async (record: ConfigHistory) => {
    setDiffLoading(true);
    setDiffOpen(true);
    setSelectedContent(record.content || '');
    setDiffLanguage(record.type || 'text');

    try {
      const response = await configApi.get({
        dataId: record.dataId,
        groupName: record.groupName,
        namespaceId: activeNamespace,
      });
      const result = response as unknown as { data: { content: string } };
      setCurrentContent(result.data?.content || '');
    } catch {
      setCurrentContent('');
    } finally {
      setDiffLoading(false);
    }
  };

  const getPublishTypeBadge = (record: ConfigHistory) => {
    if (record.publishType === 'gray') {
      let grayName = '';
      try {
        const extInfo = JSON.parse(record.extInfo || '{}');
        grayName = extInfo.gray_name || '';
      } catch { /* ignore */ }
      return (
        <Badge variant="secondary">
          {t('history.gray')}{grayName ? ` (${grayName})` : ''}
        </Badge>
      );
    }
    return <Badge>{t('history.formal')}</Badge>;
  };

  const formatTime = (time: string) => {
    if (!time) return '-';
    const ts = Number(time);
    if (!isNaN(ts) && ts > 0) {
      return new Date(ts).toLocaleString();
    }
    return time;
  };

  const totalPages = Math.ceil(total / pageSize);
  const hasSearched = !!(searchedDataId && searchedGroup);

  return (
    <div className="flex flex-col gap-4">
      {/* Header */}
      <div className="flex items-center gap-4">
        <Button variant="outline" size="icon" onClick={handleBack}>
          <ArrowLeft className="h-4 w-4" />
        </Button>
        <h1 className="text-2xl font-semibold text-foreground">{t('history.title')}</h1>
      </div>

      {/* Search Area */}
      <Card className="py-0">
        <CardContent className="py-4">
          <div className="flex flex-col gap-4">
            <div className="flex flex-wrap items-end gap-4">
              <div className="flex flex-col gap-2">
                <label className="text-sm font-medium text-muted-foreground">
                  {t('config.dataId')}
                </label>
                <Input
                  placeholder={t('config.dataId')}
                  value={localDataId}
                  onChange={(e) => setLocalDataId(e.target.value)}
                  className="w-[200px]"
                  onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
                />
              </div>
              <div className="flex flex-col gap-2">
                <label className="text-sm font-medium text-muted-foreground">
                  {t('config.group')}
                </label>
                <Input
                  placeholder={t('config.group')}
                  value={localGroup}
                  onChange={(e) => setLocalGroup(e.target.value)}
                  className="w-[200px]"
                  onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
                />
              </div>
            </div>
            <div className="flex gap-2">
              <Button onClick={handleSearch} className="gap-2" disabled={!localDataId || !localGroup}>
                <Search className="h-4 w-4" />
                {t('common.search')}
              </Button>
              <Button variant="outline" onClick={handleReset} className="gap-2">
                <RotateCcw className="h-4 w-4" />
                {t('common.reset')}
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Table */}
      <Card className="py-0">
        <CardContent className="p-0">
          {loading && historyList.length === 0 ? (
            <div className="p-6 space-y-4">
              {Array.from({ length: 5 }).map((_, i) => (
                <Skeleton key={i} className="h-12 w-full" />
              ))}
            </div>
          ) : !hasSearched && historyList.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-16 text-muted-foreground">
              <p className="text-lg">{t('common.noData')}</p>
            </div>
          ) : historyList.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-16 text-muted-foreground">
              <p className="text-lg">{t('common.noData')}</p>
            </div>
          ) : (
            <div className={loading ? 'opacity-50 pointer-events-none transition-opacity' : 'transition-opacity'}>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="pl-6">{t('config.dataId')}</TableHead>
                  <TableHead>{t('config.group')}</TableHead>
                  <TableHead>{t('history.publishType')}</TableHead>
                  <TableHead>{t('history.operator')}</TableHead>
                  <TableHead>{t('config.modifyTime')}</TableHead>
                  <TableHead className="text-right pr-6">{t('common.operation')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {historyList.map((record) => (
                  <TableRow key={record.id}>
                    <TableCell className="font-medium max-w-[200px] truncate pl-6" title={record.dataId}>
                      {record.dataId}
                    </TableCell>
                    <TableCell className="max-w-[150px] truncate" title={record.groupName}>
                      {record.groupName}
                    </TableCell>
                    <TableCell>
                      {getPublishTypeBadge(record)}
                    </TableCell>
                    <TableCell>{record.srcUser || '-'}</TableCell>
                    <TableCell>{formatTime(record.modifyTime)}</TableCell>
                    <TableCell className="text-right pr-6">
                      <div className="flex items-center justify-end gap-2">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => handleDetail(record)}
                        >
                          {t('common.detail')}
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => handleRollback(record)}
                          disabled={record.publishType === 'gray'}
                        >
                          {t('history.rollback')}
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => handleCompare(record)}
                        >
                          {t('history.compare')}
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Pagination */}
      {total > 0 && (
        <div className="flex items-center justify-between">
          <div className="text-sm text-muted-foreground">
            {t('history.totalHistory', { total })}
          </div>
          <div className="flex items-center gap-4">
            <div className="flex items-center gap-2">
              <span className="text-sm text-muted-foreground">{t('common.pageSize')}</span>
              <Select
                value={pageSize.toString()}
                onValueChange={(value) => setPage(1, parseInt(value, 10))}
              >
                <SelectTrigger className="w-[80px]">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="10">10</SelectItem>
                  <SelectItem value="20">20</SelectItem>
                  <SelectItem value="50">50</SelectItem>
                  <SelectItem value="100">100</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="flex items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage(pageNo - 1)}
                disabled={pageNo <= 1}
              >
                <ChevronLeft className="h-4 w-4" />
              </Button>
              <span className="text-sm text-muted-foreground min-w-[80px] text-center">
                {pageNo} / {totalPages || 1}
              </span>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage(pageNo + 1)}
                disabled={pageNo >= totalPages}
              >
                <ChevronRight className="h-4 w-4" />
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* Diff Comparison Dialog */}
      <Dialog open={diffOpen} onOpenChange={setDiffOpen}>
        <DialogContent className="max-w-[90vw] w-[90vw]">
          <DialogHeader>
            <DialogTitle>{t('history.compare')}</DialogTitle>
          </DialogHeader>
          <div className="flex justify-between text-sm text-muted-foreground mb-2">
            <span>{t('history.selectedVersion')}</span>
            <span>{t('history.currentVersion')}</span>
          </div>
          {diffLoading ? (
            <div className="flex items-center justify-center h-[400px]">
              <Skeleton className="h-full w-full" />
            </div>
          ) : (
            <DiffEditor
              original={selectedContent}
              modified={currentContent}
              language={diffLanguage}
              height="500px"
            />
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
