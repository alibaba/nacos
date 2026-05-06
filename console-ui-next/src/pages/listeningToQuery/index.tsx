import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Search, RotateCcw, ChevronLeft, ChevronRight } from 'lucide-react';

import { configApi } from '@/api/config';
import { useNamespaceStore } from '@/stores/namespace-store';
import type { ConfigListenerInfo } from '@/types/config';

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
import {
  ToggleGroup,
  ToggleGroupItem,
} from '@/components/ui/toggle-group';

interface ListenerRow {
  ip?: string;
  dataId?: string;
  group?: string;
  md5: string;
}

export default function ListeningToQueryPage() {
  const { t } = useTranslation();
  const [searchParams] = useSearchParams();
  const { currentNamespace } = useNamespaceStore();

  // Query mode: 'config' or 'ip'
  const [queryMode, setQueryMode] = useState<'config' | 'ip'>(
    searchParams.get('ip') ? 'ip' : 'config'
  );

  // Search inputs
  const [localDataId, setLocalDataId] = useState(searchParams.get('dataId') || '');
  const [localGroup, setLocalGroup] = useState(searchParams.get('group') || '');
  const [localIp, setLocalIp] = useState(searchParams.get('ip') || '');

  // Results
  const [loading, setLoading] = useState(false);
  const [allRows, setAllRows] = useState<ListenerRow[]>([]);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  const handleSearch = async () => {
    setLoading(true);
    setAllRows([]);
    setPageNo(1);

    try {
      if (queryMode === 'config') {
        if (!localDataId.trim() || !localGroup.trim()) {
          setLoading(false);
          return;
        }
        const response = await configApi.listenersByConfig({
          dataId: localDataId.trim(),
          groupName: localGroup.trim(),
          namespaceId: currentNamespace,
        });
        const result = response as unknown as { data: ConfigListenerInfo };
        const status = result.data?.listenersStatus || {};
        const rows: ListenerRow[] = Object.entries(status).map(([ip, md5]) => ({
          ip,
          md5,
        }));
        setAllRows(rows);
      } else {
        if (!localIp.trim()) {
          setLoading(false);
          return;
        }
        const response = await configApi.listenersByIp({
          ip: localIp.trim(),
          namespaceId: currentNamespace,
        });
        const result = response as unknown as { data: ConfigListenerInfo };
        const status = result.data?.listenersStatus || {};
        const rows: ListenerRow[] = Object.entries(status).map(([key, md5]) => {
          const plusIndex = key.indexOf('+');
          if (plusIndex > -1) {
            return {
              dataId: key.substring(0, plusIndex),
              group: key.substring(plusIndex + 1),
              md5,
            };
          }
          return { dataId: key, group: '', md5 };
        });
        setAllRows(rows);
      }
    } catch {
      // Error toast handled by interceptor
    } finally {
      setLoading(false);
    }
  };

  // Client-side pagination
  const totalPages = Math.ceil(allRows.length / pageSize);
  const paginatedRows = allRows.slice((pageNo - 1) * pageSize, pageNo * pageSize);

  return (
    <div className="flex flex-col gap-4">
      {/* Header */}
      <h1 className="text-2xl font-semibold text-foreground">{t('listener.title')}</h1>

      {/* Search Area */}
      <Card className="py-0">
        <CardContent className="py-4">
          <div className="flex flex-col gap-4">
            {/* Query mode toggle */}
            <div className="flex items-center gap-4">
              <ToggleGroup
                type="single"
                value={queryMode}
                onValueChange={(v) => {
                  if (v) {
                    setQueryMode(v as 'config' | 'ip');
                    setAllRows([]);
                  }
                }}
              >
                <ToggleGroupItem value="config" className="px-4">
                  {t('listener.queryByConfig')}
                </ToggleGroupItem>
                <ToggleGroupItem value="ip" className="px-4">
                  {t('listener.queryByIp')}
                </ToggleGroupItem>
              </ToggleGroup>
            </div>

            {/* Search inputs */}
            <div className="flex flex-wrap items-end gap-4">
              {queryMode === 'config' ? (
                <>
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
                </>
              ) : (
                <div className="flex flex-col gap-2">
                  <label className="text-sm font-medium text-muted-foreground">
                    {t('listener.ip')}
                  </label>
                  <Input
                    placeholder={t('listener.ip')}
                    value={localIp}
                    onChange={(e) => setLocalIp(e.target.value)}
                    className="w-[200px]"
                    onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
                  />
                </div>
              )}
              <Button onClick={handleSearch} className="gap-2">
                <Search className="h-4 w-4" />
                {t('common.search')}
              </Button>
              <Button
                variant="outline"
                onClick={() => {
                  setLocalDataId('');
                  setLocalGroup('');
                  setLocalIp('');
                  setAllRows([]);
                  setPageNo(1);
                }}
                className="gap-2"
              >
                <RotateCcw className="h-4 w-4" />
                {t('common.reset')}
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Results Table */}
      <Card className="py-0">
        <CardContent className="p-0">
          {loading ? (
            <div className="p-6 space-y-4">
              {Array.from({ length: 5 }).map((_, i) => (
                <Skeleton key={i} className="h-12 w-full" />
              ))}
            </div>
          ) : allRows.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-16 text-muted-foreground">
              <p className="text-lg">{t('listener.noListeners')}</p>
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  {queryMode === 'config' ? (
                    <>
                      <TableHead className="pl-6">{t('listener.ip')}</TableHead>
                      <TableHead className="pr-6">{t('listener.md5')}</TableHead>
                    </>
                  ) : (
                    <>
                      <TableHead className="pl-6">{t('config.dataId')}</TableHead>
                      <TableHead>{t('config.group')}</TableHead>
                      <TableHead className="pr-6">{t('listener.md5')}</TableHead>
                    </>
                  )}
                </TableRow>
              </TableHeader>
              <TableBody>
                {paginatedRows.map((row, idx) => (
                  <TableRow key={idx}>
                    {queryMode === 'config' ? (
                      <>
                        <TableCell className="pl-6 font-medium">{row.ip}</TableCell>
                        <TableCell className="pr-6 font-mono text-sm">{row.md5}</TableCell>
                      </>
                    ) : (
                      <>
                        <TableCell className="pl-6 font-medium">{row.dataId}</TableCell>
                        <TableCell>{row.group}</TableCell>
                        <TableCell className="pr-6 font-mono text-sm">{row.md5}</TableCell>
                      </>
                    )}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {/* Pagination */}
      {allRows.length > 0 && (
        <div className="flex items-center justify-between">
          <div className="text-sm text-muted-foreground">
            {t('listener.totalListeners', { total: allRows.length })}
          </div>
          <div className="flex items-center gap-4">
            <div className="flex items-center gap-2">
              <span className="text-sm text-muted-foreground">{t('common.pageSize')}</span>
              <Select
                value={pageSize.toString()}
                onValueChange={(v) => { setPageSize(parseInt(v, 10)); setPageNo(1); }}
              >
                <SelectTrigger className="w-[80px]">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="10">10</SelectItem>
                  <SelectItem value="20">20</SelectItem>
                  <SelectItem value="50">50</SelectItem>
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
                {pageNo} / {totalPages || 1}
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
