import { useCallback, useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Bot, ChevronLeft, ChevronRight, Plus, Search, Tag, Trash2, X } from 'lucide-react';
import { toast } from 'sonner';
import { AgentCard } from '@/components/ai/agent/AgentCard';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { useAgentStore } from '@/stores/agent-store';
import { useNamespaceStore } from '@/stores/namespace-store';
import type { AgentScope } from '@/types/agent';

export default function AgentManagementPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const { currentNamespace } = useNamespaceStore();
  const {
    agents,
    loading,
    total,
    pageNo,
    pageSize,
    searchName,
    bizTag,
    scope,
    owner,
    selectedNames,
    fetchAgents,
    setFilters,
    setPage,
    resetFilters,
    toggleSelect,
    selectAll,
    clearSelection,
  } = useAgentStore();
  const namespaceId = currentNamespace || 'public';
  const [inputs, setInputs] = useState({ searchName, bizTag, owner });
  const [scopeInput, setScopeInput] = useState<AgentScope | 'ALL'>(scope || 'ALL');
  const [deleteTarget, setDeleteTarget] = useState<string | null>(null);
  const [batchDeleteOpen, setBatchDeleteOpen] = useState(false);
  const [deleteLoading, setDeleteLoading] = useState(false);

  const loadData = useCallback(() => {
    fetchAgents(namespaceId);
  }, [fetchAgents, namespaceId]);

  useEffect(() => {
    loadData();
  }, [loadData, pageNo, pageSize, searchName, bizTag, scope, owner, location.key]);

  const handleSearch = () => {
    setFilters({
      searchName: inputs.searchName,
      bizTag: inputs.bizTag,
      owner: inputs.owner,
      scope: scopeInput === 'ALL' ? undefined : scopeInput,
    });
  };

  const handleReset = () => {
    setInputs({ searchName: '', bizTag: '', owner: '' });
    setScopeInput('ALL');
    resetFilters();
  };

  const navigateTo = (path: string, name?: string, mode?: string) => {
    const params = new URLSearchParams({ namespaceId });
    if (name) {
      params.set('name', name);
    }
    if (mode) {
      params.set('mode', mode);
    }
    navigate(`${path}?${params.toString()}`);
  };

  const handleDelete = async () => {
    if (!deleteTarget) {
      return;
    }
    setDeleteLoading(true);
    const success = await useAgentStore.getState().deleteAgent(namespaceId, deleteTarget);
    setDeleteLoading(false);
    if (success) {
      toast.success(t('agent.deleteSuccess'));
      setDeleteTarget(null);
      loadData();
    }
  };

  const handleBatchDelete = async () => {
    setDeleteLoading(true);
    const allSuccess = await useAgentStore.getState().batchDelete(
      namespaceId,
      Array.from(selectedNames),
    );
    setDeleteLoading(false);
    setBatchDeleteOpen(false);
    if (allSuccess) {
      toast.success(t('agent.batchDeleteSuccess'));
    } else {
      toast.error(t('agent.batchDeletePartialFailure'));
    }
    loadData();
  };

  const totalPages = Math.ceil(total / pageSize);
  const allSelected = agents.length > 0
    && agents.every((agent) => selectedNames.has(agent.agentName));

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold tracking-tight">{t('agent.title')}</h1>
          <p className="text-sm text-muted-foreground mt-0.5">
            {t('agent.totalAgents', { total })}
          </p>
        </div>
        <Button size="sm" onClick={() => navigateTo('/newAgent')}>
          <Plus className="mr-1.5 h-3.5 w-3.5" />
          {t('agent.createAgent')}
        </Button>
      </div>

      <div className="flex w-full min-w-0 items-center gap-2 overflow-x-auto px-0.5 py-2">
        <div className="relative min-w-[12rem] max-w-md flex-1">
          <Search
            className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground"
          />
          <Input
            value={inputs.searchName}
            onChange={(event) => setInputs({ ...inputs, searchName: event.target.value })}
            onKeyDown={(event) => event.key === 'Enter' && handleSearch()}
            placeholder={t('agent.searchPlaceholder')}
            className="h-8 pl-8 text-sm"
          />
        </div>
        <div className="relative w-[10.5rem] shrink-0">
          <Tag
            className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground"
          />
          <Input
            value={inputs.bizTag}
            onChange={(event) => setInputs({ ...inputs, bizTag: event.target.value })}
            onKeyDown={(event) => event.key === 'Enter' && handleSearch()}
            placeholder={t('agent.bizTagFilter')}
            className="h-8 pl-8 text-sm"
          />
        </div>
        <Input
          value={inputs.owner}
          onChange={(event) => setInputs({ ...inputs, owner: event.target.value })}
          onKeyDown={(event) => event.key === 'Enter' && handleSearch()}
          placeholder={t('agent.ownerFilter')}
          className="h-8 w-[9rem] shrink-0 text-xs"
        />
        <Select
          value={scopeInput}
          onValueChange={(value) => setScopeInput(value as AgentScope | 'ALL')}
        >
          <SelectTrigger className="h-8 w-[7.5rem] shrink-0 text-xs">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">{t('agent.allScopes')}</SelectItem>
            <SelectItem value="PUBLIC">{t('agent.publicScope')}</SelectItem>
            <SelectItem value="PRIVATE">{t('agent.privateScope')}</SelectItem>
          </SelectContent>
        </Select>
        <div className="flex gap-2">
          <Button size="sm" variant="secondary" className="h-8" onClick={handleSearch}>
            {t('common.search')}
          </Button>
          {(inputs.searchName || inputs.bizTag || inputs.owner || scopeInput !== 'ALL') && (
            <Button size="sm" variant="ghost" className="h-8" onClick={handleReset}>
              <X className="mr-1 h-3 w-3" />
              {t('common.reset')}
            </Button>
          )}
        </div>
      </div>

      {selectedNames.size > 0 && (
        <div className="flex items-center justify-end gap-2">
          <span className="text-xs text-muted-foreground">
            {t('config.selectedCount', { count: selectedNames.size })}
          </span>
          <Button
            variant="destructive"
            size="sm"
            onClick={() => setBatchDeleteOpen(true)}
          >
            <Trash2 className="mr-1 h-3 w-3" />
            {t('agent.batchDelete')}
          </Button>
          <Button variant="ghost" size="sm" onClick={clearSelection}>
            {t('common.cancel')}
          </Button>
        </div>
      )}

      {loading && agents.length === 0 ? (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4">
          {Array.from({ length: 8 }).map((_, index) => (
            <Card key={index} className="p-4 space-y-3">
              <Skeleton className="h-10 w-10 rounded-xl" />
              <Skeleton className="h-4 w-3/4" />
              <Skeleton className="h-8 w-full" />
            </Card>
          ))}
        </div>
      ) : agents.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
          <Bot className="h-10 w-10 mb-3 opacity-50" />
          <p className="text-sm">{t('common.noData')}</p>
        </div>
      ) : (
        <>
          <button
            onClick={() => {
              if (allSelected) {
                clearSelection();
              } else {
                selectAll(agents.map((agent) => agent.agentName));
              }
            }}
            className="text-xs text-muted-foreground hover:text-foreground"
          >
            {allSelected ? t('common.cancel') : t('agent.selectCurrentPage')}
          </button>
          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4">
            {agents.map((agent) => (
              <AgentCard
                key={agent.agentName}
                agent={agent}
                selected={selectedNames.has(agent.agentName)}
                onSelect={toggleSelect}
                onDetail={(name) => navigateTo('/agentDetail', name)}
                onDelete={setDeleteTarget}
              />
            ))}
          </div>
        </>
      )}

      {totalPages > 1 && (
        <div className="flex items-center justify-end gap-2">
          <Select value={String(pageSize)} onValueChange={(value) => setPage(1, Number(value))}>
            <SelectTrigger className="w-[100px] h-8 text-xs">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {[12, 24, 48].map((size) => (
                <SelectItem key={size} value={String(size)}>{size} / page</SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button
            variant="outline"
            size="icon"
            className="h-8 w-8"
            disabled={pageNo <= 1}
            onClick={() => setPage(pageNo - 1)}
          >
            <ChevronLeft className="h-4 w-4" />
          </Button>
          <span className="text-xs text-muted-foreground">{pageNo} / {totalPages}</span>
          <Button
            variant="outline"
            size="icon"
            className="h-8 w-8"
            disabled={pageNo >= totalPages}
            onClick={() => setPage(pageNo + 1)}
          >
            <ChevronRight className="h-4 w-4" />
          </Button>
        </div>
      )}

      <Dialog open={deleteTarget !== null} onOpenChange={() => setDeleteTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('common.delete')}</DialogTitle>
            <DialogDescription>{t('agent.deleteConfirm', { name: deleteTarget })}</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteTarget(null)}>
              {t('common.cancel')}
            </Button>
            <Button variant="destructive" disabled={deleteLoading} onClick={handleDelete}>
              {t('common.delete')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={batchDeleteOpen} onOpenChange={setBatchDeleteOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('agent.batchDelete')}</DialogTitle>
            <DialogDescription>
              {t('agent.batchDeleteConfirm', { count: selectedNames.size })}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setBatchDeleteOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button
              variant="destructive"
              disabled={deleteLoading}
              onClick={handleBatchDelete}
            >
              {t('common.delete')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
