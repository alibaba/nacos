import { useState, useEffect, useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Search, RotateCcw, ChevronLeft, ChevronRight } from 'lucide-react';

import { serviceApi } from '@/api/service';
import { useNamespaceStore } from '@/stores/namespace-store';
import type { SubscriberInfo, SubscriberListResponse } from '@/types/service';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent } from '@/components/ui/card';
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
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';

export default function SubscriberListPage() {
  const { t } = useTranslation();
  const [searchParams] = useSearchParams();
  const { currentNamespace } = useNamespaceStore();
  const activeNamespace =
    searchParams.get('namespace') || searchParams.get('namespaceId') || currentNamespace;

  // Search
  const [serviceName, setServiceName] = useState(searchParams.get('serviceName') || '');
  const [groupName, setGroupName] = useState(searchParams.get('groupName') || '');

  // Data
  const [subscribers, setSubscribers] = useState<SubscriberInfo[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  const fetchSubscribers = useCallback(async () => {
    if (!serviceName.trim()) return;
    setLoading(true);
    try {
      const response = await serviceApi.listSubscribers({
        namespaceId: activeNamespace,
        serviceName: serviceName.trim(),
        groupName: groupName.trim() || undefined,
        pageNo,
        pageSize,
      });
      const result = response as unknown as { data: SubscriberListResponse };
      const data = result.data;
      setSubscribers(data.pageItems || []);
      setTotal(data.totalCount || 0);
    } catch {
      setSubscribers([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [activeNamespace, groupName, pageNo, pageSize, serviceName]);

  useEffect(() => {
    if (serviceName.trim()) {
      fetchSubscribers();
    }
    // Keep search input edits manual; refetch automatically only when namespace or pagination changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeNamespace, pageNo, pageSize]);

  const handleSearch = () => {
    if (pageNo === 1) {
      fetchSubscribers();
      return;
    }
    setPageNo(1);
  };

  const handleReset = () => {
    setServiceName('');
    setGroupName('');
    setSubscribers([]);
    setTotal(0);
    setPageNo(1);
  };

  const totalPages = Math.max(1, Math.ceil(total / pageSize));

  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-2xl font-semibold text-foreground">{t('service.subscriberTitle')}</h1>

      {/* Search */}
      <Card className="py-0">
        <CardContent className="py-4">
          <div className="flex flex-wrap items-end gap-4">
            <div className="flex flex-col gap-2">
              <label className="text-sm font-medium text-muted-foreground">
                {t('service.serviceName')}
              </label>
              <Input
                placeholder={t('service.serviceName')}
                value={serviceName}
                onChange={(e) => setServiceName(e.target.value)}
                className="w-[200px]"
                onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
              />
            </div>
            <div className="flex flex-col gap-2">
              <label className="text-sm font-medium text-muted-foreground">
                {t('service.groupName')}
              </label>
              <Input
                placeholder={t('service.groupName')}
                value={groupName}
                onChange={(e) => setGroupName(e.target.value)}
                className="w-[200px]"
                onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
              />
            </div>
            <Button onClick={handleSearch} className="gap-2">
              <Search className="h-4 w-4" />
              {t('common.search')}
            </Button>
            <Button variant="outline" onClick={handleReset} className="gap-2">
              <RotateCcw className="h-4 w-4" />
              {t('common.reset')}
            </Button>
          </div>
        </CardContent>
      </Card>

      {/* Table */}
      <Card className="py-0">
        <CardContent className="p-0">
          {loading && subscribers.length === 0 ? (
            <div className="p-6 space-y-4">
              {Array.from({ length: 5 }).map((_, i) => (
                <Skeleton key={i} className="h-12 w-full" />
              ))}
            </div>
          ) : subscribers.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-16 text-muted-foreground">
              <p className="text-lg">{t('common.noData')}</p>
            </div>
          ) : (
            <Table className={loading ? 'opacity-50 pointer-events-none' : ''}>
              <TableHeader>
                <TableRow>
                  <TableHead className="pl-6">{t('service.groupName')}</TableHead>
                  <TableHead>{t('service.serviceName')}</TableHead>
                  <TableHead>{t('service.subscriberName')}</TableHead>
                  <TableHead>{t('service.subscribeCount')}</TableHead>
                  <TableHead className="pr-6">{t('service.clusters')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {subscribers.map((sub, idx) => (
                  <TableRow key={idx}>
                    <TableCell className="pl-6">{sub.groupName}</TableCell>
                    <TableCell>{sub.serviceName}</TableCell>
                    <TableCell className="font-medium">{sub.subscriberName}</TableCell>
                    <TableCell>{sub.subscribeCount}</TableCell>
                    <TableCell className="pr-6">{sub.clusters || '-'}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {/* Pagination */}
      {total > 0 && (
        <div className="flex items-center justify-between">
          <div className="text-sm text-muted-foreground">
            {t('service.totalSubscribers', { total })}
          </div>
          <div className="flex items-center gap-4">
            <div className="flex items-center gap-2">
              <span className="text-sm text-muted-foreground">{t('common.pageSize')}</span>
              <Select
                value={pageSize.toString()}
                onValueChange={(v) => {
                  setPageSize(parseInt(v, 10));
                  setPageNo(1);
                }}
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
                onClick={() => setPageNo(pageNo - 1)}
                disabled={pageNo <= 1}
              >
                <ChevronLeft className="h-4 w-4" />
              </Button>
              <span className="text-sm text-muted-foreground min-w-[80px] text-center">
                {pageNo} / {totalPages}
              </span>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPageNo(pageNo + 1)}
                disabled={pageNo >= totalPages}
              >
                <ChevronRight className="h-4 w-4" />
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
