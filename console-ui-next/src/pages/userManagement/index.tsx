import { useEffect, useState, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import {
  Plus, Search, RotateCcw, ChevronLeft, ChevronRight, Users, KeyRound,
} from 'lucide-react';

import { authApi } from '@/api/auth';
import type { UserItem } from '@/api/auth';

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

export default function UserManagementPage() {
  const { t } = useTranslation();

  const [users, setUsers] = useState<UserItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  // Search
  const [searchUsername, setSearchUsername] = useState('');
  const [localSearch, setLocalSearch] = useState('');
  const [searchMode, setSearchMode] = useState<string>('blur');

  // Create dialog
  const [createOpen, setCreateOpen] = useState(false);
  const [newUsername, setNewUsername] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [newConfirmPassword, setNewConfirmPassword] = useState('');
  const [saving, setSaving] = useState(false);

  // Delete dialog
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [userToDelete, setUserToDelete] = useState<string>('');

  // Reset password dialog
  const [resetOpen, setResetOpen] = useState(false);
  const [resetUsername, setResetUsername] = useState('');
  const [resetNewPassword, setResetNewPassword] = useState('');
  const [resetConfirmPassword, setResetConfirmPassword] = useState('');

  const fetchUsers = useCallback(async () => {
    setLoading(true);
    try {
      const response = await authApi.listUsers({
        pageNo,
        pageSize,
        username: searchUsername || undefined,
        search: searchMode === 'blur' ? 'blur' : 'accurate',
      });
      const body = response as unknown as { data: { pageItems: UserItem[]; totalCount: number } };
      const data = body.data || { pageItems: [], totalCount: 0 };
      setUsers(data.pageItems || []);
      setTotal(data.totalCount || 0);
    } catch {
      setUsers([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [pageNo, pageSize, searchUsername, searchMode]);

  useEffect(() => {
    fetchUsers();
  }, [fetchUsers]);

  const handleSearch = () => {
    setSearchUsername(localSearch);
    setPageNo(1);
  };

  const handleReset = () => {
    setLocalSearch('');
    setSearchUsername('');
    setSearchMode('blur');
    setPageNo(1);
  };

  const handleCreateUser = async () => {
    if (!newUsername.trim()) { toast.error(t('authority.usernameRequired')); return; }
    if (!newPassword.trim()) { toast.error(t('authority.passwordRequired')); return; }
    if (newPassword !== newConfirmPassword) { toast.error(t('authority.passwordMismatch')); return; }
    setSaving(true);
    try {
      await authApi.createUser({ username: newUsername.trim(), password: newPassword.trim() });
      toast.success(t('authority.createUserSuccess'));
      setCreateOpen(false);
      setNewUsername('');
      setNewPassword('');
      setNewConfirmPassword('');
      fetchUsers();
    } catch {
      // Error handled by interceptor
    } finally {
      setSaving(false);
    }
  };

  const handleDeleteUser = async () => {
    if (!userToDelete) return;
    try {
      await authApi.deleteUser(userToDelete);
      toast.success(t('authority.deleteUserSuccess'));
      setDeleteOpen(false);
      setUserToDelete('');
      fetchUsers();
    } catch {
      // Error handled by interceptor
    }
  };

  const handleResetPassword = async () => {
    if (!resetNewPassword.trim()) { toast.error(t('authority.passwordRequired')); return; }
    if (resetNewPassword !== resetConfirmPassword) { toast.error(t('authority.passwordMismatch')); return; }
    setSaving(true);
    try {
      await authApi.resetPassword({ username: resetUsername, newPassword: resetNewPassword.trim() });
      toast.success(t('authority.resetPasswordSuccess'));
      setResetOpen(false);
      setResetUsername('');
      setResetNewPassword('');
      setResetConfirmPassword('');
    } catch {
      // Error handled by interceptor
    } finally {
      setSaving(false);
    }
  };

  const totalPages = Math.ceil(total / pageSize);

  return (
    <div className="flex flex-col gap-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-foreground">{t('authority.userManagement')}</h1>
        <Button onClick={() => { setNewUsername(''); setNewPassword(''); setNewConfirmPassword(''); setCreateOpen(true); }} className="gap-2">
          <Plus className="h-4 w-4" />
          {t('authority.createUser')}
        </Button>
      </div>

      {/* Search */}
      <Card className="py-0">
        <CardContent className="py-4">
          <div className="flex flex-wrap items-end gap-4">
            <div className="flex flex-col gap-2">
              <label className="text-sm font-medium text-muted-foreground">
                {t('authority.username')}
              </label>
              <Input
                placeholder={t('authority.searchUserPlaceholder')}
                value={localSearch}
                onChange={(e) => setLocalSearch(e.target.value)}
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
          {loading && users.length === 0 ? (
            <div className="p-6 space-y-4">
              {Array.from({ length: 4 }).map((_, i) => (
                <Skeleton key={i} className="h-12 w-full" />
              ))}
            </div>
          ) : users.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-16 text-muted-foreground">
              <Users className="h-10 w-10 mb-3 opacity-40" />
              <p className="text-lg">{t('common.noData')}</p>
            </div>
          ) : (
            <div className={loading ? 'opacity-50 pointer-events-none transition-opacity' : 'transition-opacity'}>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="pl-6">{t('authority.username')}</TableHead>
                    <TableHead>{t('authority.password')}</TableHead>
                    <TableHead className="text-right pr-6">{t('common.operation')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {users.map((user) => (
                    <TableRow key={user.username}>
                      <TableCell className="pl-6 font-medium">{user.username}</TableCell>
                      <TableCell className="text-muted-foreground">********</TableCell>
                      <TableCell className="text-right pr-6">
                        <div className="flex items-center justify-end gap-1">
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => {
                              setResetUsername(user.username);
                              setResetNewPassword('');
                              setResetConfirmPassword('');
                              setResetOpen(true);
                            }}
                          >
                            <KeyRound className="h-3.5 w-3.5 mr-1" />
                            {t('authority.resetPassword')}
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            className="text-destructive hover:text-destructive"
                            onClick={() => { setUserToDelete(user.username); setDeleteOpen(true); }}
                          >
                            {t('common.delete')}
                          </Button>
                        </div>
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
            {t('authority.totalUsers', { total })}
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

      {/* Create User Dialog */}
      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('authority.createUser')}</DialogTitle>
          </DialogHeader>
          <div className="flex flex-col gap-4">
            <div className="flex flex-col gap-2">
              <Label>
                {t('authority.username')}
                <span className="text-destructive ml-1">*</span>
              </Label>
              <Input
                placeholder={t('authority.usernamePlaceholder')}
                value={newUsername}
                onChange={(e) => setNewUsername(e.target.value)}
              />
            </div>
            <div className="flex flex-col gap-2">
              <Label>
                {t('authority.password')}
                <span className="text-destructive ml-1">*</span>
              </Label>
              <Input
                type="password"
                placeholder={t('authority.passwordPlaceholder')}
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
              />
            </div>
            <div className="flex flex-col gap-2">
              <Label>
                {t('authority.confirmPassword')}
                <span className="text-destructive ml-1">*</span>
              </Label>
              <Input
                type="password"
                placeholder={t('authority.confirmPasswordPlaceholder')}
                value={newConfirmPassword}
                onChange={(e) => setNewConfirmPassword(e.target.value)}
              />
              {newConfirmPassword && newPassword !== newConfirmPassword && (
                <p className="text-xs text-destructive">{t('authority.passwordMismatch')}</p>
              )}
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCreateOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button onClick={handleCreateUser} disabled={saving || !newPassword || newPassword !== newConfirmPassword}>
              {saving ? t('common.loading') : t('common.confirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Delete User Dialog */}
      <Dialog open={deleteOpen} onOpenChange={setDeleteOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('authority.deleteUser')}</DialogTitle>
            <DialogDescription>
              {t('authority.deleteUserConfirm', { name: userToDelete })}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button variant="destructive" onClick={handleDeleteUser}>
              {t('common.confirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Reset Password Dialog */}
      <Dialog open={resetOpen} onOpenChange={setResetOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('authority.resetPassword')}</DialogTitle>
          </DialogHeader>
          <div className="flex flex-col gap-4">
            <div className="flex flex-col gap-2">
              <Label>{t('authority.username')}</Label>
              <Input value={resetUsername} disabled />
            </div>
            <div className="flex flex-col gap-2">
              <Label>
                {t('authority.newPassword')}
                <span className="text-destructive ml-1">*</span>
              </Label>
              <Input
                type="password"
                placeholder={t('authority.newPasswordPlaceholder')}
                value={resetNewPassword}
                onChange={(e) => setResetNewPassword(e.target.value)}
              />
            </div>
            <div className="flex flex-col gap-2">
              <Label>
                {t('authority.confirmPassword')}
                <span className="text-destructive ml-1">*</span>
              </Label>
              <Input
                type="password"
                placeholder={t('authority.newPasswordPlaceholder')}
                value={resetConfirmPassword}
                onChange={(e) => setResetConfirmPassword(e.target.value)}
              />
              {resetConfirmPassword && resetNewPassword !== resetConfirmPassword && (
                <p className="text-xs text-destructive">{t('authority.passwordMismatch')}</p>
              )}
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setResetOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button onClick={handleResetPassword} disabled={saving || !resetNewPassword || resetNewPassword !== resetConfirmPassword}>
              {saving ? t('common.loading') : t('common.confirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
