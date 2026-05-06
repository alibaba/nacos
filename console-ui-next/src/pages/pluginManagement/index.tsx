import { useEffect, useState, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import {
  Puzzle, RotateCcw, Shield, Database, FileEdit, Lock, Activity,
  Cloud, Settings2, Eye, Bot, HardDrive, ChevronRight, ChevronDown,
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

const PLUGIN_TYPE_ICONS: Record<string, React.ComponentType<{ className?: string }>> = {
  'auth': Shield,
  'datasource-dialect': Database,
  'config-change': FileEdit,
  'encryption': Lock,
  'trace': Activity,
  'environment': Cloud,
  'control': Settings2,
  'visibility': Eye,
  'ai-pipeline': Bot,
  'ai-storage': HardDrive,
};

export default function PluginManagementPage() {
  const { t } = useTranslation();

  const [plugins, setPlugins] = useState<PluginInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [expandedTypes, setExpandedTypes] = useState<Record<string, boolean>>({});
  const [detailOpen, setDetailOpen] = useState(false);
  const [selectedPlugin, setSelectedPlugin] = useState<PluginInfo | null>(null);

  const getTypeLabel = (type: string) => {
    const key = PLUGIN_TYPE_KEYS[type];
    return key ? t(key) : type;
  };

  const fetchPlugins = useCallback(async () => {
    setLoading(true);
    try {
      const response = await pluginApi.list(undefined);
      const body = response as unknown as { data: PluginInfo[] };
      setPlugins(body.data || []);
    } catch {
      setPlugins([]);
    } finally {
      setLoading(false);
    }
  }, []);

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

  const toggleType = (type: string) => {
    setExpandedTypes(prev => ({ ...prev, [type]: !prev[type] }));
  };

  // Group plugins by type
  const groupedPlugins = plugins.reduce<Record<string, PluginInfo[]>>((acc, plugin) => {
    const type = plugin.pluginType;
    if (!acc[type]) {
      acc[type] = [];
    }
    acc[type].push(plugin);
    return acc;
  }, {});

  const renderTypeCards = () => {
    const types = Object.keys(groupedPlugins).sort();

    if (loading) {
      return (
        <div className="flex flex-col gap-3">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-20 w-full rounded-xl" />
          ))}
        </div>
      );
    }

    if (types.length === 0) {
      return (
        <div className="flex flex-col items-center justify-center py-16 text-muted-foreground">
          <Puzzle className="h-10 w-10 mb-3 opacity-40" />
          <p className="text-lg">{t('plugin.noPlugins')}</p>
        </div>
      );
    }

    return (
      <div className="flex flex-col gap-3">
        {types.map((type) => {
          const items = groupedPlugins[type];
          const enabledCount = items.filter((p) => p.enabled).length;
          const IconComponent = PLUGIN_TYPE_ICONS[type] || Puzzle;
          const expanded = !!expandedTypes[type];
          return (
            <Card key={type} className="py-0 gap-0 overflow-hidden">
              <div
                className={`cursor-pointer transition-colors hover:bg-muted/50 ${expanded ? 'border-b' : ''}`}
                onClick={() => toggleType(type)}
              >
                <CardContent className="py-3 px-5">
                  <div className="flex items-center gap-3">
                    <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary/10">
                      <IconComponent className="h-4 w-4 text-primary" />
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="font-semibold text-sm">{getTypeLabel(type)}</div>
                      <div className="text-xs text-muted-foreground mt-1">
                        <span>{items.length} {t('plugin.pluginCount')}</span>
                        <span className="mx-1.5">·</span>
                        <span className="text-emerald-600">{enabledCount} {t('plugin.enabled')}</span>
                      </div>
                    </div>
                    {expanded
                      ? <ChevronDown className="h-4 w-4 text-muted-foreground/50" />
                      : <ChevronRight className="h-4 w-4 text-muted-foreground/50" />
                    }
                  </div>
                </CardContent>
              </div>
              {expanded && (
                <CardContent className="p-0">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead className="pl-6 w-[30%]">{t('plugin.pluginName')}</TableHead>
                        <TableHead className="w-[20%] text-center">{t('plugin.status')}</TableHead>
                        <TableHead className="w-[15%] text-center">{t('plugin.critical')}</TableHead>
                        <TableHead className="w-[20%] text-center">{t('plugin.availableNodes')}</TableHead>
                        <TableHead className="w-[15%] text-center">{t('common.operation')}</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {items.map((plugin) => (
                        <TableRow key={plugin.pluginId || `${plugin.pluginType}-${plugin.pluginName}`}>
                          <TableCell className="pl-6 font-medium">{plugin.pluginName}</TableCell>
                          <TableCell>
                            <div className="flex items-center justify-center gap-2">
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
                          <TableCell className="text-center">
                            {plugin.critical && (
                              <Badge className="bg-amber-500/15 text-amber-600 border-amber-200 hover:bg-amber-500/15">
                                {t('plugin.critical')}
                              </Badge>
                            )}
                          </TableCell>
                          <TableCell className="text-center">
                            <span className="text-sm">
                              {plugin.availableNodeCount} / {plugin.totalNodeCount}
                            </span>
                          </TableCell>
                          <TableCell className="text-center">
                            <Button
                              variant="ghost"
                              size="sm"
                              className="h-auto px-0"
                              onClick={() => { setSelectedPlugin(plugin); setDetailOpen(true); }}
                            >
                              {t('common.detail')}
                            </Button>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </CardContent>
              )}
            </Card>
          );
        })}
      </div>
    );
  };

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

      {renderTypeCards()}

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
