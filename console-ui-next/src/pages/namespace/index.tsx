import { useEffect, useState, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import {
  Plus, RotateCcw, Globe, Info,
} from 'lucide-react';

import { namespaceApi } from '@/api/namespace';
import type { Namespace } from '@/api/namespace';
import { useNamespaceStore } from '@/stores/namespace-store';

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
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';

export default function NamespacePage() {
  const { t } = useTranslation();

  // Data
  const [namespaces, setNamespaces] = useState<Namespace[]>([]);
  const [loading, setLoading] = useState(true);

  // Dialogs
  const [createOpen, setCreateOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);

  // Form state
  const [formId, setFormId] = useState('');
  const [formName, setFormName] = useState('');
  const [formDesc, setFormDesc] = useState('');
  const [saving, setSaving] = useState(false);

  // Selected namespace for edit/delete/detail
  const [selected, setSelected] = useState<Namespace | null>(null);
  const [detailData, setDetailData] = useState<Namespace | null>(null);

  // Fetch global namespace store refresh
  const { fetchNamespaces: refreshGlobalNamespaces } = useNamespaceStore();

  const fetchNamespaces = useCallback(async () => {
    setLoading(true);
    try {
      const response = await namespaceApi.list();
      const body = response as unknown as { code: number; data: Namespace[] };
      setNamespaces(body.data || []);
    } catch {
      // Error handled by interceptor
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchNamespaces();
  }, [fetchNamespaces]);

  // Create
  const handleCreate = async () => {
    if (!formName.trim()) {
      toast.error(t('namespace.nameRequired'));
      return;
    }
    setSaving(true);
    try {
      await namespaceApi.create({
        customNamespaceId: formId.trim(),
        namespaceName: formName.trim(),
        namespaceDesc: formDesc.trim(),
      });
      toast.success(t('namespace.createSuccess'));
      setCreateOpen(false);
      resetForm();
      fetchNamespaces();
      refreshGlobalNamespaces();
    } catch {
      // Error handled by interceptor
    } finally {
      setSaving(false);
    }
  };

  // Edit
  const openEdit = (ns: Namespace) => {
    setSelected(ns);
    setFormName(ns.namespaceShowName);
    setFormDesc(ns.namespaceDesc || '');
    setEditOpen(true);
  };

  const handleEdit = async () => {
    if (!selected || !formName.trim()) {
      toast.error(t('namespace.nameRequired'));
      return;
    }
    setSaving(true);
    try {
      await namespaceApi.update({
        namespaceId: selected.namespace,
        namespaceName: formName.trim(),
        namespaceDesc: formDesc.trim(),
      });
      toast.success(t('namespace.updateSuccess'));
      setEditOpen(false);
      resetForm();
      fetchNamespaces();
      refreshGlobalNamespaces();
    } catch {
      // Error handled by interceptor
    } finally {
      setSaving(false);
    }
  };

  // Delete
  const openDelete = (ns: Namespace) => {
    setSelected(ns);
    setDeleteOpen(true);
  };

  const handleDelete = async () => {
    if (!selected) return;
    try {
      await namespaceApi.remove(selected.namespace);
      toast.success(t('namespace.deleteSuccess'));
      setDeleteOpen(false);
      setSelected(null);
      fetchNamespaces();
      refreshGlobalNamespaces();
    } catch {
      // Error handled by interceptor
    }
  };

  // Detail
  const openDetail = async (ns: Namespace) => {
    setSelected(ns);
    setDetailOpen(true);
    try {
      const response = await namespaceApi.detail(ns.namespace);
      const body = response as unknown as { data: Namespace };
      setDetailData(body.data || ns);
    } catch {
      setDetailData(ns);
    }
  };

  const resetForm = () => {
    setFormId('');
    setFormName('');
    setFormDesc('');
    setSelected(null);
  };

  const isPublic = (ns: Namespace) => ns.type === 0;

  return (
    <div className="flex flex-col gap-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-foreground">{t('namespace.title')}</h1>
        <div className="flex items-center gap-2">
          <Button variant="outline" onClick={fetchNamespaces} className="gap-2">
            <RotateCcw className="h-4 w-4" />
            {t('cluster.refresh')}
          </Button>
          <Button onClick={() => { resetForm(); setCreateOpen(true); }} className="gap-2">
            <Plus className="h-4 w-4" />
            {t('namespace.create')}
          </Button>
        </div>
      </div>

      {/* Table */}
      <Card className="py-0">
        <CardContent className="p-0">
          {loading ? (
            <div className="p-6 space-y-4">
              {Array.from({ length: 4 }).map((_, i) => (
                <Skeleton key={i} className="h-12 w-full" />
              ))}
            </div>
          ) : namespaces.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-16 text-muted-foreground">
              <Globe className="h-10 w-10 mb-3 opacity-40" />
              <p className="text-lg">{t('common.noData')}</p>
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="pl-6 w-[200px]">{t('namespace.name')}</TableHead>
                  <TableHead className="w-[280px]">{t('namespace.id')}</TableHead>
                  <TableHead>{t('namespace.description')}</TableHead>
                  <TableHead className="w-[100px] text-center">{t('namespace.configCount')}</TableHead>
                  <TableHead className="text-right pr-6 w-[200px]">{t('common.operation')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {namespaces.map((ns) => (
                  <TableRow key={ns.namespace || 'public'}>
                    <TableCell className="pl-6 font-medium">
                      <div className="flex items-center gap-2">
                        {ns.namespaceShowName || t('namespace.publicNamespace')}
                        {isPublic(ns) && (
                          <Badge variant="secondary" className="text-[10px]">public</Badge>
                        )}
                      </div>
                    </TableCell>
                    <TableCell>
                      <code className="text-xs bg-muted px-1.5 py-0.5 rounded font-mono">
                        {ns.namespace || '(public)'}
                      </code>
                    </TableCell>
                    <TableCell className="text-muted-foreground max-w-[300px] truncate">
                      {ns.namespaceDesc || (isPublic(ns) ? t('namespace.publicNamespaceDesc') : '-')}
                    </TableCell>
                    <TableCell className="text-center">
                      <Badge variant="outline">{ns.configCount}</Badge>
                    </TableCell>
                    <TableCell className="text-right pr-6">
                      <div className="flex items-center justify-end gap-1">
                        <Button variant="ghost" size="sm" onClick={() => openDetail(ns)}>
                          {t('common.detail')}
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          disabled={isPublic(ns)}
                          onClick={() => openEdit(ns)}
                        >
                          {t('common.edit')}
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          disabled={isPublic(ns)}
                          className={isPublic(ns) ? '' : 'text-destructive hover:text-destructive'}
                          onClick={() => openDelete(ns)}
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

      {/* Footer */}
      {namespaces.length > 0 && (
        <div className="text-sm text-muted-foreground">
          {t('namespace.total', { total: namespaces.length })}
        </div>
      )}

      {/* Create Dialog */}
      <Dialog open={createOpen} onOpenChange={(open) => { setCreateOpen(open); if (!open) resetForm(); }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('namespace.create')}</DialogTitle>
          </DialogHeader>
          <div className="flex flex-col gap-4">
            <div className="flex flex-col gap-2">
              <Label>
                {t('namespace.name')}
                <span className="text-destructive ml-1">*</span>
              </Label>
              <Input
                placeholder={t('namespace.namePlaceholder')}
                value={formName}
                onChange={(e) => setFormName(e.target.value)}
              />
            </div>
            <div className="flex flex-col gap-2">
              <Label className="flex items-center gap-1">
                {t('namespace.id')}
                <Tooltip>
                  <TooltipTrigger asChild>
                    <Info className="h-3.5 w-3.5 text-muted-foreground" />
                  </TooltipTrigger>
                  <TooltipContent>{t('namespace.idPlaceholder')}</TooltipContent>
                </Tooltip>
              </Label>
              <Input
                placeholder={t('namespace.idPlaceholder')}
                value={formId}
                onChange={(e) => setFormId(e.target.value)}
              />
            </div>
            <div className="flex flex-col gap-2">
              <Label>{t('namespace.description')}</Label>
              <Textarea
                placeholder={t('namespace.descriptionPlaceholder')}
                value={formDesc}
                onChange={(e) => setFormDesc(e.target.value)}
                rows={3}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => { setCreateOpen(false); resetForm(); }}>
              {t('common.cancel')}
            </Button>
            <Button onClick={handleCreate} disabled={saving}>
              {saving ? t('common.loading') : t('common.confirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Edit Dialog */}
      <Dialog open={editOpen} onOpenChange={(open) => { setEditOpen(open); if (!open) resetForm(); }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('namespace.edit')}</DialogTitle>
          </DialogHeader>
          <div className="flex flex-col gap-4">
            <div className="flex flex-col gap-2">
              <Label>{t('namespace.id')}</Label>
              <Input value={selected?.namespace || ''} disabled />
            </div>
            <div className="flex flex-col gap-2">
              <Label>
                {t('namespace.name')}
                <span className="text-destructive ml-1">*</span>
              </Label>
              <Input
                placeholder={t('namespace.namePlaceholder')}
                value={formName}
                onChange={(e) => setFormName(e.target.value)}
              />
            </div>
            <div className="flex flex-col gap-2">
              <Label>{t('namespace.description')}</Label>
              <Textarea
                placeholder={t('namespace.descriptionPlaceholder')}
                value={formDesc}
                onChange={(e) => setFormDesc(e.target.value)}
                rows={3}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => { setEditOpen(false); resetForm(); }}>
              {t('common.cancel')}
            </Button>
            <Button onClick={handleEdit} disabled={saving}>
              {saving ? t('common.loading') : t('common.confirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Delete Confirmation */}
      <Dialog open={deleteOpen} onOpenChange={setDeleteOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('common.delete')}</DialogTitle>
            <DialogDescription>
              {t('namespace.deleteConfirm', { name: selected?.namespaceShowName })}
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

      {/* Detail Dialog */}
      <Dialog open={detailOpen} onOpenChange={setDetailOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('namespace.detail')}</DialogTitle>
          </DialogHeader>
          {detailData && (
            <div className="flex flex-col gap-3">
              <div className="flex items-center justify-between rounded-lg border p-3">
                <span className="text-sm text-muted-foreground">{t('namespace.name')}</span>
                <span className="text-sm font-medium">{detailData.namespaceShowName}</span>
              </div>
              <div className="flex items-center justify-between rounded-lg border p-3">
                <span className="text-sm text-muted-foreground">{t('namespace.id')}</span>
                <code className="text-xs bg-muted px-1.5 py-0.5 rounded font-mono">
                  {detailData.namespace || '(public)'}
                </code>
              </div>
              <div className="flex items-center justify-between rounded-lg border p-3">
                <span className="text-sm text-muted-foreground">{t('namespace.configCount')}</span>
                <span className="text-sm font-medium">
                  {detailData.configCount} / {detailData.quota}
                </span>
              </div>
              <div className="flex items-center justify-between rounded-lg border p-3">
                <span className="text-sm text-muted-foreground">{t('namespace.description')}</span>
                <span className="text-sm text-right max-w-[250px]">
                  {detailData.namespaceDesc || '-'}
                </span>
              </div>
            </div>
          )}
          <DialogFooter>
            <Button variant="outline" onClick={() => setDetailOpen(false)}>
              {t('common.cancel')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
