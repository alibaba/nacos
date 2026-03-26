import { useEffect, useState, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import {
  Plus, Search, RotateCcw, ChevronLeft, ChevronRight, Key,
} from 'lucide-react';

import { authApi } from '@/api/auth';
import type { RoleItem, UserItem } from '@/api/auth';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent } from '@/components/ui/card';
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
import { Badge } from '@/components/ui/badge';
import { ComboInput } from '@/components/ui/combo-input';

export default function RolesManagementPage() {
  const { t } = useTranslation();

  const [roles, setRoles] = useState<RoleItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  // Search
  const [searchRole, setSearchRole] = useState('');
  const [searchUsername, setSearchUsername] = useState('');
  const [localRole, setLocalRole] = useState('');
  const [localUsername, setLocalUsername] = useState('');
  const [searchMode, setSearchMode] = useState<string>('blur');

  // Create dialog
  const [createOpen, setCreateOpen] = useState(false);
  const [newRole, setNewRole] = useState('');
  const [newUsername, setNewUsername] = useState('');
  const [saving, setSaving] = useState(false);
  const [allUsers, setAllUsers] = useState<UserItem[]>([]);
  const [usersLoading, setUsersLoading] = useState(false);

  // Delete dialog
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [roleToDelete, setRoleToDelete] = useState<RoleItem | null>(null);

  const fetchRoles = useCallback(async () => {
    setLoading(true);
    try {
      const response = await authApi.listRoles({
        pageNo,
        pageSize,
        role: searchRole || undefined,
        username: searchUsername || undefined,
        search: searchMode === 'blur' ? 'blur' : 'accurate',
      });
      const body = response as unknown as { data: { pageItems: RoleItem[]; totalCount: number } };
      const data = body.data || { pageItems: [], totalCount: 0 };
      setRoles(data.pageItems || []);
      setTotal(data.totalCount || 0);
    } catch {
      setRoles([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [pageNo, pageSize, searchRole, searchUsername, searchMode]);

  useEffect(() => {
    fetchRoles();
  }, [fetchRoles]);

  const fetchAllUsers = useCallback(async () => {
    setUsersLoading(true);
    try {
      const response = await authApi.listUsers({ pageNo: 1, pageSize: 500, search: 'blur' });
      const body = response as unknown as { data: { pageItems: UserItem[]; totalCount: number } };
      setAllUsers(body.data?.pageItems || []);
    } catch {
      setAllUsers([]);
    } finally {
      setUsersLoading(false);
    }
  }, []);

  const handleSearch = () => {
    setSearchRole(localRole);
    setSearchUsername(localUsername);
    setPageNo(1);
  };

  const handleReset = () => {
    setLocalRole('');
    setLocalUsername('');
    setSearchRole('');
    setSearchUsername('');
    setSearchMode('blur');
    setPageNo(1);
  };

  const handleCreate = async () => {
    if (!newRole.trim()) { toast.error(t('authority.roleRequired')); return; }
    if (!newUsername.trim()) { toast.error(t('authority.usernameRequired')); return; }
    setSaving(true);
    try {
      await authApi.createRole({ role: newRole.trim(), username: newUsername.trim() });
      toast.success(t('authority.createRoleSuccess'));
      setCreateOpen(false);
      setNewRole('');
      setNewUsername('');
      fetchRoles();
    } catch {
      // Error handled by interceptor
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!roleToDelete) return;
    try {
      await authApi.deleteRole({ role: roleToDelete.role, username: roleToDelete.username });
      toast.success(t('authority.deleteRoleSuccess'));
      setDeleteOpen(false);
      setRoleToDelete(null);
      fetchRoles();
    } catch {
      // Error handled by interceptor
    }
  };

  const isAdmin = (role: RoleItem) => role.role === 'ROLE_ADMIN';
  const totalPages = Math.ceil(total / pageSize);

  return (
    <div className="flex flex-col gap-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-foreground">{t('authority.roleManagement')}</h1>
        <Button onClick={() => { setNewRole(''); setNewUsername(''); fetchAllUsers(); setCreateOpen(true); }} className="gap-2">
          <Plus className="h-4 w-4" />
          {t('authority.bindRole')}
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
                className="w-[200px]"
                onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
              />
            </div>
            <div className="flex flex-col gap-2">
              <label className="text-sm font-medium text-muted-foreground">
                {t('authority.username')}
              </label>
              <Input
                placeholder={t('authority.searchUserPlaceholder')}
                value={localUsername}
                onChange={(e) => setLocalUsername(e.target.value)}
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
          {loading && roles.length === 0 ? (
            <div className="p-6 space-y-4">
              {Array.from({ length: 4 }).map((_, i) => (
                <Skeleton key={i} className="h-12 w-full" />
              ))}
            </div>
          ) : roles.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-16 text-muted-foreground">
              <Key className="h-10 w-10 mb-3 opacity-40" />
              <p className="text-lg">{t('common.noData')}</p>
            </div>
          ) : (
            <div className={loading ? 'opacity-50 pointer-events-none transition-opacity' : 'transition-opacity'}>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="pl-6">{t('authority.role')}</TableHead>
                    <TableHead>{t('authority.username')}</TableHead>
                    <TableHead className="text-right pr-6">{t('common.operation')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {roles.map((role, idx) => (
                    <TableRow key={`${role.role}-${role.username}-${idx}`}>
                      <TableCell className="pl-6 font-medium">
                        <div className="flex items-center gap-2">
                          {role.role}
                          {isAdmin(role) && (
                            <Badge className="bg-amber-500/15 text-amber-600 border-amber-200 hover:bg-amber-500/15 text-[10px]">
                              Admin
                            </Badge>
                          )}
                        </div>
                      </TableCell>
                      <TableCell>{role.username}</TableCell>
                      <TableCell className="text-right pr-6">
                        <Button
                          variant="ghost"
                          size="sm"
                          disabled={isAdmin(role)}
                          className={isAdmin(role) ? '' : 'text-destructive hover:text-destructive'}
                          onClick={() => { setRoleToDelete(role); setDeleteOpen(true); }}
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
            {t('authority.totalRoles', { total })}
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

      {/* Create Role Dialog */}
      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('authority.bindRole')}</DialogTitle>
          </DialogHeader>
          <div className="flex flex-col gap-4">
            <div className="flex flex-col gap-2">
              <Label>
                {t('authority.role')}
                <span className="text-destructive ml-1">*</span>
              </Label>
              <Input
                placeholder={t('authority.rolePlaceholder')}
                value={newRole}
                onChange={(e) => setNewRole(e.target.value)}
              />
            </div>
            <div className="flex flex-col gap-2">
              <Label>
                {t('authority.username')}
                <span className="text-destructive ml-1">*</span>
              </Label>
              <ComboInput
                value={newUsername}
                onChange={setNewUsername}
                options={allUsers.map((u) => ({ value: u.username, label: u.username }))}
                placeholder={t('authority.selectUserPlaceholder')}
                loading={usersLoading}
                loadingText={t('common.loading')}
              />
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

      {/* Delete Role Dialog */}
      <Dialog open={deleteOpen} onOpenChange={setDeleteOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('common.delete')}</DialogTitle>
            <DialogDescription>
              {t('authority.deleteRoleConfirm', {
                role: roleToDelete?.role,
                username: roleToDelete?.username,
              })}
            </DialogDescription>
          </DialogHeader>
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
