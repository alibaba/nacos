import { useEffect, useState, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import {
  Plus, Search, RotateCcw, ChevronLeft, ChevronRight, Lock,
} from 'lucide-react';

import { authApi } from '@/api/auth';
import type { PermissionItem, RoleItem } from '@/api/auth';
import { namespaceApi } from '@/api/namespace';
import type { Namespace } from '@/api/namespace';

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
import { Label } from '@/components/ui/label';
import { ComboInput } from '@/components/ui/combo-input';

const ACTION_OPTIONS = [
  { value: 'r', labelKey: 'authority.actionRead' },
  { value: 'w', labelKey: 'authority.actionWrite' },
  { value: 'rw', labelKey: 'authority.actionReadWrite' },
];

export default function PermissionsManagementPage() {
  const { t } = useTranslation();

  const [permissions, setPermissions] = useState<PermissionItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  // Search
  const [searchRole, setSearchRole] = useState('');
  const [localRole, setLocalRole] = useState('');
  const [searchMode, setSearchMode] = useState<string>('blur');

  // Create dialog
  const [createOpen, setCreateOpen] = useState(false);
  const [newRole, setNewRole] = useState('');
  const [newResource, setNewResource] = useState('');
  const [newAction, setNewAction] = useState('');
  const [saving, setSaving] = useState(false);

  // Namespace list for resource dropdown
  const [namespaces, setNamespaces] = useState<Namespace[]>([]);

  // Role list for role dropdown
  const [allRoles, setAllRoles] = useState<string[]>([]);
  const [rolesLoading, setRolesLoading] = useState(false);

  // Delete dialog
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [permToDelete, setPermToDelete] = useState<PermissionItem | null>(null);

  const fetchPermissions = useCallback(async () => {
    setLoading(true);
    try {
      const response = await authApi.listPermissions({
        pageNo,
        pageSize,
        role: searchRole || undefined,
        search: searchMode === 'blur' ? 'blur' : 'accurate',
      });
      const body = response as unknown as { data: { pageItems: PermissionItem[]; totalCount: number } };
      const data = body.data || { pageItems: [], totalCount: 0 };
      setPermissions(data.pageItems || []);
      setTotal(data.totalCount || 0);
    } catch {
      setPermissions([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [pageNo, pageSize, searchRole, searchMode]);

  const fetchNamespaces = useCallback(async () => {
    try {
      const response = await namespaceApi.list();
      const body = response as unknown as { code: number; data: Namespace[] };
      setNamespaces(body.data || []);
    } catch {
      // silently fail
    }
  }, []);

  useEffect(() => {
    fetchPermissions();
  }, [fetchPermissions]);

  useEffect(() => {
    fetchNamespaces();
  }, [fetchNamespaces]);

  const fetchAllRoles = useCallback(async () => {
    setRolesLoading(true);
    try {
      const response = await authApi.listRoles({ pageNo: 1, pageSize: 500, search: 'blur' });
      const body = response as unknown as { data: { pageItems: RoleItem[]; totalCount: number } };
      const items = body.data?.pageItems || [];
      const uniqueRoles = [...new Set(items.map((r) => r.role))];
      setAllRoles(uniqueRoles);
    } catch {
      setAllRoles([]);
    } finally {
      setRolesLoading(false);
    }
  }, []);

  const handleSearch = () => {
    setSearchRole(localRole);
    setPageNo(1);
  };

  const handleReset = () => {
    setLocalRole('');
    setSearchRole('');
    setSearchMode('blur');
    setPageNo(1);
  };

  const handleCreate = async () => {
    if (!newRole.trim()) { toast.error(t('authority.roleRequired')); return; }
    if (!newResource) { toast.error(t('authority.resourceRequired')); return; }
    if (!newAction) { toast.error(t('authority.actionRequired')); return; }
    setSaving(true);
    try {
      await authApi.createPermission({
        role: newRole.trim(),
        resource: newResource,
        action: newAction,
      });
      toast.success(t('authority.createPermissionSuccess'));
      setCreateOpen(false);
      setNewRole('');
      setNewResource('');
      setNewAction('');
      fetchPermissions();
    } catch {
      // Error handled by interceptor
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!permToDelete) return;
    try {
      await authApi.deletePermission({
        role: permToDelete.role,
        resource: permToDelete.resource,
        action: permToDelete.action,
      });
      toast.success(t('authority.deletePermissionSuccess'));
      setDeleteOpen(false);
      setPermToDelete(null);
      fetchPermissions();
    } catch {
      // Error handled by interceptor
    }
  };

  const getActionLabel = (action: string) => {
    const opt = ACTION_OPTIONS.find((o) => o.value === action);
    return opt ? t(opt.labelKey) : action;
  };

  const getActionBadgeClass = (action: string) => {
    if (action === 'rw') return 'bg-emerald-500/15 text-emerald-600 border-emerald-200 hover:bg-emerald-500/15';
    if (action === 'r') return 'bg-blue-500/15 text-blue-600 border-blue-200 hover:bg-blue-500/15';
    if (action === 'w') return 'bg-amber-500/15 text-amber-600 border-amber-200 hover:bg-amber-500/15';
    return '';
  };

  const totalPages = Math.ceil(total / pageSize);

  // Build resource options from namespaces
  const resourceOptions = namespaces.map((ns) => ({
    value: `${ns.namespace}:*:*`,
    label: `${ns.namespaceShowName || 'public'} (${ns.namespace || 'public'})`,
  }));

  return (
    <div className="flex flex-col gap-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-foreground">{t('authority.permissionManagement')}</h1>
        <Button onClick={() => { setNewRole(''); setNewResource(''); setNewAction(''); fetchAllRoles(); setCreateOpen(true); }} className="gap-2">
          <Plus className="h-4 w-4" />
          {t('authority.addPermission')}
        </Button>
      </div>

      {/* Search */}
      <Card className="py-0">
        <CardContent className="py-4">
          <div className="flex flex-wrap items-end gap-4">
            <div className="flex flex-col gap-2">
              <label className="text-sm font-medium text-muted-foreground">
                {t('authority.role')}
              </label>
              <Input
                placeholder={t('authority.searchRolePlaceholder')}
                value={localRole}
                onChange={(e) => setLocalRole(e.target.value)}
                className="w-[250px]"
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
                  variant={searchMode === 'blur' ? 'default' : 'ghost'}
                  size="sm"
                  onClick={() => setSearchMode('blur')}
                  className="rounded-none rounded-l-md"
                >
                  {t('authority.fuzzySearch')}
                </Button>
                <Button
                  type="button"
                  variant={searchMode === 'accurate' ? 'default' : 'ghost'}
                  size="sm"
                  onClick={() => setSearchMode('accurate')}
                  className="rounded-none rounded-r-md"
                >
                  {t('authority.exactSearch')}
                </Button>
              </div>
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
          {loading && permissions.length === 0 ? (
            <div className="p-6 space-y-4">
              {Array.from({ length: 4 }).map((_, i) => (
                <Skeleton key={i} className="h-12 w-full" />
              ))}
            </div>
          ) : permissions.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-16 text-muted-foreground">
              <Lock className="h-10 w-10 mb-3 opacity-40" />
              <p className="text-lg">{t('common.noData')}</p>
            </div>
          ) : (
            <div className={loading ? 'opacity-50 pointer-events-none transition-opacity' : 'transition-opacity'}>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="pl-6">{t('authority.role')}</TableHead>
                    <TableHead>{t('authority.resource')}</TableHead>
                    <TableHead>{t('authority.action')}</TableHead>
                    <TableHead className="text-right pr-6">{t('common.operation')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {permissions.map((perm, idx) => (
                    <TableRow key={`${perm.role}-${perm.resource}-${perm.action}-${idx}`}>
                      <TableCell className="pl-6 font-medium">{perm.role}</TableCell>
                      <TableCell>
                        <code className="text-xs bg-muted px-1.5 py-0.5 rounded font-mono">
                          {perm.resource}
                        </code>
                      </TableCell>
                      <TableCell>
                        <Badge className={getActionBadgeClass(perm.action)}>
                          {getActionLabel(perm.action)}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-right pr-6">
                        <Button
                          variant="ghost"
                          size="sm"
                          className="text-destructive hover:text-destructive"
                          onClick={() => { setPermToDelete(perm); setDeleteOpen(true); }}
                        >
                          {t('common.delete')}
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
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
            {t('authority.totalPermissions', { total })}
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
              <Button variant="outline" size="sm" onClick={() => setPageNo(pageNo - 1)} disabled={pageNo <= 1}>
                <ChevronLeft className="h-4 w-4" />
              </Button>
              <span className="text-sm text-muted-foreground min-w-[80px] text-center">
                {pageNo} / {totalPages || 1}
              </span>
              <Button variant="outline" size="sm" onClick={() => setPageNo(pageNo + 1)} disabled={pageNo >= totalPages}>
                <ChevronRight className="h-4 w-4" />
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* Create Permission Dialog */}
      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('authority.addPermission')}</DialogTitle>
          </DialogHeader>
          <div className="flex flex-col gap-4">
            <div className="flex flex-col gap-2">
              <Label>
                {t('authority.role')}
                <span className="text-destructive ml-1">*</span>
              </Label>
              <ComboInput
                value={newRole}
                onChange={setNewRole}
                options={allRoles.map((r) => ({ value: r, label: r }))}
                placeholder={t('authority.selectRolePlaceholder')}
                loading={rolesLoading}
                loadingText={t('common.loading')}
              />
            </div>
            <div className="flex flex-col gap-2">
              <Label>
                {t('authority.resource')}
                <span className="text-destructive ml-1">*</span>
              </Label>
              <Select value={newResource} onValueChange={setNewResource}>
                <SelectTrigger>
                  <SelectValue placeholder={t('authority.resourcePlaceholder')} />
                </SelectTrigger>
                <SelectContent>
                  {resourceOptions.map((opt) => (
                    <SelectItem key={opt.value} value={opt.value}>
                      {opt.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="flex flex-col gap-2">
              <Label>
                {t('authority.action')}
                <span className="text-destructive ml-1">*</span>
              </Label>
              <Select value={newAction} onValueChange={setNewAction}>
                <SelectTrigger>
                  <SelectValue placeholder={t('authority.actionRequired')} />
                </SelectTrigger>
                <SelectContent>
                  {ACTION_OPTIONS.map((opt) => (
                    <SelectItem key={opt.value} value={opt.value}>
                      {t(opt.labelKey)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCreateOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button onClick={handleCreate} disabled={saving}>
              {saving ? t('common.loading') : t('common.confirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Delete Permission Dialog */}
      <Dialog open={deleteOpen} onOpenChange={setDeleteOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('common.delete')}</DialogTitle>
            <DialogDescription>
              {t('authority.deletePermissionConfirm')}
            </DialogDescription>
          </DialogHeader>
          {permToDelete && (
            <div className="flex flex-col gap-2 rounded-lg border p-3 text-sm">
              <div className="flex justify-between">
                <span className="text-muted-foreground">{t('authority.role')}</span>
                <span className="font-medium">{permToDelete.role}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">{t('authority.resource')}</span>
                <code className="text-xs bg-muted px-1.5 py-0.5 rounded font-mono">{permToDelete.resource}</code>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">{t('authority.action')}</span>
                <Badge className={getActionBadgeClass(permToDelete.action)}>
                  {getActionLabel(permToDelete.action)}
                </Badge>
              </div>
            </div>
          )}
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button variant="destructive" onClick={handleDelete}>
              {t('common.confirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
