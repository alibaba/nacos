import { useEffect, useCallback, useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { Plus, Trash2, Search, X, ChevronLeft, ChevronRight, Package, Upload } from 'lucide-react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Skeleton } from '@/components/ui/skeleton';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Badge } from '@/components/ui/badge';
import { AgentSpecCard } from './components/AgentSpecCard';
import { UploadAgentSpecDialog } from './components/UploadAgentSpecDialog';
import { CreateAgentSpecDialog } from './components/CreateAgentSpecDialog';
import { useAgentSpecStore } from '@/stores/agentspec-store';
import { useNamespaceStore } from '@/stores/namespace-store';
import { useAuthStore } from '@/stores/auth-store';
import { agentSpecApi } from '@/api/agentspec';

export default function AgentSpecManagementPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const { currentNamespace } = useNamespaceStore();
  const { globalAdmin, username } = useAuthStore();
  const {
    items,
    loading,
    total,
    pageNo,
    pageSize,
    searchName,
    orderBy,
    filterOwner,
    filterScope,
    selectedNames,
    error,
    fetchList,
    setSearchParams,
    setPage,
    resetSearch,
    toggleSelect,
    selectAll,
    clearSelection,
  } = useAgentSpecStore();

  const [deleteTarget, setDeleteTarget] = useState<string | null>(null);
  const [batchDeleteOpen, setBatchDeleteOpen] = useState(false);
  const [deleteLoading, setDeleteLoading] = useState(false);
  const [searchInput, setSearchInput] = useState(searchName);
  const [ownerInput, setOwnerInput] = useState(filterOwner);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);

  const namespaceId = currentNamespace || 'public';

  const loadData = useCallback(() => {
    fetchList(namespaceId);
  }, [fetchList, namespaceId]);

  useEffect(() => {
    loadData();
  }, [loadData, pageNo, pageSize, location.key]);

  const handleSearch = () => {
    setSearchParams({
      searchName: searchInput,
      filterOwner: globalAdmin ? ownerInput : (ownerInput ? username || '' : ''),
    });
    fetchList(namespaceId);
  };

  const handleReset = () => {
    setSearchInput('');
    setOwnerInput('');
    resetSearch();
    fetchList(namespaceId);
  };

  const handleDetail = (name: string) => {
    const params = new URLSearchParams({ namespaceId });
    navigate(`/agentspec/${encodeURIComponent(name)}?${params}`);
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    setDeleteLoading(true);
    try {
      await agentSpecApi.delete({ namespaceId, agentSpecName: deleteTarget });
      toast.success(t('agentSpec.deleteSuccess'));
      setDeleteTarget(null);
      loadData();
    } catch {
      // error handled by axios interceptor
    } finally {
      setDeleteLoading(false);
    }
  };

  const handleBatchDelete = async () => {
    setDeleteLoading(true);
    try {
      const names = Array.from(selectedNames);
      await Promise.all(
        names.map((name) => agentSpecApi.delete({ namespaceId, agentSpecName: name })),
      );
      toast.success(t('agentSpec.batchDeleteSuccess'));
    } catch {
      // error handled by axios interceptor
    } finally {
      clearSelection();
      setBatchDeleteOpen(false);
      setDeleteLoading(false);
      loadData();
    }
  };

  const totalPages = Math.ceil(total / pageSize);
  const allSelected = items.length > 0 && items.every((a) => selectedNames.has(a.name));

  return (
    <div className="space-y-5">
      {/* Page header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold tracking-tight">
            {t('agentSpec.title')}
            <Badge className="ml-2 text-[10px] px-1.5 py-0 font-medium bg-amber-500 text-white border-0 hover:bg-amber-500 align-middle">
              Beta
            </Badge>
          </h1>
          <p className="text-sm text-muted-foreground mt-0.5">
            {t('agentSpec.totalAgentSpecs', { total })}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button size="sm" variant="outline" onClick={() => setUploadOpen(true)}>
            <Upload className="mr-1.5 h-3.5 w-3.5" />
            {t('agentSpec.upload')}
          </Button>
          <Button size="sm" onClick={() => setCreateOpen(true)}>
            <Plus className="mr-1.5 h-3.5 w-3.5" />
            {t('agentSpec.createAgentSpec')}
          </Button>
        </div>
      </div>

      {/* Search & filters bar */}
      <div className="flex flex-wrap items-center gap-2">
        <div className="relative flex-1 min-w-[220px] max-w-md">
          <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
          <Input
            placeholder={t('agentSpec.searchPlaceholder')}
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
            className="pl-8 h-8 text-sm"
          />
        </div>
        <Button size="sm" variant="secondary" className="h-8" onClick={handleSearch}>
          {t('common.search')}
        </Button>
        {(searchInput || filterOwner || filterScope) && (
          <Button size="sm" variant="ghost" className="h-8" onClick={handleReset}>
            <X className="mr-1 h-3 w-3" />
            {t('common.reset')}
          </Button>
        )}

        {/* Owner filter: admin gets free-text input; non-admin gets "only mine" toggle */}
        {globalAdmin ? (
          <Input
            placeholder={t('agentSpec.filterOwnerPlaceholder')}
            value={ownerInput}
            onChange={(e) => setOwnerInput(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
            className="w-[160px] h-8 text-xs"
            title={t('agentSpec.filterByOwner')}
          />
        ) : (
          <Button
            size="sm"
            variant={filterOwner ? 'default' : 'outline'}
            className="h-8 text-xs"
            onClick={() => {
              const next = filterOwner ? '' : (username || '');
              setSearchParams({ filterOwner: next });
              fetchList(namespaceId);
            }}
          >
            {t('agentSpec.filterOnlyMine')}
          </Button>
        )}

        {/* Scope filter: everyone can choose all / public / private */}
        <Select
          value={filterScope || ''}
          onValueChange={(v) => {
            setSearchParams({ filterScope: v === '_all' ? '' : v });
            fetchList(namespaceId);
          }}
        >
          <SelectTrigger className="w-[130px] h-8 text-xs">
            <SelectValue placeholder={t('agentSpec.filterScopeAll')} />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="_all">{t('agentSpec.filterScopeAll')}</SelectItem>
            <SelectItem value="PUBLIC">{t('agentSpec.filterScopePublic')}</SelectItem>
            <SelectItem value="PRIVATE">{t('agentSpec.filterScopePrivate')}</SelectItem>
          </SelectContent>
        </Select>

        {/* Sort */}
        <Select
          value={orderBy}
          onValueChange={(v) => {
            setSearchParams({ orderBy: v });
            fetchList(namespaceId);
          }}
        >
          <SelectTrigger className="w-[140px] h-8 text-xs">
            <SelectValue placeholder={t('agentSpec.sortDefault')} />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value=" ">{t('agentSpec.sortDefault')}</SelectItem>
            <SelectItem value="download_count">{t('agentSpec.sortByDownloads')}</SelectItem>
          </SelectContent>
        </Select>

        {/* Batch operations */}
        {selectedNames.size > 0 && (
          <div className="flex items-center gap-2 ml-auto">
            <span className="text-xs text-muted-foreground">
              {t('config.selectedCount', { count: selectedNames.size })}
            </span>
            <Button
              variant="destructive"
              size="sm"
              className="h-8"
              onClick={() => setBatchDeleteOpen(true)}
            >
              <Trash2 className="mr-1 h-3 w-3" />
              {t('agentSpec.batchDelete')}
            </Button>
            <Button variant="ghost" size="sm" className="h-8" onClick={clearSelection}>
              {t('common.cancel')}
            </Button>
          </div>
        )}
      </div>

      {/* Content area */}
      {loading && items.length === 0 ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
          {Array.from({ length: 8 }).map((_, i) => (
            <Card key={i} className="py-0 gap-0 overflow-hidden">
              <div className="p-4 space-y-3">
                <div className="flex gap-3">
                  <Skeleton className="h-10 w-10 rounded-xl" />
                  <div className="flex-1 space-y-2">
                    <Skeleton className="h-4 w-3/4" />
                    <Skeleton className="h-3 w-1/2" />
                  </div>
                </div>
                <Skeleton className="h-8 w-full" />
              </div>
              <div className="border-t bg-muted/20 px-4 py-2">
                <Skeleton className="h-4 w-24" />
              </div>
            </Card>
          ))}
        </div>
      ) : error && items.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
          <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-destructive/10 mb-4">
            <Package className="h-8 w-8 text-destructive/50" />
          </div>
          <p className="text-sm font-medium text-destructive">{error}</p>
          <Button variant="outline" size="sm" className="mt-4" onClick={loadData}>
            {t('common.retry')}
          </Button>
        </div>
      ) : items.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
          <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-muted/50 mb-4">
            <Package className="h-8 w-8 text-muted-foreground/50" />
          </div>
          <p className="text-sm font-medium">{t('common.noData')}</p>
          <p className="text-xs text-muted-foreground/70 mt-1">{t('agentSpec.searchPlaceholder')}</p>
          <Button variant="outline" size="sm" className="mt-4" onClick={() => setCreateOpen(true)}>
            <Plus className="mr-1.5 h-3.5 w-3.5" />
            {t('agentSpec.createAgentSpec')}
          </Button>
        </div>
      ) : (
        <div>
          {/* Select all toggle */}
          <div className="flex items-center justify-between mb-3">
            <button
              onClick={() => {
                if (allSelected) clearSelection();
                else selectAll(items.map((a) => a.name));
              }}
              className="text-xs text-muted-foreground hover:text-foreground transition-colors"
            >
              {allSelected ? t('common.cancel') : t('agentSpec.totalAgentSpecs', { total: items.length })}
            </button>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
            {items.map((item) => (
              <AgentSpecCard
                key={item.name}
                item={item}
                selected={selectedNames.has(item.name)}
                onSelect={toggleSelect}
                onDetail={handleDetail}
                onDelete={setDeleteTarget}
              />
            ))}
          </div>
        </div>
      )}

      {/* Pagination */}
      {total > 0 && totalPages > 1 && (
        <div className="flex items-center justify-end gap-2 pt-1">
          <Select
            value={String(pageSize)}
            onValueChange={(v) => setPage(1, Number(v))}
          >
            <SelectTrigger className="w-[100px] h-8 text-xs">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {[12, 24, 48].map((size) => (
                <SelectItem key={size} value={String(size)}>
                  {size} / {t('common.pageSize')}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button
            variant="outline"
            size="icon"
            className="h-8 w-8"
            disabled={pageNo <= 1}
            onClick={() => {
              setPage(pageNo - 1);
              fetchList(namespaceId);
            }}
          >
            <ChevronLeft className="h-4 w-4" />
          </Button>
          <span className="text-xs text-muted-foreground px-1.5 tabular-nums">
            {pageNo} / {totalPages || 1}
          </span>
          <Button
            variant="outline"
            size="icon"
            className="h-8 w-8"
            disabled={pageNo >= totalPages}
            onClick={() => {
              setPage(pageNo + 1);
              fetchList(namespaceId);
            }}
          >
            <ChevronRight className="h-4 w-4" />
          </Button>
        </div>
      )}

      {/* Upload dialog */}
      <UploadAgentSpecDialog
        open={uploadOpen}
        onOpenChange={setUploadOpen}
        namespaceId={namespaceId}
        onSuccess={loadData}
      />

      {/* Create dialog */}
      <CreateAgentSpecDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        namespaceId={namespaceId}
        onSuccess={(name) => {
          loadData();
          handleDetail(name);
        }}
      />

      {/* Delete confirm dialog */}
      <Dialog open={!!deleteTarget} onOpenChange={() => setDeleteTarget(null)}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>{t('common.delete')}</DialogTitle>
            <DialogDescription>
              {t('agentSpec.deleteConfirm', { name: deleteTarget })}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteTarget(null)} disabled={deleteLoading}>
              {t('common.cancel')}
            </Button>
            <Button variant="destructive" onClick={handleDelete} disabled={deleteLoading}>
              {deleteLoading ? t('common.loading') : t('common.delete')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Batch delete dialog */}
      <Dialog open={batchDeleteOpen} onOpenChange={setBatchDeleteOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>{t('agentSpec.batchDelete')}</DialogTitle>
            <DialogDescription>
              {t('agentSpec.batchDeleteConfirm', { count: selectedNames.size })}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setBatchDeleteOpen(false)} disabled={deleteLoading}>
              {t('common.cancel')}
            </Button>
            <Button variant="destructive" onClick={handleBatchDelete} disabled={deleteLoading}>
              {deleteLoading ? t('common.loading') : t('common.delete')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
