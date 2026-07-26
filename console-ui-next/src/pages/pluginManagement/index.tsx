import { useEffect, useState, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import {
  Activity,
  Bot,
  ChevronDown,
  ChevronRight,
  Cloud,
  Database,
  Eye,
  FileEdit,
  HardDrive,
  Lock,
  Puzzle,
  RotateCcw,
  Settings2,
  Shield,
} from 'lucide-react';

import { pluginApi } from '@/api/plugin';
import type { PluginInfo } from '@/api/plugin';
import { PluginDetailDialog } from './plugin-detail-dialog';

import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
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
  'ai-resource-import': 'plugin.typeAiResourceImport',
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
  'ai-resource-import': FileEdit,
};

export default function PluginManagementPage() {
  const { t } = useTranslation();

  const [plugins, setPlugins] = useState<PluginInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [expandedTypes, setExpandedTypes] = useState<Record<string, boolean>>({});
  const [detailOpen, setDetailOpen] = useState(false);
  const [selectedPlugin, setSelectedPlugin] = useState<PluginInfo | null>(null);
  const [statusPlugin, setStatusPlugin] = useState<PluginInfo | null>(null);
  const [statusLocalOnly, setStatusLocalOnly] = useState(false);
  const [statusUpdating, setStatusUpdating] = useState(false);

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

  const requestStatusChange = (plugin: PluginInfo) => {
    setStatusPlugin(plugin);
    setStatusLocalOnly(false);
  };

  const handleToggleStatus = async () => {
    if (!statusPlugin) {
      return;
    }
    const newEnabled = !statusPlugin.enabled;
    setStatusUpdating(true);
    try {
      const response = await pluginApi.setStatus({
        pluginType: statusPlugin.pluginType,
        pluginName: statusPlugin.pluginName,
        enabled: newEnabled,
        localOnly: statusLocalOnly,
      });
      if (response.code !== 0) {
        toast.error(response.message);
        return;
      }
      toast.success(newEnabled ? t('plugin.enableSuccess') : t('plugin.disableSuccess'));
      setStatusPlugin(null);
      await fetchPlugins();
    } catch {
      // Error handled by interceptor
    } finally {
      setStatusUpdating(false);
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
                  <div className="divide-y md:hidden">
                    {items.map(plugin => (
                      <div
                        key={plugin.pluginId || `${plugin.pluginType}-${plugin.pluginName}`}
                        className="space-y-3 px-4 py-3"
                      >
                        <div className="flex items-start justify-between gap-3">
                          <div className="flex min-w-0 flex-wrap items-center gap-2">
                            <span className="truncate font-medium">{plugin.pluginName}</span>
                            {plugin.critical ? (
                              <Badge className="bg-amber-500/15 text-amber-600 border-amber-200 hover:bg-amber-500/15">
                                {t('plugin.critical')}
                              </Badge>
                            ) : null}
                          </div>
                          <div className="flex shrink-0 items-center gap-2">
                            {canSwitch(plugin) ? (
                              <Switch
                                aria-label={`${plugin.pluginName} ${t('plugin.status')}`}
                                checked={plugin.enabled}
                                onCheckedChange={() => requestStatusChange(plugin)}
                              />
                            ) : null}
                            <span className={plugin.enabled ? 'text-emerald-600 text-sm' : 'text-muted-foreground text-sm'}>
                              {plugin.enabled ? t('plugin.enabled') : t('plugin.disabled')}
                            </span>
                          </div>
                        </div>
                        <div className="flex items-center justify-between gap-3 text-sm">
                          <span className="text-muted-foreground">
                            {t('plugin.availableNodes')}: {plugin.availableNodeCount}
                            {' / '}
                            {plugin.totalNodeCount}
                          </span>
                          <Button
                            variant="ghost"
                            size="sm"
                            className="h-auto px-0"
                            onClick={() => { setSelectedPlugin(plugin); setDetailOpen(true); }}
                          >
                            {t('common.detail')}
                          </Button>
                        </div>
                      </div>
                    ))}
                  </div>
                  <div className="hidden md:block">
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
                                    aria-label={`${plugin.pluginName} ${t('plugin.status')}`}
                                    checked={plugin.enabled}
                                    onCheckedChange={() => requestStatusChange(plugin)}
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
                  </div>
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

      <PluginDetailDialog
        open={detailOpen}
        plugin={selectedPlugin}
        onOpenChange={open => {
          setDetailOpen(open);
          if (!open) {
            setSelectedPlugin(null);
          }
        }}
        onUpdated={fetchPlugins}
      />

      <Dialog
        open={statusPlugin !== null}
        onOpenChange={open => {
          if (!open) {
            setStatusPlugin(null);
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {statusPlugin?.enabled
                ? t('plugin.confirmDisable')
                : t('plugin.confirmEnable')}
            </DialogTitle>
            <DialogDescription>
              {t('plugin.statusChangeConfirm', {
                plugin: statusPlugin?.pluginName || '',
              })}
            </DialogDescription>
          </DialogHeader>
          <div className="flex items-center justify-between border-y py-3">
            <div>
              <div className="text-sm font-medium">{t('plugin.localOnlyStatus')}</div>
              <div className="mt-1 text-xs text-muted-foreground">
                {t('plugin.localOnlyStatusHint')}
              </div>
            </div>
            <Switch
              aria-label={t('plugin.localOnlyStatus')}
              checked={statusLocalOnly}
              onCheckedChange={setStatusLocalOnly}
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setStatusPlugin(null)}>
              {t('common.cancel')}
            </Button>
            <Button onClick={handleToggleStatus} disabled={statusUpdating}>
              {t('common.confirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
