import { useEffect, useCallback, useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { Plus, Trash2, Search, X, ChevronLeft, ChevronRight, MessageSquare } from 'lucide-react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { Separator } from '@/components/ui/separator';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { PromptCard } from '@/components/ai/prompt/PromptCard';
import { usePromptStore } from '@/stores/prompt-store';
import { useNamespaceStore } from '@/stores/namespace-store';
import { promptApi } from '@/api/prompt';
import type { PromptMetaInfo } from '@/types/prompt';

export default function PromptManagementPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const { currentNamespace } = useNamespaceStore();
  const {
    prompts,
    loading,
    total,
    pageNo,
    pageSize,
    searchKey,
    selectedKeys,
    fetchPrompts,
    setSearchParams,
    setPage,
    resetSearch,
    toggleSelect,
    selectAll,
    clearSelection,
  } = usePromptStore();

  const [deleteTarget, setDeleteTarget] = useState<string | null>(null);
  const [batchDeleteOpen, setBatchDeleteOpen] = useState(false);
  const [deleteLoading, setDeleteLoading] = useState(false);
  const [searchInput, setSearchInput] = useState(searchKey);

  // Edit metadata dialog state
  const [editDialogOpen, setEditDialogOpen] = useState(false);
  const [editDialogLoading, setEditDialogLoading] = useState(false);
  const [editSaving, setEditSaving] = useState(false);
  const [editPromptKey, setEditPromptKey] = useState('');
  const [editDescription, setEditDescription] = useState('');
  const [editBizTags, setEditBizTags] = useState<string[]>([]);
  const [editTagInput, setEditTagInput] = useState('');

  const namespaceId = currentNamespace || 'public';

  const loadData = useCallback(() => {
    fetchPrompts(namespaceId);
  }, [fetchPrompts, namespaceId]);

  useEffect(() => {
    loadData();
  }, [loadData, pageNo, pageSize, location.key]);

  const handleSearch = () => {
    setSearchParams({ searchKey: searchInput });
    fetchPrompts(namespaceId);
  };

  const handleReset = () => {
    setSearchInput('');
    resetSearch();
    fetchPrompts(namespaceId);
  };

  const handleDetail = (key: string) => {
    const params = new URLSearchParams({ promptKey: key, namespaceId });
    navigate(`/promptDetail?${params}`);
  };

  // Open edit metadata dialog
  const handleEdit = async (key: string) => {
    setEditPromptKey(key);
    setEditDialogOpen(true);
    setEditDialogLoading(true);
    try {
      const res = await promptApi.getPromptMetadata({ promptKey: key, namespaceId });
      const meta = (res as unknown as { data: PromptMetaInfo }).data;
      setEditDescription(meta.description || '');
      setEditBizTags(meta.bizTags || []);
    } catch {
      // Fall back to card summary data
      const prompt = prompts.find((p) => p.promptKey === key);
      setEditDescription(prompt?.description || '');
      setEditBizTags(prompt?.bizTags || []);
    } finally {
      setEditDialogLoading(false);
    }
  };

  const handleEditSave = async () => {
    setEditSaving(true);
    try {
      await promptApi.updateMetadata({
        promptKey: editPromptKey,
        description: editDescription.trim(),
        bizTags: editBizTags.join(','),
        namespaceId,
      });
      toast.success(t('prompt.updateSuccess'));
      setEditDialogOpen(false);
      loadData();
    } catch {
      // handled by interceptor
    } finally {
      setEditSaving(false);
    }
  };

  const handleEditAddTag = () => {
    const tag = editTagInput.trim();
    if (!tag || editBizTags.includes(tag)) { setEditTagInput(''); return; }
    setEditBizTags((prev) => [...prev, tag]);
    setEditTagInput('');
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    setDeleteLoading(true);
    try {
      await promptApi.deletePrompt({ promptKey: deleteTarget, namespaceId });
      toast.success(t('prompt.deleteSuccess'));
      setDeleteTarget(null);
      loadData();
    } catch {
      // handled by interceptor
    } finally {
      setDeleteLoading(false);
    }
  };

  const handleBatchDelete = async () => {
    setDeleteLoading(true);
    const keys = Array.from(selectedKeys);
    let allSuccess = true;
    for (const key of keys) {
      try {
        await promptApi.deletePrompt({ promptKey: key, namespaceId });
      } catch {
        allSuccess = false;
      }
    }
    if (allSuccess) toast.success(t('prompt.batchDeleteSuccess'));
    clearSelection();
    setBatchDeleteOpen(false);
    setDeleteLoading(false);
    loadData();
  };

  const totalPages = Math.ceil(total / pageSize);
  const allSelected = prompts.length > 0 && prompts.every((p) => selectedKeys.has(p.promptKey));

  return (
    <div className="space-y-5">
      {/* Page header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold tracking-tight">{t('prompt.title')}</h1>
          <p className="text-sm text-muted-foreground mt-0.5">
            {t('prompt.totalPrompts', { total })}
          </p>
        </div>
        <Button size="sm" onClick={() => navigate('/newPrompt')}>
          <Plus className="mr-1.5 h-3.5 w-3.5" />
          {t('prompt.createPrompt')}
        </Button>
      </div>

      {/* Search & filters bar */}
      <div className="flex flex-wrap items-center gap-2">
        <div className="relative flex-1 min-w-[220px] max-w-md">
          <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
          <Input
            placeholder={t('prompt.searchPlaceholder')}
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
            className="pl-8 h-8 text-sm"
          />
        </div>
        <Button size="sm" variant="secondary" className="h-8" onClick={handleSearch}>
          {t('common.search')}
        </Button>
        {searchInput && (
          <Button size="sm" variant="ghost" className="h-8" onClick={handleReset}>
            <X className="mr-1 h-3 w-3" />
            {t('common.reset')}
          </Button>
        )}

        {/* Batch operations */}
        {selectedKeys.size > 0 && (
          <div className="flex items-center gap-2 ml-auto">
            <span className="text-xs text-muted-foreground">
              {t('config.selectedCount', { count: selectedKeys.size })}
            </span>
            <Button
              variant="destructive"
              size="sm"
              className="h-8"
              onClick={() => setBatchDeleteOpen(true)}
            >
              <Trash2 className="mr-1 h-3 w-3" />
              {t('prompt.batchDelete')}
            </Button>
            <Button variant="ghost" size="sm" className="h-8" onClick={clearSelection}>
              {t('common.cancel')}
            </Button>
          </div>
        )}
      </div>

      {/* Content area */}
      {loading && prompts.length === 0 ? (
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
      ) : prompts.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
          <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-muted/50 mb-4">
            <MessageSquare className="h-8 w-8 text-muted-foreground/50" />
          </div>
          <p className="text-sm font-medium">{t('common.noData')}</p>
          <p className="text-xs text-muted-foreground/70 mt-1">{t('prompt.searchPlaceholder')}</p>
          <Button variant="outline" size="sm" className="mt-4" onClick={() => navigate('/newPrompt')}>
            <Plus className="mr-1.5 h-3.5 w-3.5" />
            {t('prompt.createPrompt')}
          </Button>
        </div>
      ) : (
        <div>
          {/* Select all toggle */}
          <div className="flex items-center justify-between mb-3">
            <button
              onClick={() => {
                if (allSelected) clearSelection();
                else selectAll(prompts.map((p) => p.promptKey));
              }}
              className="text-xs text-muted-foreground hover:text-foreground transition-colors"
            >
              {allSelected ? t('common.cancel') : t('prompt.totalPrompts', { total: prompts.length })}
            </button>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
            {prompts.map((prompt) => (
              <PromptCard
                key={prompt.promptKey}
                prompt={prompt}
                selected={selectedKeys.has(prompt.promptKey)}
                onSelect={toggleSelect}
                onDetail={handleDetail}
                onEdit={handleEdit}
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
              fetchPrompts(namespaceId);
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
              fetchPrompts(namespaceId);
            }}
          >
            <ChevronRight className="h-4 w-4" />
          </Button>
        </div>
      )}

      {/* Edit Metadata Dialog */}
      <Dialog open={editDialogOpen} onOpenChange={(open) => { if (!editSaving) setEditDialogOpen(open); }}>
        <DialogContent className="max-w-lg p-0 gap-0 overflow-hidden">
          {/* Header */}
          <div className="px-6 pt-6 pb-4">
            <DialogHeader className="space-y-1.5">
              <DialogTitle className="text-base">{t('prompt.editMetadata')}</DialogTitle>
              <DialogDescription className="font-mono text-xs tracking-wide">{editPromptKey}</DialogDescription>
            </DialogHeader>
          </div>

          <Separator />

          {editDialogLoading ? (
            <div className="px-6 py-5 space-y-3">
              <Skeleton className="h-20 w-full" />
              <Skeleton className="h-10 w-full" />
            </div>
          ) : (
            <div className="px-6 py-5 space-y-3">
              {/* Description */}
              <div className="flex flex-col gap-3">
                <Label className="text-sm font-medium text-muted-foreground">{t('prompt.description')}</Label>
                <Textarea
                  value={editDescription}
                  onChange={(e) => setEditDescription(e.target.value)}
                  placeholder={t('prompt.descriptionPlaceholder')}
                  rows={3}
                  className="bg-transparent resize-none text-sm"
                />
              </div>

              {/* Biz Tags */}
              <div className="flex flex-col gap-3">
                <Label className="text-sm font-medium text-muted-foreground">{t('prompt.bizTags')}</Label>
                <div className="rounded-lg border bg-muted/20 p-3 space-y-2.5">
                  {editBizTags.length > 0 && (
                    <div className="flex flex-wrap gap-1.5">
                      {editBizTags.map((tag) => (
                        <Badge
                          key={tag}
                          variant="secondary"
                          className="gap-1.5 pl-2.5 pr-1 py-0.5 text-xs font-normal bg-background border shadow-sm"
                        >
                          {tag}
                          <button
                            onClick={() => setEditBizTags((prev) => prev.filter((t) => t !== tag))}
                            className="rounded-full hover:bg-destructive/10 hover:text-destructive p-0.5 transition-colors"
                          >
                            <X className="h-3 w-3" />
                          </button>
                        </Badge>
                      ))}
                    </div>
                  )}
                  <div className="flex gap-2">
                    <Input
                      value={editTagInput}
                      onChange={(e) => setEditTagInput(e.target.value)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') { e.preventDefault(); handleEditAddTag(); }
                      }}
                      placeholder={t('prompt.tagPlaceholder')}
                      className="bg-transparent flex-1 h-8 text-sm"
                    />
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      className="h-8 px-3 shrink-0"
                      onClick={handleEditAddTag}
                      disabled={!editTagInput.trim()}
                    >
                      <Plus className="h-3.5 w-3.5 mr-1" />
                      {t('common.add', { defaultValue: '添加' })}
                    </Button>
                  </div>
                </div>
              </div>
            </div>
          )}

          <Separator />

          {/* Footer */}
          <div className="px-6 py-4 flex justify-end gap-2 bg-muted/20">
            <Button variant="outline" size="sm" onClick={() => setEditDialogOpen(false)} disabled={editSaving}>
              {t('common.cancel')}
            </Button>
            <Button size="sm" onClick={handleEditSave} disabled={editSaving || editDialogLoading}>
              {editSaving ? t('common.loading') : t('common.save')}
            </Button>
          </div>
        </DialogContent>
      </Dialog>

      {/* Delete confirm dialog */}
      <Dialog open={!!deleteTarget} onOpenChange={() => setDeleteTarget(null)}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>{t('common.delete')}</DialogTitle>
            <DialogDescription>
              {t('prompt.deleteConfirm', { name: deleteTarget })}
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
            <DialogTitle>{t('prompt.batchDelete')}</DialogTitle>
            <DialogDescription>
              {t('prompt.batchDeleteConfirm', { count: selectedKeys.size })}
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
