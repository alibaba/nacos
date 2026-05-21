import { useEffect, useCallback, useState, useRef } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import {
  Trash2,
  Search,
  X,
  ChevronLeft,
  ChevronRight,
  Wand2,
  Upload,
  Plus,
  Tag,
  Download,
} from 'lucide-react';
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
import { SkillCard } from './components/SkillCard';
import { UploadSkillDialog } from './components/UploadSkillDialog';
import { CreateSkillDialog } from './components/CreateSkillDialog';
import { ImportSkillDialog } from '@/components/ai/skill/ImportSkillDialog';
import { useSkillStore } from '@/stores/skill-store';
import { useNamespaceStore } from '@/stores/namespace-store';
import { useAuthStore } from '@/stores/auth-store';
import { skillApi } from '@/api/skill';

interface SkillBatchUploadResponse {
  data?: {
    succeeded?: string[];
    failed?: { name: string; reason: string }[];
  };
}

export default function SkillManagementPage() {
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
    filterBizTag,
    selectedNames,
    error,
    fetchList,
    setSearchParams,
    setPage,
    resetSearch,
    toggleSelect,
    selectAll,
    clearSelection,
  } = useSkillStore();

  const [deleteTarget, setDeleteTarget] = useState<string | null>(null);
  const [batchDeleteOpen, setBatchDeleteOpen] = useState(false);
  const [deleteLoading, setDeleteLoading] = useState(false);
  const [searchInput, setSearchInput] = useState(searchName);
  const [ownerInput, setOwnerInput] = useState(filterOwner);
  const [bizTagInput, setBizTagInput] = useState(filterBizTag);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [isDragOver, setIsDragOver] = useState(false);
  const dragCounter = useRef(0);

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
      filterBizTag: bizTagInput,
    });
    fetchList(namespaceId);
  };

  const handleReset = () => {
    setSearchInput('');
    setOwnerInput('');
    setBizTagInput('');
    resetSearch();
    fetchList(namespaceId);
  };

  const handleDetail = (name: string) => {
    navigate(`/skill/${encodeURIComponent(name)}`);
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    setDeleteLoading(true);
    try {
      await skillApi.delete({ namespaceId, skillName: deleteTarget });
      toast.success(t('skill.deleteSuccess'));
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
        names.map((name) => skillApi.delete({ namespaceId, skillName: name })),
      );
      toast.success(t('skill.batchDeleteSuccess'));
    } catch {
      // error handled by axios interceptor
    } finally {
      clearSelection();
      setBatchDeleteOpen(false);
      setDeleteLoading(false);
      loadData();
    }
  };

  const handlePageDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (e.dataTransfer) {
      e.dataTransfer.dropEffect = 'copy';
    }
  }, []);

  const handlePageDragEnter = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    dragCounter.current++;
    if (e.dataTransfer) {
      e.dataTransfer.dropEffect = 'copy';
    }
    setIsDragOver(true);
  }, []);

  const handlePageDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    dragCounter.current--;
    if (dragCounter.current === 0) {
      setIsDragOver(false);
    }
  }, []);

  const handlePageDrop = useCallback(async (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    dragCounter.current = 0;
    setIsDragOver(false);
    const droppedFile = e.dataTransfer?.files?.[0];
    if (!droppedFile) return;
    if (!droppedFile.name.toLowerCase().endsWith('.zip') && droppedFile.type !== 'application/zip') {
      toast.error(t('skill.invalidZipFile'));
      return;
    }
    try {
      const res = await skillApi.batchUpload(namespaceId, droppedFile);
      const data = (res as SkillBatchUploadResponse)?.data;
      if (data && (data.succeeded || data.failed)) {
        const succeededList: string[] = data.succeeded ?? [];
        const failedList: { name: string; reason: string }[] = data.failed ?? [];
        if (failedList.length === 0) {
          toast.success(t('skill.batchUploadAllSuccess', { count: succeededList.length }), { duration: 5000 });
        } else {
          const title = succeededList.length > 0
            ? t('skill.batchUploadResult', { succeeded: succeededList.length, failed: failedList.length })
            : t('skill.batchUploadAllFailed', { count: failedList.length });
          const description = (
            <div className="flex flex-col gap-0.5 text-xs">
              {succeededList.map((name) => (
                <div key={name} style={{ color: '#16a34a' }}>✓ {name}</div>
              ))}
              {failedList.map((item) => (
                <div key={item.name} style={{ color: '#dc2626' }}>
                  ✗ {item.name}<span style={{ opacity: 0.8 }}> — {item.reason}</span>
                </div>
              ))}
            </div>
          );
          const toastFn = succeededList.length > 0 ? toast.warning : toast.error;
          toastFn(title, { description, duration: 8000 });
        }
      } else {
        toast.success(t('skill.uploadSuccess'));
      }
      loadData();
    } catch {
      toast.error(t('skill.uploadFailed'));
    }
  }, [namespaceId, t, loadData]);

  const totalPages = Math.ceil(total / pageSize);
  const allSelected = items.length > 0 && items.every((a) => selectedNames.has(a.name));

  return (
    <div
      className="space-y-5 relative"
      onDragOver={handlePageDragOver}
      onDragEnter={handlePageDragEnter}
      onDragLeave={handlePageDragLeave}
      onDrop={handlePageDrop}
    >
      {/* Drag overlay */}
      {isDragOver && (
        <div className="absolute inset-0 z-50 flex items-center justify-center rounded-lg border-2 border-dashed border-primary bg-primary/5 pointer-events-none">
          <div className="text-center">
            <Upload className="h-12 w-12 text-primary mx-auto mb-2" />
            <p className="text-sm font-medium text-primary">{t('skill.dropFileHere')}</p>
          </div>
        </div>
      )}
      {/* Page header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold tracking-tight">{t('skill.title')}</h1>
          <p className="text-sm text-muted-foreground mt-0.5">
            {t('skill.totalSkills', { total })}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-xs text-muted-foreground hidden sm:inline">
            {t('skill.dragDropHint')}
          </span>
          <Button size="sm" variant="outline" onClick={() => setUploadOpen(true)}>
            <Upload className="mr-1.5 h-3.5 w-3.5" />
            {t('skill.upload')}
          </Button>
          <Button size="sm" variant="outline" onClick={() => setImportOpen(true)}>
            <Download className="mr-1.5 h-3.5 w-3.5" />
            {t('skill.importFromRegistry')}
          </Button>
          <Button size="sm" onClick={() => setCreateOpen(true)}>
            <Plus className="mr-1.5 h-3.5 w-3.5" />
            {t('skill.createSkill')}
          </Button>
        </div>
      </div>

      {/* Search & filters (single row; py gives room so focus rings are not clipped by overflow-x-auto) */}
      <div className="flex w-full min-w-0 items-center gap-2 overflow-x-auto px-0.5 py-2">
        <div className="relative min-w-[12rem] flex-1 max-w-md">
          <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground pointer-events-none" />
          <Input
            placeholder={t('skill.searchPlaceholder')}
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
            className="pl-8 h-8 text-sm"
          />
        </div>
        <div className="relative w-[10.5rem] shrink-0">
          <Tag className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground pointer-events-none" />
          <Input
            placeholder={t('skill.filterBizTagPlaceholder')}
            value={bizTagInput}
            onChange={(e) => setBizTagInput(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
            className="pl-8 h-8 text-sm"
          />
        </div>
        {globalAdmin ? (
          <Input
            placeholder={t('skill.filterOwnerPlaceholder')}
            value={ownerInput}
            onChange={(e) => setOwnerInput(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
            className="w-[9rem] shrink-0 h-8 text-xs"
            title={t('skill.filterByOwner')}
          />
        ) : (
          <Button
            size="sm"
            variant={filterOwner ? 'default' : 'outline'}
            className="h-8 text-xs shrink-0 whitespace-nowrap"
            onClick={() => {
              const next = filterOwner ? '' : (username || '');
              setSearchParams({ filterOwner: next });
              fetchList(namespaceId);
            }}
          >
            {t('skill.filterOnlyMine')}
          </Button>
        )}
        <Button size="sm" variant="secondary" className="h-8 shrink-0" onClick={handleSearch}>
          {t('common.search')}
        </Button>
        {(searchInput || filterOwner || filterScope || filterBizTag) && (
          <Button size="sm" variant="ghost" className="h-8 shrink-0" onClick={handleReset}>
            <X className="mr-1 h-3 w-3" />
            {t('common.reset')}
          </Button>
        )}
        <Select
          value={filterScope || ''}
          onValueChange={(v) => {
            setSearchParams({ filterScope: v === '_all' ? '' : v });
            fetchList(namespaceId);
          }}
        >
          <SelectTrigger className="w-[7.5rem] h-8 text-xs shrink-0">
            <SelectValue placeholder={t('skill.filterScopeAll')} />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="_all">{t('skill.filterScopeAll')}</SelectItem>
            <SelectItem value="PUBLIC">{t('skill.filterScopePublic')}</SelectItem>
            <SelectItem value="PRIVATE">{t('skill.filterScopePrivate')}</SelectItem>
          </SelectContent>
        </Select>
        <Select
          value={orderBy}
          onValueChange={(v) => {
            setSearchParams({ orderBy: v });
            fetchList(namespaceId);
          }}
        >
          <SelectTrigger className="w-[8.5rem] h-8 text-xs shrink-0">
            <SelectValue placeholder={t('skill.sortDefault')} />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value=" ">{t('skill.sortDefault')}</SelectItem>
            <SelectItem value="download_count">{t('skill.sortByDownloads')}</SelectItem>
          </SelectContent>
        </Select>
        {selectedNames.size > 0 && (
          <div className="flex items-center gap-2 shrink-0 ml-auto pl-2 border-l border-border/60">
            <span className="text-xs text-muted-foreground whitespace-nowrap">
              {t('config.selectedCount', { count: selectedNames.size })}
            </span>
            <Button
              variant="destructive"
              size="sm"
              className="h-8 shrink-0"
              onClick={() => setBatchDeleteOpen(true)}
            >
              <Trash2 className="mr-1 h-3 w-3" />
              {t('skill.batchDelete')}
            </Button>
            <Button variant="ghost" size="sm" className="h-8 shrink-0" onClick={clearSelection}>
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
            <Wand2 className="h-8 w-8 text-destructive/50" />
          </div>
          <p className="text-sm font-medium text-destructive">{error}</p>
          <Button variant="outline" size="sm" className="mt-4" onClick={loadData}>
            {t('common.retry') || t('common.search')}
          </Button>
        </div>
      ) : items.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
          <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-muted/50 mb-4">
            <Wand2 className="h-8 w-8 text-muted-foreground/50" />
          </div>
          <p className="text-sm font-medium">{t('common.noData')}</p>
          <p className="text-xs text-muted-foreground/70 mt-1">{t('skill.searchPlaceholder')}</p>
          <Button variant="outline" size="sm" className="mt-4" onClick={() => setUploadOpen(true)}>
            <Upload className="mr-1.5 h-3.5 w-3.5" />
            {t('skill.upload')}
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
              {allSelected ? t('common.cancel') : t('skill.totalSkills', { total: items.length })}
            </button>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
            {items.map((item) => (
              <SkillCard
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
      <UploadSkillDialog
        open={uploadOpen}
        onOpenChange={setUploadOpen}
        namespaceId={namespaceId}
        onSuccess={loadData}
      />

      {/* Import dialog */}
      <ImportSkillDialog
        open={importOpen}
        onOpenChange={setImportOpen}
        namespaceId={namespaceId}
        onSuccess={loadData}
      />

      {/* Create dialog */}
      <CreateSkillDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        namespaceId={namespaceId}
        onSuccess={(name) => {
          loadData();
          navigate(`/skill/${encodeURIComponent(name)}`);
        }}
      />

      {/* Delete confirm dialog */}
      <Dialog open={!!deleteTarget} onOpenChange={() => setDeleteTarget(null)}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>{t('common.delete')}</DialogTitle>
            <DialogDescription>
              {t('skill.deleteConfirm', { name: deleteTarget })}
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
            <DialogTitle>{t('skill.batchDelete')}</DialogTitle>
            <DialogDescription>
              {t('skill.batchDeleteConfirm', { count: selectedNames.size })}
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
