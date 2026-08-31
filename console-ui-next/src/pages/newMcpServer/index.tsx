import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import {
  ArrowLeft,
  Plus,
  Trash2,
  Shield,
  Info,
  Server,
  Terminal,
  Globe,
  Cpu,
  Radio,
} from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { Separator } from '@/components/ui/separator';
import { Skeleton } from '@/components/ui/skeleton';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { useNamespaceStore } from '@/stores/namespace-store';
import { useMcpStore } from '@/stores/mcp-store';
import { mcpApi } from '@/api/mcp';
import { serviceApi } from '@/api/service';
import type {
  McpProtocol,
  McpSecurityScheme,
  McpServerDetailInfo,
  McpServerVersionDetail,
  McpToolSpecification,
} from '@/types/mcp';
import { cn } from '@/lib/utils';
import ToolManager from './tool-manager';
import {
  buildUrlExportPath,
  resolveMcpEndpointUrl,
  shouldUseExistingService,
} from '@/lib/mcp-endpoint-utils';
import { loadServiceOptions } from './service-options';
import { nextMcpVersion } from './version-utils';

const PROTOCOL_CARD_CONFIG: Record<string, { icon: typeof Terminal; label: string; color: string; bg: string; dot: string; ring: string }> = {
  stdio: {
    icon: Terminal,
    label: 'Stdio',
    color: 'text-purple-600 dark:text-purple-400',
    bg: 'bg-purple-50 dark:bg-purple-950/40',
    dot: 'bg-purple-500',
    ring: 'ring-purple-500/30',
  },
  'mcp-sse': {
    icon: Radio,
    label: 'SSE',
    color: 'text-blue-600 dark:text-blue-400',
    bg: 'bg-blue-50 dark:bg-blue-950/40',
    dot: 'bg-blue-500',
    ring: 'ring-blue-500/30',
  },
  'mcp-streamable': {
    icon: Globe,
    label: 'Streamable',
    color: 'text-cyan-600 dark:text-cyan-400',
    bg: 'bg-cyan-50 dark:bg-cyan-950/40',
    dot: 'bg-cyan-500',
    ring: 'ring-cyan-500/30',
  },
};

type FormMode = 'create' | 'draft-create' | 'draft-edit';

interface SecurityExtensions {
  'server.defaultDownstreamSecurity'?: {
    id?: string;
    passthrough?: boolean;
  };
  'server.defaultUpstreamSecurity'?: {
    id?: string;
    credential?: string;
  };
  [key: string]: unknown;
}

export default function NewMcpServerPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { currentNamespace } = useNamespaceStore();
  const { currentMcp: cachedMcp } = useMcpStore();

  const requestedMode = searchParams.get('mode');
  const mode: FormMode = requestedMode === 'edit' ? 'draft-edit'
    : requestedMode === 'version' ? 'draft-create'
      : (requestedMode as FormMode) || 'create';
  const editMcpName = searchParams.get('mcpName') || '';
  const namespaceId = searchParams.get('namespaceId') || currentNamespace || 'public';
  const sourceVersion = searchParams.get('version') || '';

  const isEdit = mode === 'draft-edit';
  const isVersion = mode === 'draft-create';

  // Check if store already has the data we need (e.g. navigating from detail page)
  const hasCachedData = (isEdit || isVersion) && cachedMcp?.name === editMcpName;

  // Form state
  const [serverName, setServerName] = useState('');
  const [frontProtocol, setFrontProtocol] = useState<McpProtocol>('stdio');
  const [description, setDescription] = useState('');
  const [version, setVersion] = useState('1.0.0');
  const [enabled, setEnabled] = useState(true);

  // Non-stdio config
  const [restToMcpSwitch, setRestToMcpSwitch] = useState(false);
  const [useExistService, setUseExistService] = useState(false);
  const [mcpEndpointUrl, setMcpEndpointUrl] = useState('');
  const [address, setAddress] = useState('');
  const [port, setPort] = useState('');
  const [transportProtocol, setTransportProtocol] = useState('http');
  const [exportPath, setExportPath] = useState('');
  const [selectedService, setSelectedService] = useState(''); // format: groupName@@serviceName
  const [serviceList, setServiceList] = useState<{ label: string; value: string }[]>([]);
  const [serviceSearch, setServiceSearch] = useState('');

  // Stdio config
  const [localServerConfig, setLocalServerConfig] = useState('');

  // Security schemes
  const [securitySchemes, setSecuritySchemes] = useState<McpSecurityScheme[]>([]);
  const [securityExtensions, setSecurityExtensions] = useState<SecurityExtensions>({});

  // Tool specification
  const [toolSpec, setToolSpec] = useState<McpToolSpecification>({});
  const [resourceSpec, setResourceSpec] = useState<Record<string, unknown> | undefined>();

  // UI state
  const [loading, setLoading] = useState(false);
  const [initLoading, setInitLoading] = useState((isEdit || isVersion) && !hasCachedData);

  const isStdio = frontProtocol === 'stdio';

  const populateForm = useCallback((
    data: McpServerDetailInfo,
    lifecycle?: McpServerVersionDetail,
  ) => {
    setServerName(data.name);
    setFrontProtocol(data.frontProtocol || 'stdio');
    setDescription(data.description || '');
    setVersion(data.versionDetail?.version || data.version || '1.0.0');
    setEnabled(data.enabled);
    setResourceSpec(lifecycle?.resourceSpecification || data.resourceSpec);

    const resolvedFrontProtocol = data.frontProtocol || 'stdio';
    if (resolvedFrontProtocol !== 'stdio') {
      const isRestToMcp = data.protocol === 'http' || data.protocol === 'https';
      setRestToMcpSwitch(isRestToMcp);
      setSelectedService('');
      setMcpEndpointUrl('');
      setAddress('');
      setPort('');
      setExportPath(data.remoteServerConfig?.exportPath || '');

      const useExistingService = isRestToMcp && shouldUseExistingService(data);
      setUseExistService(useExistingService);

      if (useExistingService) {
        const ref = data.remoteServerConfig!.serviceRef!;
        setSelectedService(`${ref.groupName || 'DEFAULT_GROUP'}@@${ref.serviceName}`);
        setTransportProtocol(ref.transportProtocol || 'http');
        setExportPath(data.remoteServerConfig!.exportPath || '');
      } else if (isRestToMcp && (data.backendEndpoints?.length ?? 0) > 0) {
        const endpoint = data.backendEndpoints![0];
        setAddress(endpoint.address || '');
        setPort(String(endpoint.port || ''));
        setTransportProtocol(
          endpoint.protocol || data.remoteServerConfig?.serviceRef?.transportProtocol || 'http',
        );
      } else {
        setMcpEndpointUrl(resolveMcpEndpointUrl(data));
      }
    } else if (data.localServerConfig) {
      setLocalServerConfig(JSON.stringify(data.localServerConfig, null, 2));
    }

    if (data.toolSpec?.securitySchemes) {
      setSecuritySchemes(data.toolSpec.securitySchemes);
    }
    if (data.toolSpec?.extensions) {
      const extensions: SecurityExtensions = {};
      for (const [key, value] of Object.entries(data.toolSpec.extensions)) {
        if (key === 'server.defaultDownstreamSecurity') {
          extensions[key] = value as SecurityExtensions['server.defaultDownstreamSecurity'];
        } else if (key === 'server.defaultUpstreamSecurity') {
          extensions[key] = value as SecurityExtensions['server.defaultUpstreamSecurity'];
        } else {
          extensions[key] = value;
        }
      }
      setSecurityExtensions(extensions);
    }
    if (data.toolSpec) {
      setToolSpec(data.toolSpec);
    }

    if (isVersion) {
      const oldVersion = data.versionDetail?.version || data.version || '1.0.0';
      setVersion(nextMcpVersion(oldVersion));
    }
  }, [isVersion]);

  // Load existing data for edit/version modes
  useEffect(() => {
    if ((isEdit || isVersion) && editMcpName) {
      // If store already has matching data (e.g. from detail page), use it directly
      const cachedVersion = cachedMcp?.versionDetail?.version || cachedMcp?.version;
      if (cachedMcp && cachedMcp.name === editMcpName
        && (!sourceVersion || cachedVersion === sourceVersion)) {
        populateForm(cachedMcp);
      }
      setInitLoading(true);
      const legacyRequest = mcpApi.getMcpServer({
        mcpName: editMcpName,
        namespaceId,
        version: sourceVersion || undefined,
      });
      const lifecycleRequest = sourceVersion
        ? mcpApi.getVersion({ namespaceId, mcpName: editMcpName, version: sourceVersion })
        : Promise.resolve(null);
      Promise.all([legacyRequest, lifecycleRequest])
        .then(([legacyResponse, lifecycleResponse]) => {
          const data = legacyResponse.data;
          const lifecycle = lifecycleResponse?.data;
          if (isEdit && lifecycle?.status !== 'draft') {
            throw new Error(t('mcp.onlyDraftEditable'));
          }
          populateForm(data, lifecycle);
        })
        .catch((error) => {
          toast.error(error instanceof Error ? error.message : t('mcp.loadFailed'));
        })
        .finally(() => {
          setInitLoading(false);
        });
    }
  }, [cachedMcp, editMcpName, namespaceId, isEdit, isVersion, populateForm, sourceVersion, t]);

  // Fetch service list for "use existing service" mode
  useEffect(() => {
    let cancelled = false;
    loadServiceOptions(namespaceId, serviceApi.listServices, serviceSearch)
      .then((options) => {
        if (!cancelled) {
          setServiceList(options);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setServiceList([]);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [namespaceId, serviceSearch]);

  // Validation
  const validate = (): boolean => {
    if (isEdit && !sourceVersion) {
      toast.error(t('mcp.versionRequired'));
      return false;
    }
    if (!serverName.trim()) {
      toast.error(t('mcp.serverNameRequired'));
      return false;
    }
    if (!version.trim()) {
      toast.error(t('mcp.versionRequired'));
      return false;
    }
    if (isStdio) {
      if (!localServerConfig.trim()) {
        toast.error(t('mcp.localConfigRequired'));
        return false;
      }
      try {
        JSON.parse(localServerConfig);
      } catch {
        toast.error(t('mcp.invalidJson'));
        return false;
      }
    } else if (!restToMcpSwitch) {
      if (!mcpEndpointUrl.trim()) {
        toast.error(t('mcp.mcpEndpointRequired'));
        return false;
      }
    } else if (!useExistService) {
      if (!address.trim()) {
        toast.error(t('mcp.addressRequired'));
        return false;
      }
      if (!port.trim()) {
        toast.error(t('mcp.portRequired'));
        return false;
      }
    }
    return true;
  };

  // Build submit data
  const buildSubmitData = () => {
    // Build serverSpecification
    const serverSpec: Record<string, unknown> = {
      name: serverName.trim(),
      frontProtocol,
      protocol: isStdio ? 'stdio' : restToMcpSwitch ? transportProtocol : frontProtocol,
      description: description.trim() || undefined,
      enabled,
      versionDetail: { version: version.trim() },
    };

    if (isStdio) {
      serverSpec.localServerConfig = JSON.parse(localServerConfig);
    } else if (restToMcpSwitch && useExistService) {
      const [refGroup, refServiceName] = selectedService.includes('@@')
        ? selectedService.split('@@')
        : ['DEFAULT_GROUP', selectedService];
      serverSpec.remoteServerConfig = {
        serviceRef: {
          serviceName: refServiceName,
          groupName: refGroup,
          namespaceId,
          transportProtocol,
        },
        exportPath: exportPath || undefined,
      };
    } else {
      // Non-stdio: always include remoteServerConfig for backend compatibility
      serverSpec.remoteServerConfig = {
        exportPath: exportPath || undefined,
      };
    }

    // Build endpointSpecification
    let endpointSpec: string | undefined;
    if (!isStdio) {
      if (restToMcpSwitch && !useExistService) {
        endpointSpec = JSON.stringify({
          type: 'DIRECT',
          data: {
            transportProtocol,
            address: address.trim(),
            port: port.trim(),
          },
        });
      } else if (restToMcpSwitch && useExistService) {
        const [refGroup, refServiceName] = selectedService.includes('@@')
          ? selectedService.split('@@')
          : ['DEFAULT_GROUP', selectedService];
        endpointSpec = JSON.stringify({
          type: 'REF',
          data: {
            serviceName: refServiceName,
            groupName: refGroup,
            namespaceId,
            transportProtocol,
          },
        });
      } else {
        // Non-restToMcp: parse the MCP endpoint URL
        try {
          const url = new URL(mcpEndpointUrl.trim());
          const urlTransportProtocol = url.protocol.replace(':', '');
          const urlPath = buildUrlExportPath(url);

          // Update serverSpec with remoteServerConfig including exportPath
          serverSpec.remoteServerConfig = {
            exportPath: urlPath,
          };

          endpointSpec = JSON.stringify({
            type: 'DIRECT',
            data: {
              address: url.hostname,
              port: url.port || (url.protocol === 'https:' ? '443' : '80'),
              transportProtocol: urlTransportProtocol,
            },
          });
        } catch {
          // fallback: just pass it as-is
          endpointSpec = JSON.stringify({
            type: 'DIRECT',
            data: { address: mcpEndpointUrl.trim() },
          });
        }
      }
    }

    // Build toolSpecification
    let toolSpecStr: string | undefined;
    const mergedToolSpec: Record<string, unknown> = { ...toolSpec };
    if (securitySchemes.length > 0) mergedToolSpec.securitySchemes = securitySchemes;
    if (Object.keys(securityExtensions).length > 0) mergedToolSpec.extensions = securityExtensions;
    if (
      Object.keys(mergedToolSpec).length > 0 &&
      (mergedToolSpec.tools || mergedToolSpec.securitySchemes || mergedToolSpec.extensions)
    ) {
      toolSpecStr = JSON.stringify(mergedToolSpec);
    }

    return {
      mcpName: serverName.trim(),
      namespaceId,
      version: version.trim(),
      serverSpecification: JSON.stringify(serverSpec),
      toolSpecification: toolSpecStr,
      resourceSpecification: resourceSpec ? JSON.stringify(resourceSpec) : undefined,
      endpointSpecification: endpointSpec,
    };
  };

  const handleSubmit = async () => {
    if (!validate()) return;
    await doSubmit();
  };

  const doSubmit = async () => {
    setLoading(true);
    try {
      const data = buildSubmitData();
      if (isEdit) {
        await mcpApi.updateDraft(data);
        toast.success(t('mcp.draftUpdateSuccess'));
      } else {
        await mcpApi.createDraft(data);
        toast.success(t('mcp.draftCreateSuccess'));
      }
      const params = new URLSearchParams({
        mcpName: serverName.trim(),
        namespaceId,
        version: version.trim(),
      });
      navigate(`/mcpServerDetail?${params.toString()}`);
    } catch {
      // handled by interceptor
    } finally {
      setLoading(false);
    }
  };

  // Security scheme helpers
  const addSecurityScheme = () => {
    setSecuritySchemes((prev) => [
      ...prev,
      { id: '', type: 'http', scheme: 'bearer' },
    ]);
  };

  const removeSecurityScheme = (index: number) => {
    const removedId = securitySchemes[index]?.id;
    setSecuritySchemes((prev) => prev.filter((_, i) => i !== index));
    // Clear downstream/upstream references to the removed scheme
    if (removedId) {
      setSecurityExtensions((prev) => {
        const next = { ...prev };
        if (next['server.defaultDownstreamSecurity']?.id === removedId) {
          next['server.defaultDownstreamSecurity'] = { id: undefined, passthrough: false };
        }
        if (next['server.defaultUpstreamSecurity']?.id === removedId) {
          next['server.defaultUpstreamSecurity'] = { id: undefined, credential: '' };
        }
        return next;
      });
    }
  };

  const updateSecurityScheme = (index: number, field: keyof McpSecurityScheme, value: string) => {
    const oldId = securitySchemes[index]?.id;
    setSecuritySchemes((prev) =>
      prev.map((s, i) => {
        if (i !== index) return s;
        const updated = { ...s, [field]: value };
        // Reset conditional fields when type changes
        if (field === 'type') {
          if (value === 'http') {
            updated.scheme = 'bearer';
            delete updated.in;
            delete updated.name;
          } else {
            delete updated.scheme;
            updated.in = 'header';
            updated.name = '';
          }
        }
        return updated;
      })
    );
    // When scheme ID changes, update downstream/upstream references
    if (field === 'id' && oldId && oldId !== value) {
      setSecurityExtensions((prev) => {
        const next = { ...prev };
        if (next['server.defaultDownstreamSecurity']?.id === oldId) {
          next['server.defaultDownstreamSecurity'] = {
            ...next['server.defaultDownstreamSecurity'],
            id: value || undefined,
          };
        }
        if (next['server.defaultUpstreamSecurity']?.id === oldId) {
          next['server.defaultUpstreamSecurity'] = {
            ...next['server.defaultUpstreamSecurity'],
            id: value || undefined,
          };
        }
        return next;
      });
    }
  };

  // Parse MCP endpoint URL
  const handleEndpointUrlParse = (url: string) => {
    setMcpEndpointUrl(url);
  };

  if (initLoading) {
    return (
      <div className="space-y-6">
        <div className="flex items-center gap-4">
          <Skeleton className="h-9 w-9 rounded-md" />
          <Skeleton className="h-8 w-48" />
        </div>
        <Skeleton className="h-[400px] w-full" />
      </div>
    );
  }

  const pageTitle = isEdit
    ? t('mcp.editServer')
    : isVersion
      ? t('mcp.newVersion')
      : t('mcp.createServer');

  const protoConfig = PROTOCOL_CARD_CONFIG[frontProtocol];

  return (
    <div className="flex flex-col min-h-[calc(100vh-120px)]">
      <div className="space-y-5 grow">
      {/* ===== Hero Header ===== */}
      <div className="relative rounded-xl border bg-card overflow-hidden">
        <div className="absolute inset-0 bg-gradient-to-br from-primary/[0.04] via-transparent to-blue-500/[0.03]" />
        <div className="absolute top-0 right-0 w-64 h-64 bg-gradient-to-bl from-primary/[0.06] to-transparent rounded-full -translate-y-1/2 translate-x-1/3" />

        <div className="relative px-5 py-4">
          {/* Top bar: back */}
          <div className="flex items-center justify-between mb-4">
            <Button
              variant="ghost"
              size="sm"
              className="h-7 gap-1.5 text-muted-foreground hover:text-foreground -ml-2"
              onClick={() => navigate(-1)}
            >
              <ArrowLeft className="h-3.5 w-3.5" />
              {t('mcp.backToList')}
            </Button>
          </div>

          {/* Server identity */}
          <div className="flex items-start gap-4">
            <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-blue-500 to-cyan-400 shadow-lg shadow-blue-500/20">
              <Cpu className="h-7 w-7 text-white" />
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2.5 mb-1">
                <h1 className="text-xl font-bold tracking-tight">{pageTitle}</h1>
                {protoConfig && (
                  <span className={cn(
                    'inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium',
                    protoConfig.bg, protoConfig.color
                  )}>
                    <span className={cn('h-1.5 w-1.5 rounded-full', protoConfig.dot)} />
                    {protoConfig.label}
                  </span>
                )}
                {version && (
                  <span className="text-xs text-muted-foreground font-mono bg-muted/60 px-1.5 py-0.5 rounded">
                    v{version}
                  </span>
                )}
              </div>
              <p className="text-sm text-muted-foreground leading-relaxed max-w-2xl">
                {isEdit
                  ? t('mcp.editServerDesc', { defaultValue: '修改 MCP Server 的配置信息' })
                  : isVersion
                    ? t('mcp.newVersionDesc', { defaultValue: '基于当前版本创建新版本' })
                    : t('mcp.createServerDesc', { defaultValue: '配置一个新的 MCP Server 草稿' })}
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* Basic Info */}
      <Card className="overflow-hidden py-0 gap-0">
        <div className="px-5 py-3.5 border-b bg-muted/30">
          <h2 className="text-sm font-semibold flex items-center gap-2">
            <Server className="h-4 w-4 text-muted-foreground" />
            {t('mcp.basicInfo')}
          </h2>
        </div>
        <CardContent className="p-5 space-y-5">
          {/* Row 1: Server Name (2/3) + Version (1/3) */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
            <div className="md:col-span-2 space-y-2.5">
              <Label htmlFor="serverName">
                {t('mcp.serverName')} <span className="text-destructive ml-1">*</span>
              </Label>
              <Input
                id="serverName"
                value={serverName}
                onChange={(e) => setServerName(e.target.value)}
                placeholder={t('mcp.serverName')}
                disabled={isEdit || isVersion}
                maxLength={255}
              />
            </div>
            <div className="space-y-2.5">
              <Label htmlFor="version">
                {t('mcp.version')} <span className="text-destructive ml-1">*</span>
              </Label>
              <Input
                id="version"
                value={version}
                onChange={(e) => setVersion(e.target.value)}
                placeholder={t('mcp.versionPlaceholder')}
                disabled={isEdit}
              />
            </div>
          </div>

          {/* Row 2: Protocol selector (card-style) + Enable */}
          <div className="grid grid-cols-1 md:grid-cols-[1fr_auto] gap-5 items-end">
            <div className="space-y-2.5">
              <Label>
                {t('mcp.protocol')} <span className="text-destructive ml-1">*</span>
              </Label>
              <RadioGroup
                value={frontProtocol}
                onValueChange={(v) => setFrontProtocol(v as McpProtocol)}
                className="grid grid-cols-3 gap-3"
                disabled={isEdit || isVersion}
              >
                {(['stdio', 'mcp-sse', 'mcp-streamable'] as const).map((proto) => {
                  const cfg = PROTOCOL_CARD_CONFIG[proto];
                  const Icon = cfg.icon;
                  const isSelected = frontProtocol === proto;
                  return (
                    <Label
                      key={proto}
                      htmlFor={`proto-${proto}`}
                      className={cn(
                        'flex items-center gap-3 rounded-lg border px-4 py-3 cursor-pointer transition-all',
                        isSelected
                          ? cn('bg-primary/[0.06] shadow-sm ring-1', cfg.ring, 'border-transparent')
                          : 'hover:bg-muted/50',
                        (isEdit || isVersion) && 'opacity-60 cursor-not-allowed'
                      )}
                    >
                      <RadioGroupItem value={proto} id={`proto-${proto}`} className="sr-only" />
                      <div className={cn(
                        'flex h-9 w-9 shrink-0 items-center justify-center rounded-lg',
                        isSelected ? cfg.bg : 'bg-muted/50'
                      )}>
                        <Icon className={cn('h-4 w-4', isSelected ? cfg.color : 'text-muted-foreground')} />
                      </div>
                      <div>
                        <div className={cn('text-sm font-medium', isSelected && 'text-foreground')}>
                          {cfg.label}
                        </div>
                        <div className="text-xs text-muted-foreground">
                          {t(`mcp.protocol${proto === 'stdio' ? 'Stdio' : proto === 'mcp-sse' ? 'Sse' : 'Streamable'}`)}
                        </div>
                      </div>
                    </Label>
                  );
                })}
              </RadioGroup>
            </div>
            <div className="flex items-center gap-3 h-9">
              <Switch checked={enabled} onCheckedChange={setEnabled}
                disabled={isEdit || isVersion} />
              <Label>{enabled ? t('mcp.enabled') : t('mcp.disabled')}</Label>
            </div>
          </div>

          {/* Row 3: Description full width */}
          <div className="space-y-2.5">
            <Label htmlFor="description">{t('mcp.description')}</Label>
            <Textarea
              id="description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder={t('mcp.descriptionPlaceholder')}
              rows={3}
            />
          </div>
        </CardContent>
      </Card>

      {/* Protocol-specific config */}
      {isStdio ? (
        /* Stdio: Local Server Config */
        <Card className="overflow-hidden py-0 gap-0">
          <div className="px-5 py-3.5 border-b bg-muted/30">
            <h2 className="text-sm font-semibold flex items-center gap-2">
              <Terminal className="h-4 w-4 text-purple-500" />
              {t('mcp.localServerConfig')}
            </h2>
          </div>
          <CardContent className="p-5 space-y-4">
            <div className="flex items-center gap-2 text-xs text-muted-foreground">
              <Info className="h-3.5 w-3.5" />
              {t('mcp.localServerConfigTip')}
            </div>
            <Textarea
              value={localServerConfig}
              onChange={(e) => setLocalServerConfig(e.target.value)}
              placeholder='{\n  "command": "npx",\n  "args": ["-y", "@modelcontextprotocol/server-demo"]\n}'
              rows={12}
              className="font-mono text-sm"
            />
            <Button
              variant="outline"
              size="sm"
              onClick={() => {
                try {
                  setLocalServerConfig(JSON.stringify(JSON.parse(localServerConfig), null, 2));
                } catch {
                  toast.error(t('mcp.invalidJson'));
                }
              }}
            >
              {t('mcp.formatJson')}
            </Button>
          </CardContent>
        </Card>
      ) : (
        /* Non-stdio: Server connection config */
        <Card className="overflow-hidden py-0 gap-0">
          <div className="px-5 py-3.5 border-b bg-muted/30">
            <h2 className="text-sm font-semibold flex items-center gap-2">
              <Globe className="h-4 w-4 text-blue-500" />
              {t('mcp.remoteServerConfig')}
            </h2>
          </div>
          <CardContent className="p-5 space-y-5">
            {/* REST to MCP switch */}
            <div className="flex items-center justify-between p-3 rounded-lg bg-muted/30">
              <div className="flex items-center gap-2">
                <Label>{t('mcp.restToMcpSwitch')}</Label>
                <Tooltip>
                  <TooltipTrigger>
                    <Info className="h-3.5 w-3.5 text-muted-foreground" />
                  </TooltipTrigger>
                  <TooltipContent>{t('mcp.restToMcpSwitchTip')}</TooltipContent>
                </Tooltip>
              </div>
              <Switch checked={restToMcpSwitch} onCheckedChange={setRestToMcpSwitch} disabled={isEdit || isVersion} />
            </div>

            <Separator />

            {restToMcpSwitch ? (
              <>
                {/* Use existing service or direct connect */}
                <RadioGroup
                  value={useExistService ? 'existing' : 'direct'}
                  onValueChange={(v) => setUseExistService(v === 'existing')}
                  className="flex gap-6"
                >
                  <div className="flex items-center gap-2">
                    <RadioGroupItem value="existing" id="use-existing" />
                    <Label htmlFor="use-existing" className="cursor-pointer font-normal">
                      {t('mcp.useExistServiceOption')}
                    </Label>
                  </div>
                  <div className="flex items-center gap-2">
                    <RadioGroupItem value="direct" id="use-direct" />
                    <Label htmlFor="use-direct" className="cursor-pointer font-normal">
                      {t('mcp.directConnectOption')}
                    </Label>
                  </div>
                </RadioGroup>

                {useExistService ? (
                  /* Existing service mode */
                  <div className="space-y-5">
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
                      <div className="space-y-2.5">
                        <Label>{t('mcp.selectService')}</Label>
                        <Input
                          value={serviceSearch}
                          onChange={(e) => setServiceSearch(e.target.value)}
                          placeholder={t('service.serviceName')}
                        />
                        {serviceList.length > 0 ? (
                          <Select value={selectedService} onValueChange={setSelectedService}>
                            <SelectTrigger>
                              <SelectValue placeholder={t('mcp.selectService')} />
                            </SelectTrigger>
                            <SelectContent>
                              {serviceList.map((svc) => (
                                <SelectItem key={svc.value} value={svc.value}>
                                  {svc.label}
                                </SelectItem>
                              ))}
                            </SelectContent>
                          </Select>
                        ) : (
                          <Input
                            value={selectedService}
                            onChange={(e) => setSelectedService(e.target.value)}
                            placeholder="groupName@@serviceName"
                          />
                        )}
                      </div>
                      <div className="space-y-2.5">
                        <Label>{t('mcp.transportProtocol')}</Label>
                        <Select value={transportProtocol} onValueChange={setTransportProtocol}>
                          <SelectTrigger>
                            <SelectValue />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value="http">HTTP</SelectItem>
                            <SelectItem value="https">HTTPS</SelectItem>
                          </SelectContent>
                        </Select>
                      </div>
                      <div className="space-y-2.5">
                        <Label>{t('mcp.exportPath')}</Label>
                        <Input
                          value={exportPath}
                          onChange={(e) => setExportPath(e.target.value)}
                          placeholder="/mcp"
                        />
                      </div>
                    </div>
                  </div>
                ) : (
                  /* Direct connect mode */
                  <div className="space-y-5">
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
                      <div className="space-y-2.5">
                        <Label>
                          {t('mcp.address')} <span className="text-destructive ml-1">*</span>
                        </Label>
                        <Input
                          value={address}
                          onChange={(e) => setAddress(e.target.value)}
                          placeholder={t('mcp.addressPlaceholder')}
                        />
                      </div>
                      <div className="space-y-2.5">
                        <Label>
                          {t('mcp.port')} <span className="text-destructive ml-1">*</span>
                        </Label>
                        <Input
                          value={port}
                          onChange={(e) => setPort(e.target.value.replace(/\D/g, ''))}
                          placeholder={t('mcp.portPlaceholder')}
                          type="number"
                          min={1}
                          max={65535}
                        />
                      </div>
                      <div className="space-y-2.5">
                        <Label>{t('mcp.transportProtocol')}</Label>
                        <Select value={transportProtocol} onValueChange={setTransportProtocol}>
                          <SelectTrigger>
                            <SelectValue />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value="http">HTTP</SelectItem>
                            <SelectItem value="https">HTTPS</SelectItem>
                          </SelectContent>
                        </Select>
                      </div>
                    </div>
                  </div>
                )}
              </>
            ) : (
              /* MCP endpoint URL direct input */
              <div className="space-y-2.5">
                <Label>
                  {t('mcp.mcpEndpointUrl')} <span className="text-destructive ml-1">*</span>
                </Label>
                <Input
                  value={mcpEndpointUrl}
                  onChange={(e) => handleEndpointUrlParse(e.target.value)}
                  placeholder={t('mcp.mcpEndpointUrlPlaceholder')}
                />
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {/* Security Schemes - non-stdio with restToMcp */}
      {!isStdio && restToMcpSwitch && (
        <Card className="overflow-hidden py-0 gap-0">
          <div className="px-5 py-3.5 border-b bg-muted/30">
            <h2 className="text-sm font-semibold flex items-center gap-2">
              <Shield className="h-4 w-4 text-rose-500" />
              {t('mcp.securitySchemes')}
              {securitySchemes.length > 0 && (
                <Badge variant="secondary" className="h-5 min-w-5 rounded-full text-[11px] font-semibold px-1.5 bg-rose-100 text-rose-700 dark:bg-rose-950/50 dark:text-rose-300">
                  {securitySchemes.length}
                </Badge>
              )}
            </h2>
          </div>
          <CardContent className="p-5 space-y-5">
            {/* Default downstream security */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 p-5 rounded-xl bg-muted/40 border border-border/50">
              <div className="flex flex-col gap-2">
                <Label>{t('mcp.defaultDownstreamSecurity')}</Label>
                <p className="text-xs text-muted-foreground -mt-1">{t('mcp.defaultDownstreamSecurityDesc')}</p>
                <Select
                  value={securityExtensions['server.defaultDownstreamSecurity']?.id || ''}
                  onValueChange={(v) =>
                    setSecurityExtensions((prev) => ({
                      ...prev,
                      'server.defaultDownstreamSecurity': {
                        ...prev['server.defaultDownstreamSecurity'],
                        id: v,
                      },
                    }))
                  }
                >
                  <SelectTrigger>
                    <SelectValue placeholder="-" />
                  </SelectTrigger>
                  <SelectContent>
                    {securitySchemes.filter((s) => s.id).map((s) => (
                      <SelectItem key={s.id} value={s.id}>
                        {s.id}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <div className="flex items-center gap-2">
                  <Switch
                    checked={securityExtensions['server.defaultDownstreamSecurity']?.passthrough || false}
                    disabled={!securityExtensions['server.defaultDownstreamSecurity']?.id}
                    onCheckedChange={(v) =>
                      setSecurityExtensions((prev) => ({
                        ...prev,
                        'server.defaultDownstreamSecurity': {
                          ...prev['server.defaultDownstreamSecurity'],
                          passthrough: v,
                        },
                      }))
                    }
                  />
                  <span className="text-sm text-muted-foreground">{t('mcp.passthroughAuth')}</span>
                </div>
              </div>

              <div className="flex flex-col gap-2">
                <Label>{t('mcp.defaultUpstreamSecurity')}</Label>
                <p className="text-xs text-muted-foreground -mt-1">{t('mcp.defaultUpstreamSecurityDesc')}</p>
                <Select
                  value={securityExtensions['server.defaultUpstreamSecurity']?.id || ''}
                  onValueChange={(v) =>
                    setSecurityExtensions((prev) => ({
                      ...prev,
                      'server.defaultUpstreamSecurity': {
                        ...prev['server.defaultUpstreamSecurity'],
                        id: v,
                      },
                    }))
                  }
                >
                  <SelectTrigger>
                    <SelectValue placeholder="-" />
                  </SelectTrigger>
                  <SelectContent>
                    {securitySchemes.filter((s) => s.id).map((s) => (
                      <SelectItem key={s.id} value={s.id}>
                        {s.id}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <div className="space-y-1">
                  <Label className="text-xs text-muted-foreground">{t('mcp.credentialOverride')}</Label>
                  <Input
                    value={securityExtensions['server.defaultUpstreamSecurity']?.credential || ''}
                    disabled={!securityExtensions['server.defaultUpstreamSecurity']?.id}
                    onChange={(e) =>
                      setSecurityExtensions((prev) => ({
                        ...prev,
                        'server.defaultUpstreamSecurity': {
                          ...prev['server.defaultUpstreamSecurity'],
                          credential: e.target.value,
                        },
                      }))
                    }
                    placeholder={t('mcp.credentialOverride')}
                  />
                </div>
              </div>
            </div>

            <Separator />

            {/* Scheme rows */}
            {securitySchemes.length > 0 && (
              <div className="flex flex-col gap-2">
                {securitySchemes.map((scheme, index) => (
                  <div
                    key={index}
                    className="grid grid-cols-1 sm:grid-cols-[1fr_1fr_1fr_1fr_auto] gap-4 items-end p-4 rounded-lg border border-border/40"
                  >
                    <div className="space-y-1">
                      <Label>{t('mcp.securitySchemeId')}</Label>
                      <Input
                        value={scheme.id}
                        onChange={(e) => updateSecurityScheme(index, 'id', e.target.value)}
                        placeholder="ID"
                      />
                    </div>
                    <div className="space-y-1">
                      <Label>{t('mcp.securitySchemeType')}</Label>
                      <Select
                        value={scheme.type}
                        onValueChange={(v) => updateSecurityScheme(index, 'type', v)}
                      >
                        <SelectTrigger>
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="http">HTTP</SelectItem>
                          <SelectItem value="apiKey">API Key</SelectItem>
                        </SelectContent>
                      </Select>
                    </div>
                    <div className="space-y-1">
                      {scheme.type === 'http' ? (
                        <>
                          <Label>{t('mcp.securitySchemeField')}</Label>
                          <Select
                            value={scheme.scheme || 'bearer'}
                            onValueChange={(v) => updateSecurityScheme(index, 'scheme', v)}
                          >
                            <SelectTrigger>
                              <SelectValue />
                            </SelectTrigger>
                            <SelectContent>
                              <SelectItem value="basic">{t('mcp.schemeBasic')}</SelectItem>
                              <SelectItem value="bearer">{t('mcp.schemeBearer')}</SelectItem>
                            </SelectContent>
                          </Select>
                        </>
                      ) : (
                        <>
                          <Label>{t('mcp.securityIn')}</Label>
                          <Select
                            value={scheme.in || 'header'}
                            onValueChange={(v) => updateSecurityScheme(index, 'in', v)}
                          >
                            <SelectTrigger>
                              <SelectValue />
                            </SelectTrigger>
                            <SelectContent>
                              <SelectItem value="header">{t('mcp.inHeader')}</SelectItem>
                              <SelectItem value="query">{t('mcp.inQuery')}</SelectItem>
                            </SelectContent>
                          </Select>
                        </>
                      )}
                    </div>
                    <div className="space-y-1">
                      {scheme.type === 'apiKey' ? (
                        <>
                          <Label>Key Name</Label>
                          <Input
                            value={scheme.name || ''}
                            onChange={(e) => updateSecurityScheme(index, 'name', e.target.value)}
                            placeholder="X-API-Key"
                          />
                        </>
                      ) : (
                        <>
                          <Label>{t('mcp.securityDefaultCredential')}</Label>
                          <Input
                            value={scheme.defaultCredential || ''}
                            onChange={(e) => updateSecurityScheme(index, 'defaultCredential', e.target.value)}
                            placeholder="token..."
                          />
                        </>
                      )}
                    </div>
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-9 w-9 text-destructive hover:text-destructive hover:bg-destructive/10 shrink-0"
                      onClick={() => removeSecurityScheme(index)}
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </div>
                ))}
              </div>
            )}

            <Button variant="outline" size="sm" onClick={addSecurityScheme}>
              <Plus className="mr-1.5 h-3.5 w-3.5" />
              {t('mcp.addSecurityScheme')}
            </Button>
          </CardContent>
        </Card>
      )}

      {/* Tool Management */}
      <ToolManager
        toolSpec={toolSpec}
        importMode={isStdio ? 'none' : restToMcpSwitch ? 'openapi' : 'mcp'}
        onChange={(newSpec) => {
          setToolSpec(newSpec);
          // Sync security schemes from tool imports (e.g. OpenAPI)
          if (newSpec.securitySchemes && newSpec.securitySchemes.length > 0) {
            setSecuritySchemes((prev) => {
              const existingIds = new Set(prev.map((s) => s.id));
              const newSchemes = newSpec.securitySchemes!.filter((s) => !existingIds.has(s.id));
              return newSchemes.length > 0 ? [...prev, ...newSchemes] : prev;
            });
          }
        }}
      />

      </div>

      {/* Submit — full-width sticky bottom bar */}
      <div className="sticky bottom-0 z-10 -mx-6 -mb-6 mt-2">
        <div className="border-t bg-background/95 backdrop-blur-sm py-3 px-6">
          <div className="flex justify-end gap-3">
            <Button variant="outline" onClick={() => navigate(-1)} disabled={loading}>
              {t('common.cancel')}
            </Button>
            <Button onClick={handleSubmit} disabled={loading}>
              {loading ? t('mcp.saving') : t('mcp.saveDraft')}
            </Button>
          </div>
        </div>
      </div>

    </div>
  );
}
