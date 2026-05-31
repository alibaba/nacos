import { useState, useEffect, useCallback } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { ArrowLeft, Pencil, ChevronLeft, ChevronRight, Server, Hash, Shield, ToggleLeft, Filter, Braces, Trash2} from 'lucide-react';

import { serviceApi } from '@/api/service';
import { useServiceStore } from '@/stores/service-store';
import { useNamespaceStore } from '@/stores/namespace-store';
import type { Instance, InstanceListResponse, ClusterInfo, ServiceDetailInfo } from '@/types/service';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Switch } from '@/components/ui/switch';
import { Skeleton } from '@/components/ui/skeleton';
import { Textarea } from '@/components/ui/textarea';
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

const getServiceManagementPath = (namespaceId: string) => {
  const params = new URLSearchParams();
  if (namespaceId) {
    params.set('namespace', namespaceId);
  }
  const query = params.toString();
  return query ? `/serviceManagement?${query}` : '/serviceManagement';
};

export default function ServiceDetailPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { currentNamespace, setNamespaceChangeGuard } = useNamespaceStore();

  const serviceName = searchParams.get('serviceName') || '';
  const groupName = searchParams.get('groupName') || 'DEFAULT_GROUP';
  const activeNamespace =
    searchParams.get('namespace') || searchParams.get('namespaceId') || currentNamespace;

  const {
    currentService,
    detailLoading,
    selectorTypes,
    fetchServiceDetail,
    fetchSelectorTypes,
    clearCurrentService,
  } = useServiceStore();

  // Edit service dialog
  const [editServiceOpen, setEditServiceOpen] = useState(false);
  const [editServiceForm, setEditServiceForm] = useState({
    protectThreshold: 0,
    ephemeral: false,
    selectorType: 'none',
    selectorExpression: '',
    metadata: '',
  });
  const [editServiceSubmitting, setEditServiceSubmitting] = useState(false);

  // Edit cluster dialog
  const [editClusterOpen, setEditClusterOpen] = useState(false);
  const [editClusterName, setEditClusterName] = useState('');
  const [editClusterForm, setEditClusterForm] = useState({
    checkType: 'TCP' as 'TCP' | 'HTTP' | 'NONE',
    checkPort: 80,
    useInstancePort: true,
    checkPath: '',
    checkHeaders: '',
    metadata: '',
  });
  const [editClusterSubmitting, setEditClusterSubmitting] = useState(false);

  // Edit instance dialog
  const [editInstanceOpen, setEditInstanceOpen] = useState(false);
  const [editInstanceCluster, setEditInstanceCluster] = useState('');
  const [editInstanceForm, setEditInstanceForm] = useState({
    ip: '',
    port: 0,
    weight: 1,
    enabled: true,
    ephemeral: false,
    metadata: '',
  });
  const [editInstanceSubmitting, setEditInstanceSubmitting] = useState(false);
  const [deleteInstanceTarget, setDeleteInstanceTarget] = useState<{ clusterName: string; instance: Instance } | null>(null);
  const [deleteInstanceSubmitting, setDeleteInstanceSubmitting] = useState(false);

  // Per-cluster pagination state
  const [clusterPages, setClusterPages] = useState<Record<string, { pageNo: number; pageSize: number }>>({});

  // Per-cluster instance data (fetched separately via API)
  const [instancesByCluster, setInstancesByCluster] = useState<Record<string, { list: Instance[]; total: number; loading: boolean }>>({});

  // Instance toggling state
  const [togglingInstances, setTogglingInstances] = useState<Set<string>>(new Set());

  const closeDetailDialogs = useCallback(() => {
    setEditServiceOpen(false);
    setEditClusterOpen(false);
    setEditInstanceOpen(false);
    setDeleteInstanceTarget(null);
  }, []);

  // Load service detail
  useEffect(() => {
    if (serviceName) {
      fetchServiceDetail(activeNamespace, serviceName, groupName);
    }
    return () => clearCurrentService();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [serviceName, groupName, activeNamespace]);

  useEffect(() => {
    const hasPendingDialog = editServiceOpen
      || editClusterOpen
      || editInstanceOpen
      || deleteInstanceTarget !== null;
    setNamespaceChangeGuard(() => {
      if (!hasPendingDialog) {
        return true;
      }
      if (!window.confirm(t('service.namespaceSwitchConfirm'))) {
        return false;
      }
      closeDetailDialogs();
      return true;
    });
    return () => setNamespaceChangeGuard(null);
  }, [
    closeDetailDialogs,
    deleteInstanceTarget,
    editClusterOpen,
    editInstanceOpen,
    editServiceOpen,
    setNamespaceChangeGuard,
    t,
  ]);

  // Fetch instances for each cluster when service detail loads
  useEffect(() => {
    if (currentService?.clusterMap) {
      Object.keys(currentService.clusterMap).forEach((cn) => {
        fetchClusterInstances(cn);
      });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentService]);

  const fetchClusterInstances = useCallback(async (clusterName: string, pageOverride?: { pageNo: number; pageSize: number }) => {
    const page = pageOverride || clusterPages[clusterName] || { pageNo: 1, pageSize: 10 };
    setInstancesByCluster((prev) => ({
      ...prev,
      [clusterName]: { list: prev[clusterName]?.list || [], total: prev[clusterName]?.total || 0, loading: true },
    }));
    try {
      const response = await serviceApi.listInstances({
        namespaceId: activeNamespace,
        serviceName,
        groupName,
        clusterName,
        pageNo: page.pageNo,
        pageSize: page.pageSize,
      });
      const result = response as unknown as { data: InstanceListResponse };
      const data = result.data;
      setInstancesByCluster((prev) => ({
        ...prev,
        [clusterName]: { list: data.pageItems || [], total: data.totalCount || 0, loading: false },
      }));
    } catch {
      setInstancesByCluster((prev) => ({
        ...prev,
        [clusterName]: { list: [], total: 0, loading: false },
      }));
    }
  }, [activeNamespace, serviceName, groupName, clusterPages]);

  const refreshDetail = () => {
    fetchServiceDetail(activeNamespace, serviceName, groupName);
    // Instances will re-fetch via the useEffect on currentService change
  };

  const refreshClusterInstances = (clusterName: string) => {
    fetchClusterInstances(clusterName);
  };

  // ===== Edit Service =====
  const openEditService = (svc: ServiceDetailInfo) => {
    fetchSelectorTypes();
    const metadata = svc.metadata && Object.keys(svc.metadata).length > 0
      ? JSON.stringify(svc.metadata, null, 2)
      : '';
    setEditServiceForm({
      protectThreshold: svc.protectThreshold,
      ephemeral: svc.ephemeral,
      selectorType: svc.selector?.type || 'none',
      selectorExpression: svc.selector?.expression || '',
      metadata,
    });
    setEditServiceOpen(true);
  };

  const handleEditServiceSubmit = async () => {
    if (editServiceForm.metadata.trim()) {
      try { JSON.parse(editServiceForm.metadata); } catch {
        toast.error(t('service.metadataInvalid'));
        return;
      }
    }
    setEditServiceSubmitting(true);
    try {
      const selectorJson = editServiceForm.selectorType === 'none'
        ? JSON.stringify({ type: 'none' })
        : JSON.stringify({ type: editServiceForm.selectorType, expression: editServiceForm.selectorExpression });
      await serviceApi.updateService({
        namespaceId: activeNamespace,
        serviceName,
        groupName,
        protectThreshold: editServiceForm.protectThreshold,
        ephemeral: editServiceForm.ephemeral,
        metadata: editServiceForm.metadata.trim() || undefined,
        selector: selectorJson,
      });
      toast.success(t('service.updateSuccess'));
      setEditServiceOpen(false);
      refreshDetail();
    } catch { /* interceptor */ } finally {
      setEditServiceSubmitting(false);
    }
  };

  // ===== Edit Cluster =====
  const openEditCluster = (clusterName: string, cluster: ClusterInfo) => {
    const metadata = cluster.metadata && Object.keys(cluster.metadata).length > 0
      ? JSON.stringify(cluster.metadata, null, 2)
      : '';
    setEditClusterName(clusterName);
    setEditClusterForm({
      checkType: cluster.healthChecker?.type || 'TCP',
      checkPort: cluster.healthyCheckPort || 80,
      useInstancePort: cluster.useInstancePortForCheck ?? true,
      checkPath: cluster.healthChecker?.path || '',
      checkHeaders: cluster.healthChecker?.headers || '',
      metadata,
    });
    setEditClusterOpen(true);
  };

  const handleEditClusterSubmit = async () => {
    if (editClusterForm.metadata.trim()) {
      try { JSON.parse(editClusterForm.metadata); } catch {
        toast.error(t('service.metadataInvalid'));
        return;
      }
    }
    setEditClusterSubmitting(true);
    try {
      const healthCheckerObj: Record<string, unknown> = { type: editClusterForm.checkType };
      if (editClusterForm.checkType === 'HTTP') {
        healthCheckerObj.path = editClusterForm.checkPath;
        healthCheckerObj.headers = editClusterForm.checkHeaders;
      }
      await serviceApi.updateCluster({
        namespaceId: activeNamespace,
        serviceName,
        groupName,
        clusterName: editClusterName,
        checkPort: editClusterForm.checkPort,
        useInstancePort4Check: editClusterForm.useInstancePort,
        healthChecker: JSON.stringify(healthCheckerObj),
        metadata: editClusterForm.metadata.trim() || undefined,
      });
      toast.success(t('service.clusterUpdateSuccess'));
      setEditClusterOpen(false);
      refreshDetail();
    } catch { /* interceptor */ } finally {
      setEditClusterSubmitting(false);
    }
  };

  // ===== Edit Instance =====
  const openEditInstance = (clusterName: string, inst: Instance) => {
    const metadata = inst.metadata && Object.keys(inst.metadata).length > 0
      ? JSON.stringify(inst.metadata, null, 2)
      : '';
    setEditInstanceCluster(clusterName);
    setEditInstanceForm({
      ip: inst.ip,
      port: inst.port,
      weight: inst.weight,
      enabled: inst.enabled,
      ephemeral: inst.ephemeral,
      metadata,
    });
    setEditInstanceOpen(true);
  };

  const handleEditInstanceSubmit = async () => {
    if (editInstanceForm.metadata.trim()) {
      try { JSON.parse(editInstanceForm.metadata); } catch {
        toast.error(t('service.metadataInvalid'));
        return;
      }
    }
    setEditInstanceSubmitting(true);
    try {
      await serviceApi.updateInstance({
        namespaceId: activeNamespace,
        serviceName,
        groupName,
        clusterName: editInstanceCluster,
        ip: editInstanceForm.ip,
        port: editInstanceForm.port,
        weight: editInstanceForm.weight,
        enabled: editInstanceForm.enabled,
        ephemeral: editInstanceForm.ephemeral,
        metadata: editInstanceForm.metadata.trim() || undefined,
      });
      toast.success(t('service.instanceUpdateSuccess'));
      setEditInstanceOpen(false);
      refreshDetail();
    } catch { /* interceptor */ } finally {
      setEditInstanceSubmitting(false);
    }
  };

  // ===== Toggle Instance Online/Offline =====
  const toggleInstance = async (clusterName: string, inst: Instance) => {
    const key = `${inst.ip}:${inst.port}`;
    setTogglingInstances((prev) => new Set(prev).add(key));
    try {
      const metadata = inst.metadata && Object.keys(inst.metadata).length > 0
        ? JSON.stringify(inst.metadata)
        : undefined;
      await serviceApi.updateInstance({
        namespaceId: activeNamespace,
        serviceName,
        groupName,
        clusterName,
        ip: inst.ip,
        port: inst.port,
        weight: inst.weight,
        enabled: !inst.enabled,
        ephemeral: inst.ephemeral,
        metadata,
      });
      toast.success(t('service.instanceUpdateSuccess'));
      refreshClusterInstances(clusterName);
    } catch { /* interceptor */ } finally {
      setTogglingInstances((prev) => {
        const next = new Set(prev);
        next.delete(key);
        return next;
      });
    }
  };

  const handleDeleteInstance = async () => {
    if (!deleteInstanceTarget) return;
    const { clusterName, instance } = deleteInstanceTarget;
    setDeleteInstanceSubmitting(true);
    try {
      await serviceApi.deleteInstance({
        namespaceId: activeNamespace,
        serviceName,
        groupName,
        clusterName,
        ip: instance.ip,
        port: instance.port,
        ephemeral: instance.ephemeral,
      });
      toast.success(t('service.instanceDeleteSuccess'));
      setDeleteInstanceTarget(null);
      const currentPage = getClusterPage(clusterName);
      const currentInstances = instancesByCluster[clusterName]?.list || [];
      if (currentInstances.length === 1 && currentPage.pageNo > 1) {
        const previousPage = { ...currentPage, pageNo: currentPage.pageNo - 1 };
        setClusterPages((prev) => ({
          ...prev,
          [clusterName]: previousPage,
        }));
        fetchClusterInstances(clusterName, previousPage);
      } else {
        refreshClusterInstances(clusterName);
      }
      refreshDetail();
    } catch { /* interceptor */ } finally {
      setDeleteInstanceSubmitting(false);
    }
  };

  // ===== Cluster pagination helpers =====
  const getClusterPage = (clusterName: string) =>
    clusterPages[clusterName] || { pageNo: 1, pageSize: 10 };

  const setClusterPage = (clusterName: string, pageNo: number, pageSize?: number) => {
    const newPage = {
      pageNo,
      pageSize: pageSize ?? (clusterPages[clusterName]?.pageSize || 10),
    };
    setClusterPages((prev) => ({
      ...prev,
      [clusterName]: newPage,
    }));
    fetchClusterInstances(clusterName, newPage);
  };

  // ===== Render =====
  if (detailLoading && !currentService) {
    return (
      <div className="flex flex-col gap-4">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-48 w-full" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  if (!currentService) {
    return (
      <div className="flex flex-col gap-4">
        <Button variant="ghost" onClick={() => navigate(getServiceManagementPath(activeNamespace))} className="gap-2 w-fit">
          <ArrowLeft className="h-4 w-4" />
          {t('common.back')}
        </Button>
        <div className="flex items-center justify-center py-16 text-muted-foreground">
          <p className="text-lg">{t('common.noData')}</p>
        </div>
      </div>
    );
  }

  const clusterEntries = Object.entries(currentService.clusterMap || {});

  return (
    <div className="flex flex-col gap-4">
      {/* Back + Title */}
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="sm" onClick={() => navigate(getServiceManagementPath(activeNamespace))}>
          <ArrowLeft className="h-4 w-4" />
        </Button>
        <h1 className="text-2xl font-semibold text-foreground">{serviceName}</h1>
      </div>

      {/* Service Info Card */}
      <Card className="overflow-hidden">
        <CardContent className="p-0">
          {/* Hero identity banner — Service Name + Group prominently displayed */}
          <div className="px-6 py-5 border-b border-border/60">
            <div className="flex items-start justify-between gap-4">
              <div className="min-w-0 flex-1 flex flex-wrap items-start gap-x-10 gap-y-3">
                {/* Service Name */}
                <div className="min-w-0">
                  <div className="flex items-center gap-2 mb-1">
                    <Server className="h-3.5 w-3.5 text-muted-foreground shrink-0" />
                    <span className="text-xs font-medium uppercase tracking-wider text-muted-foreground">{t('service.serviceName')}</span>
                  </div>
                  <div className="text-base font-semibold tracking-tight break-all leading-snug">
                    {currentService.serviceName}
                  </div>
                </div>
                {/* Group Name */}
                <div className="min-w-0">
                  <div className="flex items-center gap-2 mb-1">
                    <Hash className="h-3.5 w-3.5 text-muted-foreground shrink-0" />
                    <span className="text-xs font-medium uppercase tracking-wider text-muted-foreground">{t('service.groupName')}</span>
                  </div>
                  <div className="text-base font-semibold tracking-tight break-all">
                    {currentService.groupName}
                  </div>
                </div>
              </div>
              {/* Edit button */}
              <Button variant="outline" size="sm" onClick={() => openEditService(currentService)} className="shrink-0 gap-2">
                <Pencil className="h-3.5 w-3.5" />
                {t('common.edit')}
              </Button>
            </div>
          </div>

          {/* Metadata grid */}
          <div className="px-6 py-5 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
            {/* Protect Threshold */}
            <div className="flex items-start gap-3">
              <Shield className="h-4 w-4 text-muted-foreground mt-0.5 shrink-0" />
              <div className="min-w-0">
                <div className="text-xs font-medium text-muted-foreground mb-0.5">{t('service.protectThreshold')}</div>
                <div className="text-sm font-mono tabular-nums">{currentService.protectThreshold}</div>
              </div>
            </div>

            {/* Ephemeral */}
            <div className="flex items-start gap-3">
              <ToggleLeft className="h-4 w-4 text-muted-foreground mt-0.5 shrink-0" />
              <div className="min-w-0">
                <div className="text-xs font-medium text-muted-foreground mb-1">{t('service.ephemeral')}</div>
                <span className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold ${
                  currentService.ephemeral
                    ? 'bg-emerald-100 text-emerald-800 border-emerald-200 dark:bg-emerald-900/30 dark:text-emerald-300 dark:border-emerald-800'
                    : 'bg-gray-100 text-gray-700 border-gray-200 dark:bg-gray-800/50 dark:text-gray-300 dark:border-gray-700'
                }`}>
                  {currentService.ephemeral ? 'true' : 'false'}
                </span>
              </div>
            </div>

            {/* Selector Type */}
            <div className="flex items-start gap-3">
              <Filter className="h-4 w-4 text-muted-foreground mt-0.5 shrink-0" />
              <div className="min-w-0">
                <div className="text-xs font-medium text-muted-foreground mb-0.5">{t('service.selectorType')}</div>
                <div className="text-sm">{currentService.selector?.type || 'none'}</div>
              </div>
            </div>

            {/* Selector Expression — conditional */}
            {currentService.selector?.type && currentService.selector.type !== 'none' && (
              <div className="flex items-start gap-3 sm:col-span-2 lg:col-span-3">
                <Filter className="h-4 w-4 text-muted-foreground mt-0.5 shrink-0" />
                <div className="min-w-0">
                  <div className="text-xs font-medium text-muted-foreground mb-0.5">{t('service.selectorExpression')}</div>
                  <div className="text-sm font-mono text-[13px] tracking-tight break-all">{currentService.selector.expression || '-'}</div>
                </div>
              </div>
            )}

            {/* Metadata — conditional, spans full width */}
            {currentService.metadata && Object.keys(currentService.metadata).length > 0 && (
              <>
                <div className="sm:col-span-2 lg:col-span-3 border-t border-dashed border-border/60" />
                <div className="flex items-start gap-3 sm:col-span-2 lg:col-span-3">
                  <Braces className="h-4 w-4 text-muted-foreground mt-0.5 shrink-0" />
                  <div className="min-w-0">
                    <div className="text-xs font-medium text-muted-foreground mb-1">{t('service.metadata')}</div>
                    <div className="flex flex-wrap gap-1.5">
                      {Object.entries(currentService.metadata).map(([k, v]) => (
                        <span key={k} className="inline-flex items-center rounded-full bg-secondary px-2.5 py-0.5 text-xs font-medium text-secondary-foreground ring-1 ring-inset ring-border/50">
                          {k}: {v}
                        </span>
                      ))}
                    </div>
                  </div>
                </div>
              </>
            )}
          </div>
        </CardContent>
      </Card>

      {/* Cluster Cards */}
      {clusterEntries.map(([clusterName, cluster]) => {
        const instData = instancesByCluster[clusterName] || { list: [], total: 0, loading: false };
        return (
          <ClusterCard
            key={clusterName}
            clusterName={clusterName}
            instances={instData.list}
            instanceTotal={instData.total}
            instanceLoading={instData.loading}
            page={getClusterPage(clusterName)}
            onPageChange={(pn, ps) => setClusterPage(clusterName, pn, ps)}
            onEditCluster={() => openEditCluster(clusterName, cluster)}
            onEditInstance={(inst) => openEditInstance(clusterName, inst)}
            onToggleInstance={(inst) => toggleInstance(clusterName, inst)}
            onDeleteInstance={(inst) => setDeleteInstanceTarget({ clusterName, instance: inst })}
            togglingInstances={togglingInstances}
            t={t}
          />
        );
      })}

      {clusterEntries.length === 0 && (
        <Card>
          <CardContent className="flex items-center justify-center py-12 text-muted-foreground">
            {t('service.noInstances')}
          </CardContent>
        </Card>
      )}

      {/* Edit Service Dialog */}
      <Dialog open={editServiceOpen} onOpenChange={setEditServiceOpen}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>{t('service.editService')}</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <Label>{t('service.serviceName')}</Label>
              <Input value={serviceName} disabled />
            </div>
            <div className="space-y-2">
              <Label>{t('service.groupName')}</Label>
              <Input value={groupName} disabled />
            </div>
            <div className="space-y-2">
              <Label>{t('service.protectThreshold')}</Label>
              <Input
                type="number"
                min={0}
                max={1}
                step={0.01}
                value={editServiceForm.protectThreshold}
                onChange={(e) => setEditServiceForm({ ...editServiceForm, protectThreshold: parseFloat(e.target.value) || 0 })}
              />
            </div>
            <div className="flex items-center gap-2">
              <Switch
                checked={editServiceForm.ephemeral}
                onCheckedChange={(v) => setEditServiceForm({ ...editServiceForm, ephemeral: v })}
              />
              <Label>{t('service.ephemeral')}</Label>
            </div>
            <div className="space-y-2">
              <Label>{t('service.selectorType')}</Label>
              <Select
                value={editServiceForm.selectorType}
                onValueChange={(v) => setEditServiceForm({ ...editServiceForm, selectorType: v })}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {(selectorTypes.length > 0 ? selectorTypes : ['none', 'label']).map((st) => (
                    <SelectItem key={st} value={st}>{st}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            {editServiceForm.selectorType !== 'none' && (
              <div className="space-y-2">
                <Label>{t('service.selectorExpression')}</Label>
                <Input
                  value={editServiceForm.selectorExpression}
                  onChange={(e) => setEditServiceForm({ ...editServiceForm, selectorExpression: e.target.value })}
                />
              </div>
            )}
            <div className="space-y-2">
              <Label>{t('service.metadata')}</Label>
              <Textarea
                value={editServiceForm.metadata}
                onChange={(e) => setEditServiceForm({ ...editServiceForm, metadata: e.target.value })}
                placeholder='{"key": "value"}'
                rows={4}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setEditServiceOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button onClick={handleEditServiceSubmit} disabled={editServiceSubmitting}>
              {t('common.confirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Edit Cluster Dialog */}
      <Dialog open={editClusterOpen} onOpenChange={setEditClusterOpen}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>{t('service.editCluster')} - {editClusterName}</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <Label>{t('service.checkType')}</Label>
              <Select
                value={editClusterForm.checkType}
                onValueChange={(v) => setEditClusterForm({ ...editClusterForm, checkType: v as 'TCP' | 'HTTP' | 'NONE' })}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="TCP">TCP</SelectItem>
                  <SelectItem value="HTTP">HTTP</SelectItem>
                  <SelectItem value="NONE">NONE</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label>{t('service.checkPort')}</Label>
              <Input
                type="number"
                value={editClusterForm.checkPort}
                onChange={(e) => setEditClusterForm({ ...editClusterForm, checkPort: parseInt(e.target.value) || 0 })}
                disabled={editClusterForm.useInstancePort}
              />
            </div>
            <div className="flex items-center gap-2">
              <Switch
                checked={editClusterForm.useInstancePort}
                onCheckedChange={(v) => setEditClusterForm({ ...editClusterForm, useInstancePort: v })}
              />
              <Label>{t('service.useInstancePort')}</Label>
            </div>
            {editClusterForm.checkType === 'HTTP' && (
              <>
                <div className="space-y-2">
                  <Label>{t('service.checkPath')}</Label>
                  <Input
                    value={editClusterForm.checkPath}
                    onChange={(e) => setEditClusterForm({ ...editClusterForm, checkPath: e.target.value })}
                    placeholder="/health"
                  />
                </div>
                <div className="space-y-2">
                  <Label>{t('service.checkHeaders')}</Label>
                  <Input
                    value={editClusterForm.checkHeaders}
                    onChange={(e) => setEditClusterForm({ ...editClusterForm, checkHeaders: e.target.value })}
                  />
                </div>
              </>
            )}
            <div className="space-y-2">
              <Label>{t('service.metadata')}</Label>
              <Textarea
                value={editClusterForm.metadata}
                onChange={(e) => setEditClusterForm({ ...editClusterForm, metadata: e.target.value })}
                placeholder='{"key": "value"}'
                rows={3}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setEditClusterOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button onClick={handleEditClusterSubmit} disabled={editClusterSubmitting}>
              {t('common.confirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Edit Instance Dialog */}
      <Dialog open={editInstanceOpen} onOpenChange={setEditInstanceOpen}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>{t('service.editInstance')}</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <Label>{t('service.ip')}</Label>
              <Input value={editInstanceForm.ip} disabled />
            </div>
            <div className="space-y-2">
              <Label>{t('service.port')}</Label>
              <Input value={editInstanceForm.port} disabled />
            </div>
            <div className="space-y-2">
              <Label>{t('service.weight')}</Label>
              <Input
                type="number"
                min={0}
                step={0.1}
                value={editInstanceForm.weight}
                onChange={(e) => setEditInstanceForm({ ...editInstanceForm, weight: parseFloat(e.target.value) || 0 })}
              />
            </div>
            <div className="flex items-center gap-2">
              <Switch
                checked={editInstanceForm.enabled}
                onCheckedChange={(v) => setEditInstanceForm({ ...editInstanceForm, enabled: v })}
              />
              <Label>{t('service.enabled')}</Label>
            </div>
            <div className="space-y-2">
              <Label>{t('service.metadata')}</Label>
              <Textarea
                value={editInstanceForm.metadata}
                onChange={(e) => setEditInstanceForm({ ...editInstanceForm, metadata: e.target.value })}
                placeholder='{"key": "value"}'
                rows={4}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setEditInstanceOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button onClick={handleEditInstanceSubmit} disabled={editInstanceSubmitting}>
              {t('common.confirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Delete Instance Dialog */}
      <Dialog open={!!deleteInstanceTarget} onOpenChange={(open) => !open && setDeleteInstanceTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('service.deleteInstance')}</DialogTitle>
            <DialogDescription>
              {deleteInstanceTarget
                ? t('service.instanceDeleteConfirm', {
                    ip: deleteInstanceTarget.instance.ip,
                    port: deleteInstanceTarget.instance.port,
                  })
                : ''}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteInstanceTarget(null)}>
              {t('common.cancel')}
            </Button>
            <Button variant="destructive" onClick={handleDeleteInstance} disabled={deleteInstanceSubmitting}>
              {t('common.delete')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

// ===== Helper Components =====

function ClusterCard({
  clusterName,
  instances,
  instanceTotal,
  instanceLoading,
  page,
  onPageChange,
  onEditCluster,
  onEditInstance,
  onToggleInstance,
  onDeleteInstance,
  togglingInstances,
  t,
}: {
  clusterName: string;
  instances: Instance[];
  instanceTotal: number;
  instanceLoading: boolean;
  page: { pageNo: number; pageSize: number };
  onPageChange: (pageNo: number, pageSize?: number) => void;
  onEditCluster: () => void;
  onEditInstance: (inst: Instance) => void;
  onToggleInstance: (inst: Instance) => void;
  onDeleteInstance: (inst: Instance) => void;
  togglingInstances: Set<string>;
  t: (key: string, options?: Record<string, unknown>) => string;
}) {
  const totalPages = Math.max(1, Math.ceil(instanceTotal / page.pageSize));

  return (
    <Card className="py-0">
      <CardHeader className="flex flex-row items-center justify-between py-4">
        <CardTitle className="text-base flex items-center gap-2">
          {t('service.clusterName')}: {clusterName}
          <Badge variant="outline">{instanceTotal} {t('service.ipCount').toLowerCase()}</Badge>
        </CardTitle>
        <Button variant="outline" size="sm" onClick={onEditCluster} className="gap-2">
          <Pencil className="h-3.5 w-3.5" />
          {t('service.editCluster')}
        </Button>
      </CardHeader>
      <CardContent className="p-0">
        {instanceLoading && instances.length === 0 ? (
          <div className="p-6 space-y-3">
            {Array.from({ length: 3 }).map((_, i) => (
              <Skeleton key={i} className="h-10 w-full" />
            ))}
          </div>
        ) : instances.length === 0 ? (
          <div className="flex items-center justify-center py-8 text-muted-foreground text-sm">
            {t('service.noInstances')}
          </div>
        ) : (
          <>
            <Table className={instanceLoading ? 'opacity-50 pointer-events-none' : ''}>
              <TableHeader>
                <TableRow>
                  <TableHead className="pl-6">{t('service.ip')}</TableHead>
                  <TableHead>{t('service.port')}</TableHead>
                  <TableHead>{t('service.instanceEphemeral')}</TableHead>
                  <TableHead>{t('service.weight')}</TableHead>
                  <TableHead>{t('service.healthy')}</TableHead>
                  <TableHead>{t('service.metadata')}</TableHead>
                  <TableHead className="pr-6">{t('common.operation')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {instances.map((inst) => {
                  const key = `${inst.ip}:${inst.port}`;
                  const toggling = togglingInstances.has(key);
                  return (
                    <TableRow
                      key={key}
                      className={
                        inst.healthy
                          ? 'bg-green-50/50 dark:bg-green-950/10'
                          : 'bg-red-50 dark:bg-red-950/20'
                      }
                    >
                      <TableCell className="pl-6 font-medium">{inst.ip}</TableCell>
                      <TableCell>{inst.port}</TableCell>
                      <TableCell>{inst.ephemeral ? 'true' : 'false'}</TableCell>
                      <TableCell>{inst.weight}</TableCell>
                      <TableCell>
                        <Badge variant={inst.healthy ? 'default' : 'destructive'}>
                          {inst.healthy ? t('service.healthy') : 'Unhealthy'}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        <div className="flex flex-wrap gap-1 max-w-[200px]">
                          {inst.metadata && Object.keys(inst.metadata).length > 0
                            ? Object.entries(inst.metadata).map(([k, v]) => (
                                <Badge key={k} variant="secondary" className="text-xs">
                                  {k}={v}
                                </Badge>
                              ))
                            : <span className="text-muted-foreground text-sm">-</span>}
                        </div>
                      </TableCell>
                      <TableCell className="pr-6">
                        <div className="flex items-center gap-2">
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => onEditInstance(inst)}
                          >
                            {t('common.edit')}
                          </Button>
                          <Button
                            variant={inst.enabled ? 'ghost' : 'outline'}
                            size="sm"
                            disabled={toggling}
                            onClick={() => onToggleInstance(inst)}
                            className={inst.enabled ? 'text-destructive hover:text-destructive' : ''}
                          >
                            {inst.enabled ? t('service.offline') : t('service.online')}
                          </Button>
                          {!inst.ephemeral && (
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={() => onDeleteInstance(inst)}
                              className="text-destructive hover:text-destructive"
                              aria-label={t('service.deleteInstance')}
                            >
                              <Trash2 className="h-3.5 w-3.5" />
                            </Button>
                          )}
                        </div>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>

            {/* Per-cluster Pagination */}
            {instanceTotal > page.pageSize && (
              <div className="flex items-center justify-end gap-4 px-6 py-3 border-t">
                <div className="flex items-center gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => onPageChange(page.pageNo - 1)}
                    disabled={page.pageNo <= 1}
                  >
                    <ChevronLeft className="h-4 w-4" />
                  </Button>
                  <span className="text-sm text-muted-foreground min-w-[60px] text-center">
                    {page.pageNo} / {totalPages}
                  </span>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => onPageChange(page.pageNo + 1)}
                    disabled={page.pageNo >= totalPages}
                  >
                    <ChevronRight className="h-4 w-4" />
                  </Button>
                </div>
              </div>
            )}
          </>
        )}
      </CardContent>
    </Card>
  );
}
