import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { Plus, Search, RotateCcw, ChevronLeft, ChevronRight } from 'lucide-react';

import { serviceApi } from '@/api/service';
import { useServiceStore } from '@/stores/service-store';
import { useNamespaceStore } from '@/stores/namespace-store';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Badge } from '@/components/ui/badge';
import { Switch } from '@/components/ui/switch';
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
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Textarea } from '@/components/ui/textarea';

export default function ServiceManagementPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { currentNamespace } = useNamespaceStore();

  const {
    services,
    loading,
    total,
    pageNo,
    pageSize,
    serviceNameParam,
    groupNameParam,
    ignoreEmptyService,
    selectorTypes,
    fetchServices,
    setSearchParams,
    setPage,
    resetSearch,
    deleteService,
    fetchSelectorTypes,
  } = useServiceStore();

  // Local search inputs
  const [localServiceName, setLocalServiceName] = useState(serviceNameParam);
  const [localGroupName, setLocalGroupName] = useState(groupNameParam);

  // Delete dialog
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<{ name: string; groupName: string } | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  // Create service dialog
  const [createOpen, setCreateOpen] = useState(false);
  const [createForm, setCreateForm] = useState({
    serviceName: '',
    groupName: 'DEFAULT_GROUP',
    protectThreshold: 0,
    ephemeral: false,
    selectorType: 'none',
    selectorExpression: '',
    metadata: '',
  });
  const [creating, setCreating] = useState(false);

  const loadServices = useCallback(() => {
    fetchServices(currentNamespace);
  }, [fetchServices, currentNamespace]);

  useEffect(() => {
    loadServices();
  }, [loadServices, pageNo, pageSize]);

  useEffect(() => {
    setLocalServiceName(serviceNameParam);
    setLocalGroupName(groupNameParam);
  }, [serviceNameParam, groupNameParam]);

  const handleSearch = () => {
    setSearchParams({
      serviceNameParam: localServiceName,
      groupNameParam: localGroupName,
    });
    setTimeout(() => fetchServices(currentNamespace), 0);
  };

  const handleReset = () => {
    setLocalServiceName('');
    setLocalGroupName('');
    resetSearch();
    setTimeout(() => fetchServices(currentNamespace), 0);
  };

  const handleIgnoreEmptyChange = (checked: boolean) => {
    setSearchParams({ ignoreEmptyService: checked });
    setTimeout(() => fetchServices(currentNamespace), 0);
  };

  const handlePageSizeChange = (v: string) => {
    setPage(1, parseInt(v, 10));
    setTimeout(() => fetchServices(currentNamespace), 0);
  };

  const handlePageChange = (newPage: number) => {
    setPage(newPage);
    setTimeout(() => fetchServices(currentNamespace), 0);
  };

  // Delete
  const handleDeleteConfirm = async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    setDeleteError(null);
    const result = await deleteService(currentNamespace, deleteTarget.name, deleteTarget.groupName);
    setDeleting(false);
    if (result.ok) {
      toast.success(t('service.deleteSuccess'));
      setDeleteOpen(false);
      setDeleteTarget(null);
      loadServices();
    } else {
      // Show localized error: detect "not empty" pattern from backend
      const reason = result.reason || '';
      const hasInstances = reason.toLowerCase().includes('not empty');
      setDeleteError(hasInstances ? t('service.deleteHasInstances') : reason);
    }
  };

  // Create
  const handleCreateOpen = () => {
    fetchSelectorTypes();
    setCreateForm({
      serviceName: '',
      groupName: 'DEFAULT_GROUP',
      protectThreshold: 0,
      ephemeral: false,
      selectorType: 'none',
      selectorExpression: '',
      metadata: '',
    });
    setCreateOpen(true);
  };

  const handleCreateSubmit = async () => {
    if (!createForm.serviceName.trim()) {
      toast.error(t('service.serviceNameRequired'));
      return;
    }
    if (createForm.metadata.trim()) {
      try {
        JSON.parse(createForm.metadata);
      } catch {
        toast.error(t('service.metadataInvalid'));
        return;
      }
    }
    setCreating(true);
    try {
      const selectorJson = createForm.selectorType === 'none'
        ? JSON.stringify({ type: 'none' })
        : JSON.stringify({ type: createForm.selectorType, expression: createForm.selectorExpression });
      await serviceApi.createService({
        namespaceId: currentNamespace,
        serviceName: createForm.serviceName.trim(),
        groupName: createForm.groupName.trim() || 'DEFAULT_GROUP',
        protectThreshold: createForm.protectThreshold,
        ephemeral: createForm.ephemeral,
        metadata: createForm.metadata.trim() || undefined,
        selector: selectorJson,
      });
      toast.success(t('service.createSuccess'));
      setCreateOpen(false);
      loadServices();
    } catch {
      // Error handled by interceptor
    } finally {
      setCreating(false);
    }
  };

  const totalPages = Math.max(1, Math.ceil(total / pageSize));

  return (
    <div className="flex flex-col gap-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-foreground">{t('service.title')}</h1>
        <Button onClick={handleCreateOpen} className="gap-2">
          <Plus className="h-4 w-4" />
          {t('service.createService')}
        </Button>
      </div>

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
                value={localServiceName}
                onChange={(e) => setLocalServiceName(e.target.value)}
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
                value={localGroupName}
                onChange={(e) => setLocalGroupName(e.target.value)}
                className="w-[200px]"
                onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
              />
            </div>
            <div className="flex items-center gap-2 pb-0.5">
              <Switch
                id="ignoreEmpty"
                checked={ignoreEmptyService}
                onCheckedChange={handleIgnoreEmptyChange}
              />
              <Label htmlFor="ignoreEmpty" className="text-sm cursor-pointer">
                {t('service.ignoreEmptyService')}
              </Label>
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
          {loading && services.length === 0 ? (
            <div className="p-6 space-y-4">
              {Array.from({ length: 5 }).map((_, i) => (
                <Skeleton key={i} className="h-12 w-full" />
              ))}
            </div>
          ) : services.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-16 text-muted-foreground">
              <p className="text-lg">{t('common.noData')}</p>
            </div>
          ) : (
            <Table className={loading ? 'opacity-50 pointer-events-none' : ''}>
              <TableHeader>
                <TableRow>
                  <TableHead className="pl-6">{t('service.serviceName')}</TableHead>
                  <TableHead>{t('service.groupName')}</TableHead>
                  <TableHead>{t('service.clusterCount')}</TableHead>
                  <TableHead>{t('service.ipCount')}</TableHead>
                  <TableHead>{t('service.healthyInstanceCount')}</TableHead>
                  <TableHead>{t('service.triggerFlag')}</TableHead>
                  <TableHead className="pr-6">{t('common.operation')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {services.map((svc) => (
                  <TableRow
                    key={`${svc.groupName}@@${svc.name}`}
                    className={svc.healthyInstanceCount === 0 && svc.ipCount > 0 ? 'bg-red-50 dark:bg-red-950/20' : ''}
                  >
                    <TableCell className="pl-6 font-medium">{svc.name}</TableCell>
                    <TableCell>{svc.groupName}</TableCell>
                    <TableCell>{svc.clusterCount}</TableCell>
                    <TableCell>{svc.ipCount}</TableCell>
                    <TableCell>
                      <span className={svc.healthyInstanceCount === 0 && svc.ipCount > 0 ? 'text-destructive font-medium' : ''}>
                        {svc.healthyInstanceCount}
                      </span>
                    </TableCell>
                    <TableCell>
                      {svc.triggerFlag ? (
                        <Badge variant="destructive">{t('service.triggerFlag')}</Badge>
                      ) : (
                        <span className="text-muted-foreground">-</span>
                      )}
                    </TableCell>
                    <TableCell className="pr-6">
                      <div className="flex items-center gap-2">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() =>
                            navigate(`/serviceDetail?serviceName=${encodeURIComponent(svc.name)}&groupName=${encodeURIComponent(svc.groupName)}&namespace=${encodeURIComponent(currentNamespace)}`)
                          }
                        >
                          {t('common.detail')}
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() =>
                            navigate(`/subscriberList?serviceName=${encodeURIComponent(svc.name)}&groupName=${encodeURIComponent(svc.groupName)}&namespace=${encodeURIComponent(currentNamespace)}`)
                          }
                        >
                          {t('service.subscriberName')}
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          className="text-destructive hover:text-destructive"
                          onClick={() => {
                            setDeleteTarget({ name: svc.name, groupName: svc.groupName });
                            setDeleteError(null);
                            setDeleteOpen(true);
                          }}
                        >
                          {t('common.delete')}
                        </Button>
                      </div>
                    </TableCell>
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
            {t('service.totalServices', { total })}
          </div>
          <div className="flex items-center gap-4">
            <div className="flex items-center gap-2">
              <span className="text-sm text-muted-foreground">{t('common.pageSize')}</span>
              <Select value={pageSize.toString()} onValueChange={handlePageSizeChange}>
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
                onClick={() => handlePageChange(pageNo - 1)}
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
                onClick={() => handlePageChange(pageNo + 1)}
                disabled={pageNo >= totalPages}
              >
                <ChevronRight className="h-4 w-4" />
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* Delete Dialog */}
      <Dialog open={deleteOpen} onOpenChange={setDeleteOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('common.delete')}</DialogTitle>
            <DialogDescription>
              {deleteTarget && t('service.deleteConfirm', { name: deleteTarget.name })}
            </DialogDescription>
          </DialogHeader>
          {deleteError && (
            <div className="text-sm text-destructive bg-destructive/10 rounded-md px-3 py-2">
              {deleteError}
            </div>
          )}
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button variant="destructive" onClick={handleDeleteConfirm} disabled={deleting}>
              {t('common.confirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Create Service Dialog */}
      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>{t('service.createService')}</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <Label>
                {t('service.serviceName')}
                <span className="text-destructive ml-1">*</span>
              </Label>
              <Input
                value={createForm.serviceName}
                onChange={(e) => setCreateForm({ ...createForm, serviceName: e.target.value })}
                placeholder={t('service.serviceName')}
              />
            </div>
            <div className="space-y-2">
              <Label>{t('service.groupName')}</Label>
              <Input
                value={createForm.groupName}
                onChange={(e) => setCreateForm({ ...createForm, groupName: e.target.value })}
                placeholder="DEFAULT_GROUP"
              />
            </div>
            <div className="space-y-2">
              <Label>
                {t('service.protectThreshold')}
              </Label>
              <Input
                type="number"
                min={0}
                max={1}
                step={0.01}
                value={createForm.protectThreshold}
                onChange={(e) => setCreateForm({ ...createForm, protectThreshold: parseFloat(e.target.value) || 0 })}
              />
            </div>
            <div className="flex items-center gap-2">
              <Switch
                checked={createForm.ephemeral}
                onCheckedChange={(v) => setCreateForm({ ...createForm, ephemeral: v })}
              />
              <Label>{t('service.ephemeral')}</Label>
            </div>
            <div className="space-y-2">
              <Label>{t('service.selectorType')}</Label>
              <Select
                value={createForm.selectorType}
                onValueChange={(v) => setCreateForm({ ...createForm, selectorType: v })}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {(selectorTypes.length > 0 ? selectorTypes : ['none', 'label']).map((st) => (
                    <SelectItem key={st} value={st}>
                      {st}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            {createForm.selectorType !== 'none' && (
              <div className="space-y-2">
                <Label>{t('service.selectorExpression')}</Label>
                <Input
                  value={createForm.selectorExpression}
                  onChange={(e) => setCreateForm({ ...createForm, selectorExpression: e.target.value })}
                  placeholder={t('service.selectorExpression')}
                />
              </div>
            )}
            <div className="space-y-2">
              <Label>{t('service.metadata')}</Label>
              <Textarea
                value={createForm.metadata}
                onChange={(e) => setCreateForm({ ...createForm, metadata: e.target.value })}
                placeholder='{"key": "value"}'
                rows={3}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCreateOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button onClick={handleCreateSubmit} disabled={creating}>
              {t('common.confirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
