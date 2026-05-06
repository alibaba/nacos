import { useEffect, useState, useRef } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import {
  Plus, Search, RotateCcw, MoreHorizontal, ChevronLeft, ChevronRight,
  Download, Upload, Copy, Trash2, X,
} from 'lucide-react';

import { useConfigStore } from '@/stores/config-store';
import { useNamespaceStore } from '@/stores/namespace-store';
import { CONFIG_TYPES } from '@/types/config';
import type { ConflictPolicy, ConfigCloneItem } from '@/types/config';
import { configApi } from '@/api/config';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { Checkbox } from '@/components/ui/checkbox';
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
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';

export default function ConfigurationManagementPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();

  const { currentNamespace, namespaceShowName, namespaces } = useNamespaceStore();
  const {
    configs,
    loading,
    total,
    pageNo,
    pageSize,
    dataId,
    groupName,
    appName,
    searchMode,
    selectedIds,
    fetchConfigs,
    setSearchParams: setStoreSearchParams,
    setPage,
    resetSearch,
    deleteConfig,
    toggleSelect,
    selectAll,
    clearSelection,
  } = useConfigStore();

  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [configToDelete, setConfigToDelete] = useState<{ dataId: string; groupName: string } | null>(null);

  // Batch operation dialog states
  const [batchDeleteDialogOpen, setBatchDeleteDialogOpen] = useState(false);
  const [importDialogOpen, setImportDialogOpen] = useState(false);
  const [cloneDialogOpen, setCloneDialogOpen] = useState(false);

  // Import state
  const [importPolicy, setImportPolicy] = useState<ConflictPolicy>('ABORT');
  const [importFile, setImportFile] = useState<File | null>(null);
  const [importing, setImporting] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // Clone state
  const [cloneTargetNamespace, setCloneTargetNamespace] = useState('');
  const [clonePolicy, setClonePolicy] = useState<ConflictPolicy>('ABORT');
  const [cloning, setCloning] = useState(false);

  // Local state for search inputs
  const [localDataId, setLocalDataId] = useState(dataId);
  const [localGroupName, setLocalGroupName] = useState(groupName);
  const [localAppName, setLocalAppName] = useState(appName);
  const [localSearchMode, setLocalSearchMode] = useState<'blur' | 'accurate'>(searchMode);

  // Clear selection when namespace changes
  useEffect(() => {
    clearSelection();
  }, [currentNamespace]);

  // Initialize from URL params on mount
  useEffect(() => {
    const urlDataId = searchParams.get('dataId') || '';
    const urlGroupName = searchParams.get('groupName') || '';
    const urlAppName = searchParams.get('appName') || '';
    const urlSearchMode = (searchParams.get('searchMode') as 'blur' | 'accurate') || 'blur';
    const urlPageNo = parseInt(searchParams.get('pageNo') || '1', 10);
    const urlPageSize = parseInt(searchParams.get('pageSize') || '10', 10);

    setLocalDataId(urlDataId);
    setLocalGroupName(urlGroupName);
    setLocalAppName(urlAppName);
    setLocalSearchMode(urlSearchMode);

    setStoreSearchParams({
      dataId: urlDataId,
      groupName: urlGroupName,
      appName: urlAppName,
      searchMode: urlSearchMode,
    });
    setPage(urlPageNo, urlPageSize);
  }, []);

  // Fetch data when namespace or page changes
  useEffect(() => {
    if (currentNamespace) {
      fetchConfigs(currentNamespace);
    }
  }, [currentNamespace, pageNo, pageSize]);

  // Update URL when search params or page changes
  useEffect(() => {
    const params = new URLSearchParams();
    if (dataId) params.set('dataId', dataId);
    if (groupName) params.set('groupName', groupName);
    if (appName) params.set('appName', appName);
    if (searchMode !== 'blur') params.set('searchMode', searchMode);
    if (pageNo !== 1) params.set('pageNo', pageNo.toString());
    if (pageSize !== 10) params.set('pageSize', pageSize.toString());
    setSearchParams(params, { replace: true });
  }, [dataId, groupName, appName, searchMode, pageNo, pageSize]);

  const handleSearch = () => {
    setStoreSearchParams({
      dataId: localDataId,
      groupName: localGroupName,
      appName: localAppName,
      searchMode: localSearchMode,
    });
    clearSelection();
    if (currentNamespace) {
      fetchConfigs(currentNamespace);
    }
  };

  const handleReset = () => {
    setLocalDataId('');
    setLocalGroupName('');
    setLocalAppName('');
    setLocalSearchMode('blur');
    resetSearch();
    if (currentNamespace) {
      fetchConfigs(currentNamespace);
    }
  };

  const handleDeleteClick = (dataId: string, groupName: string) => {
    setConfigToDelete({ dataId, groupName });
    setDeleteDialogOpen(true);
  };

  const handleConfirmDelete = async () => {
    if (!configToDelete || !currentNamespace) return;

    const success = await deleteConfig(
      configToDelete.dataId,
      configToDelete.groupName,
      currentNamespace
    );

    if (success) {
      setDeleteDialogOpen(false);
      setConfigToDelete(null);
      fetchConfigs(currentNamespace);
    }
  };

  const handleDetail = (dataId: string, groupName: string) => {
    navigate(`/configdetail?dataId=${encodeURIComponent(dataId)}&group=${encodeURIComponent(groupName)}&namespace=${encodeURIComponent(currentNamespace)}`);
  };

  const handleEdit = (dataId: string, groupName: string) => {
    navigate(`/configeditor?dataId=${encodeURIComponent(dataId)}&group=${encodeURIComponent(groupName)}&namespace=${encodeURIComponent(currentNamespace)}`);
  };

  const handleHistory = (dataId: string, groupName: string) => {
    navigate(`/historyRollback?dataId=${encodeURIComponent(dataId)}&group=${encodeURIComponent(groupName)}&namespace=${encodeURIComponent(currentNamespace)}`);
  };

  const handleListeners = (dataId: string, groupName: string) => {
    navigate(`/listeningToQuery?dataId=${encodeURIComponent(dataId)}&group=${encodeURIComponent(groupName)}&namespace=${encodeURIComponent(currentNamespace)}`);
  };

  const handleNewConfig = () => {
    navigate('/newconfig');
  };

  // Batch delete
  const handleBatchDelete = async () => {
    if (!currentNamespace) return;
    const ids = Array.from(selectedIds).join(',');
    try {
      await configApi.batchDelete({ ids, namespaceId: currentNamespace });
      toast.success(t('config.batchDeleteSuccess'));
      setBatchDeleteDialogOpen(false);
      clearSelection();
      fetchConfigs(currentNamespace);
    } catch {
      // Error toast handled by interceptor
    }
  };

  // Export
  const handleExportAll = () => {
    const url = configApi.exportUrl({
      namespaceId: currentNamespace,
      ids: '',
      dataId: dataId || '',
      groupName: groupName || '',
      appName: appName || '',
    });
    window.open(url);
  };

  const handleExportSelected = () => {
    if (selectedIds.size === 0) {
      toast.error(t('config.noSelection'));
      return;
    }
    const url = configApi.exportUrl({
      namespaceId: currentNamespace,
      ids: Array.from(selectedIds).join(','),
    });
    window.open(url);
  };

  // Import
  const handleImport = async () => {
    if (!importFile) {
      toast.error(t('config.noFileSelected'));
      return;
    }
    if (!currentNamespace) return;

    setImporting(true);
    try {
      await configApi.importFile(currentNamespace, importPolicy, importFile);
      toast.success(t('config.importSuccess'));
      setImportDialogOpen(false);
      setImportFile(null);
      setImportPolicy('ABORT');
      fetchConfigs(currentNamespace);
    } catch {
      // Error toast handled by interceptor
    } finally {
      setImporting(false);
    }
  };

  // Clone
  const handleClone = async () => {
    if (!cloneTargetNamespace) {
      toast.error(t('config.noTargetNamespace'));
      return;
    }
    if (selectedIds.size === 0 || !currentNamespace) return;

    const cloneItems: ConfigCloneItem[] = configs
      .filter((c) => c.id && selectedIds.has(c.id))
      .map((c) => ({
        cfgId: c.id!,
        dataId: c.dataId,
        group: c.groupName,
      }));

    setCloning(true);
    try {
      await configApi.clone(
        {
          namespaceId: currentNamespace,
          targetNamespaceId: cloneTargetNamespace,
          policy: clonePolicy,
        },
        cloneItems
      );
      toast.success(t('config.cloneSuccess'));
      setCloneDialogOpen(false);
      setCloneTargetNamespace('');
      setClonePolicy('ABORT');
      clearSelection();
    } catch {
      // Error toast handled by interceptor
    } finally {
      setCloning(false);
    }
  };

  const getTypeLabel = (type: string) => {
    const configType = CONFIG_TYPES.find(t => t.value === type);
    return configType?.label || type?.toUpperCase() || 'TEXT';
  };

  const getTypeBadgeVariant = (type: string): 'default' | 'secondary' | 'destructive' | 'outline' => {
    switch (type) {
      case 'json':
        return 'default';
      case 'yaml':
      case 'yml':
        return 'secondary';
      case 'xml':
        return 'outline';
      case 'properties':
        return 'destructive';
      default:
        return 'secondary';
    }
  };

  const totalPages = Math.ceil(total / pageSize);

  // Selection helpers
  const configsWithId = configs.filter((c) => c.id);
  const allSelected = configsWithId.length > 0 && configsWithId.every((c) => selectedIds.has(c.id!));
  const someSelected = configsWithId.some((c) => selectedIds.has(c.id!));

  // Get selected configs for dialogs
  const selectedConfigs = configs.filter((c) => c.id && selectedIds.has(c.id));

  // Available clone target namespaces (exclude current)
  const cloneTargetNamespaces = namespaces.filter((ns) => ns.namespace !== currentNamespace);

  return (
    <div className="flex flex-col gap-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-foreground">{t('config.title')}</h1>
        <div className="flex items-center gap-2">
          {/* Import button */}
          <Button
            variant="outline"
            onClick={() => {
              setImportFile(null);
              setImportPolicy('ABORT');
              setImportDialogOpen(true);
            }}
            className="gap-2"
          >
            <Upload className="h-4 w-4" />
            {t('config.import')}
          </Button>

          {/* New config button */}
          <Button onClick={handleNewConfig} className="gap-2">
            <Plus className="h-4 w-4" />
            {t('config.newConfig')}
          </Button>
        </div>
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
                  value={localGroupName}
                  onChange={(e) => setLocalGroupName(e.target.value)}
                  className="w-[200px]"
                  onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
                />
              </div>
              <div className="flex flex-col gap-2">
                <label className="text-sm font-medium text-muted-foreground">
                  {t('config.appName')}
                </label>
                <Input
                  placeholder={t('config.appName')}
                  value={localAppName}
                  onChange={(e) => setLocalAppName(e.target.value)}
                  className="w-[200px]"
                  onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
                />
              </div>
              <div className="flex flex-col gap-2">
                <label className="text-sm font-medium text-muted-foreground">
                  {t('common.mode')}
                </label>
                <div className="flex rounded-md border">
                  <Button
                    type="button"
                    variant={localSearchMode === 'blur' ? 'default' : 'ghost'}
                    size="sm"
                    onClick={() => setLocalSearchMode('blur')}
                    className="rounded-none rounded-l-md"
                  >
                    {t('config.fuzzySearch')}
                  </Button>
                  <Button
                    type="button"
                    variant={localSearchMode === 'accurate' ? 'default' : 'ghost'}
                    size="sm"
                    onClick={() => setLocalSearchMode('accurate')}
                    className="rounded-none rounded-r-md"
                  >
                    {t('config.exactSearch')}
                  </Button>
                </div>
              </div>
            </div>
            <div className="flex gap-2">
              <Button onClick={handleSearch} className="gap-2">
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

      {/* Batch Action Toolbar */}
      {selectedIds.size > 0 && (
        <div className="flex items-center gap-3 rounded-lg border bg-muted/50 px-4 py-2">
          <span className="text-sm font-medium">
            {t('config.selectedCount', { count: selectedIds.size })}
          </span>
          <div className="h-4 w-px bg-border" />
          <Button
            variant="destructive"
            size="sm"
            onClick={() => setBatchDeleteDialogOpen(true)}
            className="gap-1"
          >
            <Trash2 className="h-3.5 w-3.5" />
            {t('config.batchDelete')}
          </Button>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="outline" size="sm" className="gap-1">
                <Download className="h-3.5 w-3.5" />
                {t('config.export')}
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="start">
              <DropdownMenuItem onClick={handleExportSelected}>
                {t('config.exportSelected')}
              </DropdownMenuItem>
              <DropdownMenuItem onClick={handleExportAll}>
                {t('config.exportAll')}
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              setCloneTargetNamespace('');
              setClonePolicy('ABORT');
              setCloneDialogOpen(true);
            }}
            className="gap-1"
          >
            <Copy className="h-3.5 w-3.5" />
            {t('config.clone')}
          </Button>
          <div className="flex-1" />
          <Button variant="ghost" size="sm" onClick={clearSelection} className="gap-1">
            <X className="h-3.5 w-3.5" />
            {t('common.cancel')}
          </Button>
        </div>
      )}

      {/* Table Area */}
      <Card className="py-0">
        <CardContent className="p-0">
          {loading && configs.length === 0 ? (
            <div className="p-6 space-y-4">
              {Array.from({ length: 5 }).map((_, i) => (
                <Skeleton key={i} className="h-12 w-full" />
              ))}
            </div>
          ) : configs.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-16 text-muted-foreground">
              <p className="text-lg">{t('common.noData')}</p>
            </div>
          ) : (
            <div className={loading ? 'opacity-50 pointer-events-none transition-opacity' : 'transition-opacity'}>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-[40px] pl-4">
                    <Checkbox
                      checked={allSelected ? true : someSelected ? 'indeterminate' : false}
                      onCheckedChange={() => selectAll()}
                    />
                  </TableHead>
                  <TableHead className="pl-2">{t('config.dataId')}</TableHead>
                  <TableHead>{t('config.group')}</TableHead>
                  <TableHead>{t('config.type')}</TableHead>
                  <TableHead>{t('config.appName')}</TableHead>
                  <TableHead className="text-right pr-6">{t('common.operation')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {configs.map((config) => {
                  const isSelected = config.id ? selectedIds.has(config.id) : false;
                  return (
                    <TableRow
                      key={`${config.dataId}-${config.groupName}`}
                      className={isSelected ? 'bg-muted/50' : undefined}
                    >
                      <TableCell className="pl-4">
                        <Checkbox
                          checked={isSelected}
                          onCheckedChange={() => config.id && toggleSelect(config.id)}
                          disabled={!config.id}
                        />
                      </TableCell>
                      <TableCell className="font-medium max-w-[300px] truncate pl-2" title={config.dataId}>
                        {config.dataId}
                      </TableCell>
                      <TableCell className="max-w-[200px] truncate" title={config.groupName}>
                        {config.groupName}
                      </TableCell>
                      <TableCell>
                        <Badge variant={getTypeBadgeVariant(config.type)}>
                          {getTypeLabel(config.type)}
                        </Badge>
                      </TableCell>
                      <TableCell className="max-w-[200px] truncate" title={config.appName}>
                        {config.appName || '-'}
                      </TableCell>
                      <TableCell className="text-right pr-6">
                        <div className="flex items-center justify-end gap-2">
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => handleDetail(config.dataId, config.groupName)}
                          >
                            {t('common.detail')}
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => handleEdit(config.dataId, config.groupName)}
                          >
                            {t('common.edit')}
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            className="text-destructive hover:text-destructive"
                            onClick={() => handleDeleteClick(config.dataId, config.groupName)}
                          >
                            {t('common.delete')}
                          </Button>
                          <DropdownMenu>
                            <DropdownMenuTrigger asChild>
                              <Button variant="ghost" size="sm">
                                <MoreHorizontal className="h-4 w-4" />
                              </Button>
                            </DropdownMenuTrigger>
                            <DropdownMenuContent align="end">
                              <DropdownMenuItem
                                onClick={() => handleHistory(config.dataId, config.groupName)}
                              >
                                {t('config.history')}
                              </DropdownMenuItem>
                              <DropdownMenuItem
                                onClick={() => handleListeners(config.dataId, config.groupName)}
                              >
                                {t('config.listeners')}
                              </DropdownMenuItem>
                            </DropdownMenuContent>
                          </DropdownMenu>
                        </div>
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

      {/* Pagination - always show when total > 0 */}
      {total > 0 && (
        <div className="flex items-center justify-between">
          <div className="text-sm text-muted-foreground">
            {t('config.totalConfigs', { total })}
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

      {/* Single Delete Confirmation Dialog */}
      <Dialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('common.delete')}</DialogTitle>
            <DialogDescription>{t('config.deleteConfirm')}</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteDialogOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button variant="destructive" onClick={handleConfirmDelete}>
              {t('common.confirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Batch Delete Dialog */}
      <Dialog open={batchDeleteDialogOpen} onOpenChange={setBatchDeleteDialogOpen}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>{t('config.batchDelete')}</DialogTitle>
            <DialogDescription>
              {t('config.batchDeleteConfirm', { count: selectedIds.size })}
            </DialogDescription>
          </DialogHeader>
          <div className="max-h-[300px] overflow-auto border rounded-md">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="pl-4">Data ID</TableHead>
                  <TableHead>Group</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {selectedConfigs.map((config) => (
                  <TableRow key={config.id}>
                    <TableCell className="pl-4 font-medium">{config.dataId}</TableCell>
                    <TableCell>{config.groupName}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setBatchDeleteDialogOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button variant="destructive" onClick={handleBatchDelete}>
              {t('common.confirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Import Dialog */}
      <Dialog open={importDialogOpen} onOpenChange={setImportDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('config.importTitle')}</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            {/* Target namespace */}
            <div className="space-y-2">
              <label className="text-sm font-medium">{t('config.targetNamespace')}</label>
              <div className="text-sm text-primary font-medium px-3 py-2 rounded-md bg-muted">
                {namespaceShowName || currentNamespace}
              </div>
            </div>

            {/* Conflict policy */}
            <div className="space-y-2">
              <label className="text-sm font-medium">{t('config.conflictPolicy')}</label>
              <Select value={importPolicy} onValueChange={(v) => setImportPolicy(v as ConflictPolicy)}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ABORT">{t('config.policyAbort')}</SelectItem>
                  <SelectItem value="SKIP">{t('config.policySkip')}</SelectItem>
                  <SelectItem value="OVERWRITE">{t('config.policyOverwrite')}</SelectItem>
                </SelectContent>
              </Select>
            </div>

            {/* File upload */}
            <div className="space-y-2">
              <label className="text-sm font-medium">{t('config.uploadZip')}</label>
              <div className="flex items-center gap-3">
                <input
                  ref={fileInputRef}
                  type="file"
                  accept=".zip"
                  className="hidden"
                  onChange={(e) => setImportFile(e.target.files?.[0] || null)}
                />
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => fileInputRef.current?.click()}
                >
                  {t('config.selectFile')}
                </Button>
                <span className="text-sm text-muted-foreground truncate">
                  {importFile ? importFile.name : t('config.noFileSelected')}
                </span>
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setImportDialogOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button onClick={handleImport} disabled={!importFile || importing}>
              {importing ? '...' : t('config.import')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Clone Dialog */}
      <Dialog open={cloneDialogOpen} onOpenChange={setCloneDialogOpen}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>{t('config.cloneTitle')}</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            {/* Source info */}
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-1">
                <label className="text-sm font-medium text-muted-foreground">{t('config.sourceNamespace')}</label>
                <div className="text-sm">{namespaceShowName || currentNamespace}</div>
              </div>
              <div className="space-y-1">
                <label className="text-sm font-medium text-muted-foreground">{t('config.configCount')}</label>
                <div className="text-sm">{selectedIds.size}</div>
              </div>
            </div>

            {/* Target namespace */}
            <div className="space-y-2">
              <label className="text-sm font-medium">
                {t('config.targetNamespace')}
                <span className="text-destructive ml-1">*</span>
              </label>
              <Select value={cloneTargetNamespace} onValueChange={setCloneTargetNamespace}>
                <SelectTrigger>
                  <SelectValue placeholder={t('config.noTargetNamespace')} />
                </SelectTrigger>
                <SelectContent>
                  {cloneTargetNamespaces.map((ns) => (
                    <SelectItem key={ns.namespace} value={ns.namespace}>
                      {ns.namespaceShowName} ({ns.namespace})
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            {/* Conflict policy */}
            <div className="space-y-2">
              <label className="text-sm font-medium">{t('config.conflictPolicy')}</label>
              <Select value={clonePolicy} onValueChange={(v) => setClonePolicy(v as ConflictPolicy)}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ABORT">{t('config.policyAbort')}</SelectItem>
                  <SelectItem value="SKIP">{t('config.policySkip')}</SelectItem>
                  <SelectItem value="OVERWRITE">{t('config.policyOverwrite')}</SelectItem>
                </SelectContent>
              </Select>
            </div>

            {/* Selected configs table */}
            <div className="max-h-[200px] overflow-auto border rounded-md">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="pl-4">Data ID</TableHead>
                    <TableHead>Group</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {selectedConfigs.map((config) => (
                    <TableRow key={config.id}>
                      <TableCell className="pl-4 font-medium">{config.dataId}</TableCell>
                      <TableCell>{config.groupName}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCloneDialogOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button onClick={handleClone} disabled={!cloneTargetNamespace || cloning}>
              {cloning ? '...' : t('config.startClone')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
