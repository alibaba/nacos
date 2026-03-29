import { useEffect, useState, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import {
  Search, RotateCcw, ChevronLeft, ChevronRight, Network,
} from 'lucide-react';

import { clusterApi } from '@/api/cluster';
import type { ClusterNode } from '@/api/cluster';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table';
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select';

export default function ClusterManagementPage() {
  const { t } = useTranslation();

  const [allNodes, setAllNodes] = useState<ClusterNode[]>([]);
  const [loading, setLoading] = useState(true);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [localKeyword, setLocalKeyword] = useState('');

  // Leave dialog
  const [leaveOpen, setLeaveOpen] = useState(false);
  const [nodeToLeave, setNodeToLeave] = useState<ClusterNode | null>(null);

  const fetchNodes = useCallback(async () => {
    setLoading(true);
    try {
      const response = await clusterApi.list({
        keyword: keyword || undefined,
      });
      // Backend returns Result<Collection<NacosMember>> — data is a flat array
      const members = response.data || [];
      setAllNodes(Array.isArray(members) ? members : []);
    } catch {
      setAllNodes([]);
    } finally {
      setLoading(false);
    }
  }, [keyword]);

  useEffect(() => {
    fetchNodes();
  }, [fetchNodes]);

  // Client-side pagination (backend doesn't support pagination)
  const total = allNodes.length;
  const totalPages = Math.ceil(total / pageSize);
  const nodes = allNodes.slice((pageNo - 1) * pageSize, pageNo * pageSize);

  const handleSearch = () => {
    setKeyword(localKeyword);
    setPageNo(1);
  };

  const handleReset = () => {
    setLocalKeyword('');
    setKeyword('');
    setPageNo(1);
  };

  // Reset to page 1 when data changes
  useEffect(() => {
    setPageNo(1);
  }, [allNodes]);

  const handleLeave = async () => {
    if (!nodeToLeave) return;
    const address = nodeToLeave.address || `${nodeToLeave.ip}:${nodeToLeave.port}`;
    try {
      await clusterApi.leave([address]);
      toast.success(t('cluster.leaveSuccess'));
      setLeaveOpen(false);
      setNodeToLeave(null);
      fetchNodes();
    } catch {
      // Error handled by interceptor
    }
  };

  const getStateBadge = (state: string) => {
    const s = state?.toUpperCase();
    if (s === 'UP') return <Badge className="bg-emerald-500/15 text-emerald-600 border-emerald-200 hover:bg-emerald-500/15">{t('cluster.stateUp')}</Badge>;
    if (s === 'DOWN') return <Badge variant="destructive">{t('cluster.stateDown')}</Badge>;
    if (s === 'SUSPICIOUS') return <Badge className="bg-amber-500/15 text-amber-600 border-amber-200 hover:bg-amber-500/15">{t('cluster.stateSuspicious')}</Badge>;
    return <Badge variant="secondary">{state}</Badge>;
  };

  return (
    <div className="flex flex-col gap-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-foreground">{t('cluster.title')}</h1>
        <Button variant="outline" onClick={fetchNodes} className="gap-2">
          <RotateCcw className="h-4 w-4" />
          {t('cluster.refresh')}
        </Button>
      </div>

      {/* Search */}
      <Card className="py-0">
        <CardContent className="py-4">
          <div className="flex items-end gap-4">
            <div className="flex flex-col gap-2">
              <label className="text-sm font-medium text-muted-foreground">
                {t('cluster.nodeAddress')}
              </label>
              <Input
                placeholder={t('cluster.searchPlaceholder')}
                value={localKeyword}
                onChange={(e) => setLocalKeyword(e.target.value)}
                className="w-[300px]"
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
          {loading && allNodes.length === 0 ? (
            <div className="p-6 space-y-4">
              {Array.from({ length: 4 }).map((_, i) => (
                <Skeleton key={i} className="h-12 w-full" />
              ))}
            </div>
          ) : nodes.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-16 text-muted-foreground">
              <Network className="h-10 w-10 mb-3 opacity-40" />
              <p className="text-lg">{t('common.noData')}</p>
            </div>
          ) : (
            <div className={loading ? 'opacity-50 pointer-events-none transition-opacity' : 'transition-opacity'}>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="pl-6">{t('cluster.nodeAddress')}</TableHead>
                    <TableHead>{t('cluster.nodeIp')}</TableHead>
                    <TableHead>{t('cluster.nodePort')}</TableHead>
                    <TableHead>{t('cluster.nodeState')}</TableHead>
                    <TableHead className="text-right pr-6">{t('common.operation')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {nodes.map((node) => {
                    const address = node.address || `${node.ip}:${node.port}`;
                    return (
                      <TableRow key={address}>
                        <TableCell className="pl-6 font-medium">
                          <code className="text-xs bg-muted px-1.5 py-0.5 rounded font-mono">
                            {address}
                          </code>
                        </TableCell>
                        <TableCell>{node.ip}</TableCell>
                        <TableCell>{node.port}</TableCell>
                        <TableCell>{getStateBadge(node.state)}</TableCell>
                        <TableCell className="text-right pr-6">
                          <Button
                            variant="ghost"
                            size="sm"
                            className="text-destructive hover:text-destructive"
                            onClick={() => { setNodeToLeave(node); setLeaveOpen(true); }}
                          >
                            {t('cluster.leave')}
                          </Button>
                        </TableCell>
                      </TableRow>
                    );
                  })}
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
            {t('cluster.total', { total })}
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
                variant="outline" size="sm"
                onClick={() => setPageNo(pageNo - 1)}
                disabled={pageNo <= 1}
              >
                <ChevronLeft className="h-4 w-4" />
              </Button>
              <span className="text-sm text-muted-foreground min-w-[80px] text-center">
                {pageNo} / {totalPages || 1}
              </span>
              <Button
                variant="outline" size="sm"
                onClick={() => setPageNo(pageNo + 1)}
                disabled={pageNo >= totalPages}
              >
                <ChevronRight className="h-4 w-4" />
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* Leave Confirmation */}
      <Dialog open={leaveOpen} onOpenChange={setLeaveOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('cluster.leave')}</DialogTitle>
            <DialogDescription>
              {t('cluster.leaveConfirm', { address: nodeToLeave?.address || `${nodeToLeave?.ip}:${nodeToLeave?.port}` })}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setLeaveOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button variant="destructive" onClick={handleLeave}>
              {t('common.confirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
