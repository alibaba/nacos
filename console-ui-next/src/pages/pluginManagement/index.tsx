import { useEffect, useState, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import {
  Puzzle, RotateCcw,
} from 'lucide-react';

import { pluginApi } from '@/api/plugin';
import type { PluginInfo } from '@/api/plugin';

import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table';
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select';
import { Switch } from '@/components/ui/switch';

// Known plugin type i18n keys
const PLUGIN_TYPE_KEYS: Record<string, string> = {
  'auth': 'plugin.typeAuth',
  'datasource-dialect': 'plugin.typeDatasource',
  'config-change': 'plugin.typeConfigChange',
  'encryption': 'plugin.typeEncryption',
  'trace': 'plugin.typeTrace',
  'environment': 'plugin.typeEnvironment',
  'control': 'plugin.typeControl',
  'ai-pipeline': 'plugin.typeAiPipeline',
  'ai-storage': 'plugin.typeAiStorage',
  'visibility': 'plugin.typeVisibility',
};

export default function PluginManagementPage() {
  const { t } = useTranslation();

  const [plugins, setPlugins] = useState<PluginInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [filterType, setFilterType] = useState('all');
  const [detailOpen, setDetailOpen] = useState(false);
  const [selectedPlugin, setSelectedPlugin] = useState<PluginInfo | null>(null);

  // Derive plugin types from data
  const pluginTypes = [...new Set(plugins.map((p) => p.pluginType))].sort();

  const getTypeLabel = (type: string) => {
    const key = PLUGIN_TYPE_KEYS[type];
    return key ? t(key) : type;
  };

  const fetchPlugins = useCallback(async () => {
    setLoading(true);
    try {
      const typeParam = filterType === 'all' ? undefined : filterType;
      const response = await pluginApi.list(typeParam);
      const body = response as unknown as { data: PluginInfo[] };
      setPlugins(body.data || []);
    } catch {
      setPlugins([]);
    } finally {
      setLoading(false);
    }
  }, [filterType]);

  useEffect(() => {
    fetchPlugins();
  }, [fetchPlugins]);

  const handleToggleStatus = async (plugin: PluginInfo) => {
    const newEnabled = !plugin.enabled;
    try {
      await pluginApi.setStatus({
        pluginType: plugin.pluginType,
        pluginName: plugin.pluginName,
        enabled: newEnabled,
      });
      toast.success(newEnabled ? t('plugin.enableSuccess') : t('plugin.disableSuccess'));
      fetchPlugins();
    } catch {
      // Error handled by interceptor
    }
  };

  const canSwitch = (plugin: PluginInfo) => !plugin.critical && !plugin.exclusive;

  const filteredPlugins = filterType === 'all'
    ? plugins
    : plugins.filter((p) => p.pluginType === filterType);

  return (
    <div className="flex flex-col gap-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-foreground">{t('plugin.title')}</h1>
        <Button variant="outline" onClick={fetchPlugins} className="gap-2">
          <RotateCcw className="h-4 w-4" />
          {t('cluster.refresh')}
        </Button>
      </div>

      {/* Filter */}
      <Card className="py-0">
        <CardContent className="py-4">
          <div className="flex items-end gap-4">
            <div className="flex flex-col gap-2">
              <label className="text-sm font-medium text-muted-foreground">
                {t('plugin.pluginType')}
              </label>
              <Select value={filterType} onValueChange={setFilterType}>
                <SelectTrigger className="w-[200px]">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">{t('plugin.allTypes')}</SelectItem>
                  {pluginTypes.map((type) => (
                    <SelectItem key={type} value={type}>{getTypeLabel(type)}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Table */}
      <Card className="py-0">
        <CardContent className="p-0">
          {loading ? (
            <div className="p-6 space-y-4">
              {Array.from({ length: 4 }).map((_, i) => (
                <Skeleton key={i} className="h-12 w-full" />
              ))}
            </div>
          ) : filteredPlugins.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-16 text-muted-foreground">
              <Puzzle className="h-10 w-10 mb-3 opacity-40" />
              <p className="text-lg">{t('plugin.noPlugins')}</p>
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="pl-6">{t('plugin.pluginName')}</TableHead>
                  <TableHead>{t('plugin.pluginType')}</TableHead>
                  <TableHead>{t('plugin.status')}</TableHead>
                  <TableHead>{t('plugin.critical')}</TableHead>
                  <TableHead>{t('plugin.availableNodes')}</TableHead>
                  <TableHead className="text-right pr-6">{t('common.operation')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {filteredPlugins.map((plugin) => (
                  <TableRow key={plugin.pluginId || `${plugin.pluginType}-${plugin.pluginName}`}>
                    <TableCell className="pl-6 font-medium">{plugin.pluginName}</TableCell>
                    <TableCell>
                      <Badge variant="outline">{getTypeLabel(plugin.pluginType)}</Badge>
                    </TableCell>
                    <TableCell>
                      <div className="flex items-center gap-2">
                        {canSwitch(plugin) ? (
                          <Switch
                            checked={plugin.enabled}
                            onCheckedChange={() => handleToggleStatus(plugin)}
                          />
                        ) : null}
                        <span className={plugin.enabled ? 'text-emerald-600 text-sm' : 'text-muted-foreground text-sm'}>
                          {plugin.enabled ? t('plugin.enabled') : t('plugin.disabled')}
                        </span>
                      </div>
                    </TableCell>
                    <TableCell>
                      {plugin.critical && (
                        <Badge className="bg-amber-500/15 text-amber-600 border-amber-200 hover:bg-amber-500/15">
                          {t('plugin.critical')}
                        </Badge>
                      )}
                    </TableCell>
                    <TableCell>
                      <span className="text-sm">
                        {plugin.availableNodeCount} / {plugin.totalNodeCount}
                      </span>
                    </TableCell>
                    <TableCell className="text-right pr-6">
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => { setSelectedPlugin(plugin); setDetailOpen(true); }}
                      >
                        {t('common.detail')}
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {/* Footer */}
      {filteredPlugins.length > 0 && (
        <div className="text-sm text-muted-foreground">
          {t('plugin.total', { total: filteredPlugins.length })}
        </div>
      )}

      {/* Detail Dialog */}
      <Dialog open={detailOpen} onOpenChange={setDetailOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('plugin.detail')}</DialogTitle>
          </DialogHeader>
          {selectedPlugin && (
            <div className="flex flex-col gap-3">
              <div className="flex items-center justify-between rounded-lg border p-3">
                <span className="text-sm text-muted-foreground">{t('plugin.pluginName')}</span>
                <span className="text-sm font-medium">{selectedPlugin.pluginName}</span>
              </div>
              <div className="flex items-center justify-between rounded-lg border p-3">
                <span className="text-sm text-muted-foreground">{t('plugin.pluginType')}</span>
                <Badge variant="outline">{getTypeLabel(selectedPlugin.pluginType)}</Badge>
              </div>
              <div className="flex items-center justify-between rounded-lg border p-3">
                <span className="text-sm text-muted-foreground">{t('plugin.status')}</span>
                <Badge className={selectedPlugin.enabled
                  ? 'bg-emerald-500/15 text-emerald-600 border-emerald-200 hover:bg-emerald-500/15'
                  : 'bg-red-500/15 text-red-600 border-red-200 hover:bg-red-500/15'
                }>
                  {selectedPlugin.enabled ? t('plugin.enabled') : t('plugin.disabled')}
                </Badge>
              </div>
              <div className="flex items-center justify-between rounded-lg border p-3">
                <span className="text-sm text-muted-foreground">{t('plugin.critical')}</span>
                <span className="text-sm">{selectedPlugin.critical ? t('plugin.yes') : t('plugin.no')}</span>
              </div>
              <div className="flex items-center justify-between rounded-lg border p-3">
                <span className="text-sm text-muted-foreground">{t('plugin.configurable')}</span>
                <span className="text-sm">{selectedPlugin.configurable ? t('plugin.yes') : t('plugin.no')}</span>
              </div>
              <div className="flex items-center justify-between rounded-lg border p-3">
                <span className="text-sm text-muted-foreground">{t('plugin.availableNodes')}</span>
                <span className="text-sm font-medium">{selectedPlugin.availableNodeCount} / {selectedPlugin.totalNodeCount}</span>
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
