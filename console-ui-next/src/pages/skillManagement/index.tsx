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
  Bell,
  BellOff,
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
import type { SkillListItem } from '@/types/skill';

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
    subscriptions,
    subscriptionMap,
    subscriptionLoading,
    subscriptionSaving,
    error,
    fetchList,
    fetchSubscriptions,
    subscribeSkills,
    unsubscribeSkills,
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
  const [filterSubscribed, setFilterSubscribed] = useState(false);
  const [subscribedItems, setSubscribedItems] = useState<SkillListItem[]>([]);
  const [subscribedListLoading, setSubscribedListLoading] = useState(false);
  const [subscribedListError, setSubscribedListError] = useState<string | null>(null);
  const [uploadInitialFile, setUploadInitialFile] = useState<File | null>(null);
  const [isDragOver, setIsDragOver] = useState(false);
  const dragCounter = useRef(0);

  const namespaceId = currentNamespace || 'public';

  const loadData = useCallback(() => {
    fetchList(namespaceId);
    fetchSubscriptions(namespaceId);
  }, [fetchList, fetchSubscriptions, namespaceId]);

  useEffect(() => {
    if (!filterSubscribed) {
      loadData();
    }
  }, [filterSubscribed, loadData, pageNo, pageSize, location.key]);

  useEffect(() => {
    if (filterSubscribed) {
      fetchSubscriptions(namespaceId);
    }
  }, [fetchSubscriptions, filterSubscribed, location.key, namespaceId]);

  useEffect(() => {
    if (!filterSubscribed) {
      setSubscribedItems([]);
      setSubscribedListError(null);
      setSubscribedListLoading(false);
      return;
    }
    if (subscriptionLoading) {
      return;
    }
    if (subscriptions.length === 0) {
      setSubscribedItems([]);
      setSubscribedListError(null);
      setSubscribedListLoading(false);
      return;
    }

    let canceled = false;
    setSubscribedListLoading(true);
    setSubscribedListError(null);

    const loadSubscribedItems = async () => {
      try {
        const loadedItems = await Promise.all(
          subscriptions.map(async (subscription) => {
            const response = await skillApi.list({
              namespaceId,
              skillName: subscription.name,
              search: 'accurate',
              owner: filterOwner || undefined,
              bizTag: filterBizTag || undefined,
              pageNo: 1,
              pageSize: 1,
            });
            return response.data.pageItems?.find((item) => item.name === subscription.name) || null;
          }),
        );
        if (canceled) {
          return;
        }
        let nextItems = loadedItems.filter((item): item is SkillListItem => Boolean(item));
        if (searchName) {
          const lowerSearchName = searchName.toLowerCase();
          nextItems = nextItems.filter((item) => item.name.toLowerCase().includes(lowerSearchName));
        }
        if (orderBy === 'download_count') {
          nextItems = [...nextItems].sort((a, b) => b.downloadCount - a.downloadCount);
        }
        setSubscribedItems(nextItems);
        setSubscribedListLoading(false);
      } catch (loadError) {
        if (canceled) {
          return;
        }
        setSubscribedItems([]);
        const axiosError = loadError as { response?: { data?: { message?: string } } };
        setSubscribedListError(
          axiosError.response?.data?.message || 'Failed to fetch subscribed skills',
        );
        setSubscribedListLoading(false);
      }
    };

    loadSubscribedItems();
    return () => {
      canceled = true;
    };
  }, [
    filterSubscribed,
    subscriptionLoading,
    subscriptions,
    namespaceId,
    searchName,
    filterOwner,
    filterBizTag,
    orderBy,
  ]);

  useEffect(() => {
    if (!filterSubscribed || subscribedListLoading) {
      return;
    }
    const maxPage = Math.max(1, Math.ceil(subscribedItems.length / pageSize));
    if (pageNo > maxPage) {
      setPage(maxPage);
    }
  }, [
    filterSubscribed,
    subscribedItems.length,
    subscribedListLoading,
    pageNo,
    pageSize,
    setPage,
  ]);

  const handleSearch = () => {
    setSearchParams({
      searchName: searchInput,
      filterOwner: globalAdmin ? ownerInput : (ownerInput ? username || '' : ''),
      filterBizTag: bizTagInput,
    });
    if (!filterSubscribed) {
      fetchList(namespaceId);
    }
  };

  const handleReset = () => {
    setSearchInput('');
    setOwnerInput('');
    setBizTagInput('');
    setFilterSubscribed(false);
    resetSearch();
    fetchList(namespaceId);
  };

  const handleDetail = (name: string) => {
    const params = new URLSearchParams({ namespaceId });
    navigate(`/skill/${encodeURIComponent(name)}?${params}`);
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

  const handleSubscribeSkill = async (name: string) => {
    try {
      await subscribeSkills(namespaceId, [name]);
      toast.success(t('skill.subscribeSuccess'));
    } catch {
      // error handled by axios interceptor
    }
  };

  const handleUnsubscribeSkill = async (name: string) => {
    try {
      await unsubscribeSkills(namespaceId, [name]);
      toast.success(t('skill.unsubscribeSuccess'));
    } catch {
      // error handled by axios interceptor
    }
  };

  const handleBatchSubscription = async () => {
    const names = Array.from(selectedNames);
    if (names.length === 0) return;
    try {
      if (names.every((name) => subscriptionMap[name])) {
        await unsubscribeSkills(namespaceId, names);
        toast.success(t('skill.unsubscribeSuccess'));
      } else {
        await subscribeSkills(namespaceId, names);
        toast.success(t('skill.subscribeSuccess'));
      }
    } catch {
      // error handled by axios interceptor
    } finally {
      clearSelection();
    }
  };

  const handleSubscriptionFilterChange = (nextActive: boolean) => {
    clearSelection();
    setFilterSubscribed(nextActive);
    setSearchParams({ filterScope: '' });
    if (!nextActive) {
      fetchList(namespaceId);
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

  const handlePageDrop = useCallback((e: React.DragEvent) => {
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
    setUploadInitialFile(droppedFile);
    setUploadOpen(true);
  }, [t]);

  const subscribedPageItems = filterSubscribed
    ? subscribedItems.slice((pageNo - 1) * pageSize, pageNo * pageSize)
    : [];
  const visibleItems = filterSubscribed ? subscribedPageItems : items;
  const visibleTotal = filterSubscribed ? subscribedItems.length : total;
  const totalPages = Math.ceil(visibleTotal / pageSize);
  const allSelected = visibleItems.length > 0
    && visibleItems.every((a) => selectedNames.has(a.name));
  const selectedList = Array.from(selectedNames);
  const selectedAllSubscribed = selectedList.length > 0
    && selectedList.every((name) => subscriptionMap[name]);
  const contentLoading = filterSubscribed ? subscriptionLoading || subscribedListLoading : loading;
  const contentError = filterSubscribed ? subscribedListError || error : error;
  const hasSearchFilters = Boolean(
    searchInput || ownerInput || bizTagInput || filterOwner || filterScope || filterBizTag
      || orderBy.trim(),
  );

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
            {t('skill.totalSkills', { total: visibleTotal })}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            size="sm"
            variant="outline"
            onClick={() => {
              setUploadInitialFile(null);
              setUploadOpen(true);
            }}
          >
            <Upload className="mr-1.5 h-3.5 w-3.5" />
            {t('skill.upload')}
          </Button>
          <Button size="sm" variant="outline" onClick={() => setImportOpen(true)}>
            <Download className="mr-1.5 h-3.5 w-3.5" />
            {t('skill.importFromRegistry')}
          </Button>
          <Button size="sm" className="w-[8.75rem] justify-center" onClick={() => setCreateOpen(true)}>
            <Plus className="mr-1.5 h-3.5 w-3.5" />
            {t('skill.createSkill')}
          </Button>
        </div>
      </div>

      {/* Search & filters (single row; py gives room so focus rings are not clipped by overflow-x-auto) */}
      <div className="flex w-full min-w-0 items-center gap-2 overflow-x-auto px-0.5 py-2">
        <div className="relative min-w-[16rem] shrink-0 w-full md:w-[calc((100%_-_1rem)_/_2)] lg:w-[calc((100%_-_2rem)_/_3)] xl:w-[calc((100%_-_3rem)_/_4)]">
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
              if (!filterSubscribed) {
                fetchList(namespaceId);
              }
            }}
          >
            {t('skill.filterOnlyMine')}
          </Button>
        )}
        <Button size="sm" variant="secondary" className="h-8 shrink-0" onClick={handleSearch}>
          {t('common.search')}
        </Button>
        <Select
          value={filterScope || '_all'}
          onValueChange={(v) => {
            clearSelection();
            setFilterSubscribed(false);
            setSearchParams({ filterScope: v === '_all' ? '' : v });
            fetchList(namespaceId);
          }}
        >
          <SelectTrigger className="w-[9rem] h-8 text-xs shrink-0">
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
            if (!filterSubscribed) {
              fetchList(namespaceId);
            }
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
        {!filterSubscribed && hasSearchFilters && (
          <Button size="sm" variant="ghost" className="h-8 shrink-0" onClick={handleReset}>
            <X className="mr-1 h-3 w-3" />
            {t('common.reset')}
          </Button>
        )}
        <div className="ml-auto flex shrink-0 items-center gap-2 pl-2">
          {filterSubscribed && (
            <Button size="sm" variant="ghost" className="h-8 shrink-0" onClick={handleReset}>
              <X className="mr-1 h-3 w-3" />
              {t('common.reset')}
            </Button>
          )}
          <Button
            size="sm"
            variant={filterSubscribed ? 'default' : 'outline'}
            className="h-8 w-[8.75rem] shrink-0 justify-center whitespace-nowrap"
            aria-pressed={filterSubscribed}
            disabled={subscriptionLoading}
            onClick={() => handleSubscriptionFilterChange(!filterSubscribed)}
          >
            <Bell className="mr-1 h-3 w-3" />
            {t('skill.mySubscriptions')}
            {subscriptions.length > 0 && (
              <span
                className={filterSubscribed
                  ? 'ml-1 text-xs text-primary-foreground/80'
                  : 'ml-1 text-xs text-muted-foreground'}
              >
                ({subscriptions.length})
              </span>
            )}
          </Button>
        </div>
      </div>

      {selectedNames.size > 0 && (
        <div className="flex flex-wrap items-center justify-between gap-2 rounded-md border bg-muted/30 px-3 py-2">
          <span className="text-xs text-muted-foreground whitespace-nowrap">
            {t('config.selectedCount', { count: selectedNames.size })}
          </span>
          <div className="flex flex-wrap items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              className="h-8 shrink-0"
              disabled={subscriptionSaving}
              onClick={handleBatchSubscription}
            >
              {selectedAllSubscribed ? (
                <BellOff className="mr-1 h-3 w-3" />
              ) : (
                <Bell className="mr-1 h-3 w-3" />
              )}
              {selectedAllSubscribed ? t('skill.unsubscribe') : t('skill.subscribe')}
            </Button>
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
        </div>
      )}

      {/* Content area */}
      {contentLoading && visibleItems.length === 0 ? (
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
      ) : contentError && visibleItems.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
          <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-destructive/10 mb-4">
            <Wand2 className="h-8 w-8 text-destructive/50" />
          </div>
          <p className="text-sm font-medium text-destructive">{contentError}</p>
          <Button variant="outline" size="sm" className="mt-4" onClick={loadData}>
            {t('common.retry') || t('common.search')}
          </Button>
        </div>
      ) : visibleItems.length === 0 ? (
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
                else selectAll(visibleItems.map((a) => a.name));
              }}
              className="text-xs text-muted-foreground hover:text-foreground transition-colors"
            >
              {allSelected ? t('common.cancel') : t('skill.totalSkills', { total: visibleItems.length })}
            </button>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
            {visibleItems.map((item) => (
              <SkillCard
                key={item.name}
                item={item}
                selected={selectedNames.has(item.name)}
                subscribed={!!subscriptionMap[item.name]}
                subscriptionSaving={subscriptionSaving}
                onSelect={toggleSelect}
                onDetail={handleDetail}
                onDelete={setDeleteTarget}
                onSubscribe={handleSubscribeSkill}
                onUnsubscribe={handleUnsubscribeSkill}
              />
            ))}
          </div>
        </div>
      )}

      {/* Pagination */}
      {visibleTotal > 0 && totalPages > 1 && (
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
              if (!filterSubscribed) {
                fetchList(namespaceId);
              }
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
              if (!filterSubscribed) {
                fetchList(namespaceId);
              }
            }}
          >
            <ChevronRight className="h-4 w-4" />
          </Button>
        </div>
      )}

      {/* Upload dialog */}
      <UploadSkillDialog
        open={uploadOpen}
        onOpenChange={(nextOpen) => {
          setUploadOpen(nextOpen);
          if (!nextOpen) {
            setUploadInitialFile(null);
          }
        }}
        namespaceId={namespaceId}
        onSuccess={loadData}
        initialFile={uploadInitialFile}
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
          handleDetail(name);
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
