import { useEffect, useState, useRef, useMemo } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import {
  ArrowLeft,
  ChevronDown,
  ChevronUp,
  Bot,
  Plus,
  Star,
  Trash2,
  Zap,
  Radio,
  History,
  Globe,
  Server,
  Shield,
  ShieldCheck,
  Link2,
} from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { Separator } from '@/components/ui/separator';
import { Skeleton } from '@/components/ui/skeleton';
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog';
import { useNamespaceStore } from '@/stores/namespace-store';
import { useAgentStore } from '@/stores/agent-store';
import { agentApi } from '@/api/agent';
import type { AgentDetailInfo } from '@/types/agent';
import { cn } from '@/lib/utils';

interface AgentInterfaceForm {
  id: string;
  url: string;
  protocolVersion: string;
  protocolBinding: string;
  tenant: string;
}

const DEFAULT_INTERFACE_TRANSPORT = 'JSONRPC';

export default function NewAgentPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { currentNamespace } = useNamespaceStore();
  const { currentAgent: cachedAgent, versionList, fetchVersionList } = useAgentStore();

  const mode = searchParams.get('mode');
  const editName = searchParams.get('name') || '';
  const namespaceId = searchParams.get('namespaceId') || currentNamespace || 'public';

  const isEdit = mode === 'edit' && !!editName;
  const hasCachedData = isEdit && cachedAgent?.name === editName;

  const [initLoading, setInitLoading] = useState(isEdit && !hasCachedData);
  const [saving, setSaving] = useState(false);
  const [showAdvanced, setShowAdvanced] = useState(false);
  const originalAgent = useRef<AgentDetailInfo | null>(null);

  // Form state
  const [name, setName] = useState('');
  const [version, setVersion] = useState('');
  const [interfaceList, setInterfaceList] = useState<AgentInterfaceForm[]>([]);
  const [preferredInterfaceId, setPreferredInterfaceId] = useState<string>('');
  const [description, setDescription] = useState('');
  const [defaultInputModes, setDefaultInputModes] = useState('');
  const [defaultOutputModes, setDefaultOutputModes] = useState('');

  // Capabilities
  const [streaming, setStreaming] = useState(false);
  const [pushNotifications, setPushNotifications] = useState(false);
  const [stateTransitionHistory, setStateTransitionHistory] = useState(false);

  // Skills (JSON)
  const [skillsJson, setSkillsJson] = useState('');

  // Advanced
  const [iconUrl, setIconUrl] = useState('');
  const [documentationUrl, setDocumentationUrl] = useState('');
  const [organization, setOrganization] = useState('');
  const [providerUrl, setProviderUrl] = useState('');
  const [securityJson, setSecurityJson] = useState('');
  const [securitySchemesJson, setSecuritySchemesJson] = useState('');
  const [supportsAuthenticatedExtendedCard, setSupportsAuthenticatedExtendedCard] = useState(false);

  // Publish strategy (edit mode)
  type PublishStrategy = 'new-version' | 'set-latest' | 'edit-current';
  const [publishStrategy, setPublishStrategy] = useState<PublishStrategy>('edit-current');
  const [strategyDialogOpen, setStrategyDialogOpen] = useState(false);

  const createInterface = (partial?: Partial<AgentInterfaceForm>): AgentInterfaceForm => ({
    id: `${Date.now()}-${Math.random().toString(16).slice(2, 8)}`,
    url: partial?.url || '',
    protocolVersion: partial?.protocolVersion || '',
    protocolBinding: partial?.protocolBinding || DEFAULT_INTERFACE_TRANSPORT,
    tenant: partial?.tenant || '',
  });

  const updateInterface = (id: string, patch: Partial<AgentInterfaceForm>) => {
    setInterfaceList((prev) => prev.map((item) => (item.id === id ? { ...item, ...patch } : item)));
  };

  const addInterface = () => {
    const next = createInterface();
    setInterfaceList((prev) => [...prev, next]);
    if (!preferredInterfaceId) {
      setPreferredInterfaceId(next.id);
    }
  };

  const removeInterface = (id: string) => {
    setInterfaceList((prev) => {
      const next = prev.filter((item) => item.id !== id);
      if (preferredInterfaceId === id) {
        setPreferredInterfaceId(next[0]?.id || '');
      }
      return next;
    });
  };

  const normalizeInterface = (item: Record<string, unknown>): Record<string, unknown> => {
    const protocolBinding = String(item.protocolBinding || item.transport || '');
    return {
      ...item,
      protocolBinding,
      transport: String(item.transport || protocolBinding),
      protocolVersion: String(item.protocolVersion || ''),
      url: String(item.url || ''),
      tenant: String(item.tenant || ''),
    };
  };

  const populateForm = (data: AgentDetailInfo) => {
    const supportedInterfaces = Array.isArray(data.supportedInterfaces) ? data.supportedInterfaces : [];
    const fallbackInterfaces = Array.isArray(data.additionalInterfaces) ? data.additionalInterfaces : [];
    const mergedInterfaces = supportedInterfaces.length > 0
      ? supportedInterfaces
      : (data.url ? [{
        url: data.url,
        protocolVersion: data.protocolVersion,
        protocolBinding: data.preferredTransport,
      }, ...fallbackInterfaces] : fallbackInterfaces);
    const normalizedInterfaces = mergedInterfaces
      .map((item) => normalizeInterface(item as Record<string, unknown>))
      .filter((item) => item.url || item.protocolVersion || item.protocolBinding || item.tenant)
      .map((item) => createInterface({
        url: item.url as string,
        protocolVersion: item.protocolVersion as string,
        protocolBinding: item.protocolBinding as string,
        tenant: item.tenant as string,
      }));
    setName(data.name || '');
    setVersion(data.version || '');
    setInterfaceList(normalizedInterfaces);
    setPreferredInterfaceId(normalizedInterfaces[0]?.id || '');
    setDescription(data.description || '');
    setDefaultInputModes(data.defaultInputModes?.join(', ') || '');
    setDefaultOutputModes(data.defaultOutputModes?.join(', ') || '');

    setStreaming(!!data.capabilities?.streaming);
    setPushNotifications(!!data.capabilities?.pushNotifications);
    setStateTransitionHistory(!!data.capabilities?.stateTransitionHistory);

    setSkillsJson(data.skills ? JSON.stringify(data.skills, null, 2) : '');

    setIconUrl(data.iconUrl || '');
    setDocumentationUrl(data.documentationUrl || '');
    setOrganization(data.provider?.organization || '');
    setProviderUrl(data.provider?.url || '');
    setSecurityJson(data.security ? JSON.stringify(data.security, null, 2) : '');
    setSecuritySchemesJson(data.securitySchemes ? JSON.stringify(data.securitySchemes, null, 2) : '');
    setSupportsAuthenticatedExtendedCard(!!(data.capabilities?.extendedAgentCard ?? data.supportsAuthenticatedExtendedCard));
  };

  useEffect(() => {
    if (isEdit && editName) {
      if (cachedAgent && cachedAgent.name === editName) {
        originalAgent.current = cachedAgent as AgentDetailInfo;
        populateForm(cachedAgent as AgentDetailInfo);
        setInitLoading(false);
        return;
      }
      setInitLoading(true);
      agentApi.getAgent({ agentName: editName, namespaceId })
        .then((response) => {
          const data = (response as unknown as { data: AgentDetailInfo }).data;
          originalAgent.current = data;
          populateForm(data);
        })
        .catch(() => {
          toast.error(t('agent.loadFailed'));
        })
        .finally(() => {
          setInitLoading(false);
        });
    }
  }, [editName, namespaceId, isEdit]);

  // Fetch version list for publish strategy (edit mode)
  useEffect(() => {
    if (isEdit && editName) {
      fetchVersionList(namespaceId, editName);
    }
  }, [isEdit, editName, namespaceId, fetchVersionList]);

  // Compute strategy availability based on version
  const strategyAvailability = useMemo(() => {
    const trimmed = version.trim();
    const existing = versionList.find((v) => v.version === trimmed);
    const isNew = !existing;
    const isLatest = existing?.latest === true;

    return {
      'new-version': {
        available: isNew,
        reason: isNew ? undefined : t('agent.strategyReasonVersionExists'),
      },
      'set-latest': {
        available: !isNew && !isLatest,
        reason: isNew
          ? t('agent.strategyReasonVersionNotExists')
          : isLatest
            ? t('agent.strategyReasonAlreadyLatest')
            : undefined,
      },
      'edit-current': {
        available: !isNew,
        reason: isNew ? t('agent.strategyReasonVersionNotExists') : undefined,
      },
    };
  }, [version, versionList, t]);

  // Auto-switch strategy when current becomes unavailable
  useEffect(() => {
    if (!isEdit) return;
    if (!strategyAvailability[publishStrategy].available) {
      const order: PublishStrategy[] = ['edit-current', 'set-latest', 'new-version'];
      const first = order.find((s) => strategyAvailability[s].available);
      if (first) setPublishStrategy(first);
    }
  }, [strategyAvailability, publishStrategy, isEdit]);

  const validateJson = (value: string, fieldName: string): unknown | null => {
    if (!value || !value.trim()) return undefined;
    try {
      return JSON.parse(value.trim());
    } catch (e) {
      toast.error(`${fieldName} JSON ${t('agent.jsonFormatError')}: ${(e as Error).message}`);
      return null;
    }
  };

  const validate = (): Record<string, unknown> | null => {
    if (!name.trim()) { toast.error(t('agent.nameRequired')); return null; }
    if (!version.trim()) { toast.error(t('agent.versionRequired')); return null; }
    if (interfaceList.length === 0) {
      toast.error(t('agent.interfaceRequired', { defaultValue: 'At least one interface is required.' }));
      return null;
    }

    const skills = validateJson(skillsJson, t('agent.skills'));
    if (skills === null) return null;
    const security = validateJson(securityJson, t('agent.security'));
    if (security === null) return null;
    const securitySchemes = validateJson(securitySchemesJson, t('agent.securitySchemes'));
    if (securitySchemes === null) return null;
    const preferredExists = interfaceList.some((item) => item.id === preferredInterfaceId);
    const orderedInterfaces = preferredExists
      ? [
        ...interfaceList.filter((item) => item.id === preferredInterfaceId),
        ...interfaceList.filter((item) => item.id !== preferredInterfaceId),
      ]
      : interfaceList;
    const supportedInterfaces: Record<string, unknown>[] = [];
    for (let index = 0; index < orderedInterfaces.length; index += 1) {
      const item = orderedInterfaces[index];
      const normalized = normalizeInterface({
        url: item.url.trim(),
        protocolVersion: item.protocolVersion.trim(),
        protocolBinding: item.protocolBinding.trim() || DEFAULT_INTERFACE_TRANSPORT,
        tenant: item.tenant.trim(),
      });
      if (!normalized.url) {
        toast.error(t('agent.urlRequired') + ` (#${index + 1})`);
        return null;
      }
      if (!normalized.protocolVersion) {
        toast.error(t('agent.protocolVersionRequired') + ` (#${index + 1})`);
        return null;
      }
      supportedInterfaces.push(normalized);
    }

    const agentCard: Record<string, unknown> = {
      name: name.trim(),
      description: description.trim() || undefined,
      version: version.trim(),
      supportedInterfaces,
      capabilities: {
        streaming,
        pushNotifications,
        stateTransitionHistory,
        extendedAgentCard: supportsAuthenticatedExtendedCard,
      },
    };

    if (iconUrl.trim()) agentCard.iconUrl = iconUrl.trim();
    if (documentationUrl.trim()) agentCard.documentationUrl = documentationUrl.trim();
    if (organization.trim() || providerUrl.trim()) {
      agentCard.provider = { organization: organization.trim(), url: providerUrl.trim() };
    }
    if (skills !== undefined) agentCard.skills = skills;
    if (security !== undefined) agentCard.security = security;
    if (securitySchemes !== undefined) agentCard.securitySchemes = securitySchemes;
    const inputModes = defaultInputModes.split(',').map(s => s.trim()).filter(Boolean);
    const outputModes = defaultOutputModes.split(',').map(s => s.trim()).filter(Boolean);
    if (inputModes.length > 0) agentCard.defaultInputModes = inputModes;
    if (outputModes.length > 0) agentCard.defaultOutputModes = outputModes;

    return agentCard;
  };

  const handleSubmit = () => {
    if (!validate()) return;

    if (isEdit) {
      setStrategyDialogOpen(true);
      return;
    }

    doSubmit();
  };

  const doSubmit = async () => {
    const agentCard = validate();
    if (!agentCard) return;

    setSaving(true);
    try {
      if (isEdit) {
        let setAsLatest: boolean;
        if (publishStrategy === 'new-version') {
          setAsLatest = false;
        } else if (publishStrategy === 'set-latest') {
          setAsLatest = true;
        } else {
          // edit-current: maintain existing latest status
          const currentVersionInfo = versionList.find((v) => v.version === version.trim());
          setAsLatest = currentVersionInfo?.latest ?? false;
        }

        await agentApi.updateAgent({
          namespaceId,
          agentName: name.trim(),
          version: version.trim(),
          agentCard: JSON.stringify(agentCard),
          setAsLatest,
        });
        toast.success(t('agent.updateSuccess'));
      } else {
        await agentApi.createAgent({
          namespaceId,
          agentName: name.trim(),
          version: version.trim(),
          registrationType: 'URL',
          agentCard: JSON.stringify(agentCard),
        });
        toast.success(t('agent.createSuccess'));
      }
      setStrategyDialogOpen(false);
      navigate('/agentManagement');
    } catch {
      // handled by interceptor
    } finally {
      setSaving(false);
    }
  };

  if (initLoading) {
    return (
      <div className="space-y-5">
        <Skeleton className="h-10 w-64" />
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
          <div className="lg:col-span-2"><Skeleton className="h-[500px] w-full rounded-xl" /></div>
          <div><Skeleton className="h-[300px] w-full rounded-xl" /></div>
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col min-h-[calc(100vh-120px)]">
      <div className="space-y-5 grow">
      {/* ===== Header ===== */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Button
            variant="ghost"
            size="sm"
            className="h-7 gap-1.5 text-muted-foreground hover:text-foreground -ml-2"
            onClick={() => navigate('/agentManagement')}
          >
            <ArrowLeft className="h-3.5 w-3.5" />
            {t('agent.backToList')}
          </Button>
          <Separator orientation="vertical" className="h-5" />
          <div className="flex items-center gap-2">
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-violet-500 to-fuchsia-400">
              <Bot className="h-4 w-4 text-white" />
            </div>
            <h1 className="text-lg font-bold">
              {isEdit ? t('agent.editAgent') : t('agent.createAgent')}
            </h1>
          </div>
        </div>
      </div>

      {/* ===== Two-column Layout ===== */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
        {/* Left column (2/3) - Core info */}
        <div className="lg:col-span-2 space-y-5">
          {/* Basic Info */}
          <Card className="overflow-hidden py-0 gap-0">
            <div className="px-5 py-3.5 border-b bg-muted/30">
              <h2 className="text-sm font-semibold flex items-center gap-2">
                <Server className="h-4 w-4 text-muted-foreground" />
                {t('agent.basicInfo')}
              </h2>
            </div>
            <CardContent className="p-5 space-y-4">
              {/* Name + Version row */}
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <div className="sm:col-span-2 space-y-2.5">
                  <Label>{t('agent.agentName')} <span className="text-destructive">*</span></Label>
                  <Input
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    placeholder="weather-agent"
                    disabled={isEdit}
                    className="bg-transparent"
                  />
                </div>
                <div className="space-y-2.5">
                  <Label>{t('agent.version')} <span className="text-destructive">*</span></Label>
                  <Input
                    value={version}
                    onChange={(e) => setVersion(e.target.value)}
                    placeholder="1.0.0"
                    className="bg-transparent"
                  />
                </div>
              </div>

              {/* Description */}
              <div className="space-y-2.5">
                <Label>{t('agent.description')}</Label>
                <Textarea
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder={t('agent.descriptionPlaceholder')}
                  rows={3}
                  className="bg-transparent"
                />
              </div>
            </CardContent>
          </Card>

          {/* Supported Interfaces */}
          <Card className="overflow-hidden py-0 gap-0">
            <div className="px-5 py-3.5 border-b bg-muted/30">
              <h2 className="text-sm font-semibold flex items-center gap-2">
                <Link2 className="h-4 w-4 text-muted-foreground" />
                {t('agent.supportedInterfaces', { defaultValue: 'Supported Interfaces' })}
              </h2>
            </div>
            <CardContent className="p-5 space-y-3">
              <div className="flex items-center justify-between">
                <p className="text-xs text-muted-foreground">
                  {t('agent.interfaceRequired', { defaultValue: 'At least one interface is required.' })}
                </p>
                <Button type="button" variant="outline" size="sm" onClick={addInterface}>
                  <Plus className="mr-1 h-3.5 w-3.5" />
                  {t('common.add', { defaultValue: 'Add' })}
                </Button>
              </div>
              <div className="space-y-3">
                {interfaceList.map((item, index) => (
                  <div key={item.id} className="rounded-lg border p-3 space-y-3 bg-muted/10">
                    <div className="flex items-center justify-between">
                      <span className="text-sm font-medium">
                        {t('agent.interfaceItem', { index: index + 1 })}
                      </span>
                      <div className="flex items-center gap-2">
                        <Button
                          type="button"
                          variant={preferredInterfaceId === item.id ? 'default' : 'outline'}
                          size="sm"
                          onClick={() => setPreferredInterfaceId(item.id)}
                        >
                          <Star className="mr-1 h-3.5 w-3.5" />
                          {t('agent.preferredInterface', { defaultValue: 'Preferred' })}
                        </Button>
                        <Button
                          type="button"
                          variant="ghost"
                          size="icon"
                          onClick={() => removeInterface(item.id)}
                          disabled={interfaceList.length <= 1}
                        >
                          <Trash2 className="h-3.5 w-3.5 text-destructive" />
                        </Button>
                      </div>
                    </div>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                      <div className="space-y-2">
                        <Label>{t('agent.serviceUrl')} <span className="text-destructive">*</span></Label>
                        <Input
                          value={item.url}
                          onChange={(e) => updateInterface(item.id, { url: e.target.value })}
                          placeholder="https://api.example.com/agent"
                          className="bg-transparent"
                        />
                      </div>
                      <div className="space-y-2">
                        <Label>{t('agent.protocolVersion')} <span className="text-destructive">*</span></Label>
                        <Input
                          value={item.protocolVersion}
                          onChange={(e) => updateInterface(item.id, { protocolVersion: e.target.value })}
                          placeholder="1.0"
                          className="bg-transparent"
                        />
                      </div>
                    </div>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                      <div className="space-y-2">
                        <Label>{t('agent.transport')} <span className="text-destructive">*</span></Label>
                        <Input
                          value={item.protocolBinding}
                          onChange={(e) => updateInterface(item.id, { protocolBinding: e.target.value })}
                          placeholder={DEFAULT_INTERFACE_TRANSPORT}
                          className="bg-transparent"
                        />
                        <div className="flex gap-1.5">
                          {['JSONRPC', 'GRPC', 'HTTP+JSON'].map((preset) => (
                            <Button
                              key={preset}
                              type="button"
                              variant="secondary"
                              size="sm"
                              className="h-6 px-2 text-[11px] transition-all duration-200 hover:-translate-y-0.5 hover:shadow-sm hover:bg-primary/15"
                              onClick={() => updateInterface(item.id, { protocolBinding: preset })}
                            >
                              {preset}
                            </Button>
                          ))}
                        </div>
                      </div>
                      <div className="space-y-2">
                        <Label>
                          {t('agent.tenant', { defaultValue: 'Tenant' })}
                          <span className="text-xs text-muted-foreground ml-1">
                            ({t('common.optional', { defaultValue: 'optional' })})
                          </span>
                        </Label>
                        <Input
                          value={item.tenant}
                          onChange={(e) => updateInterface(item.id, { tenant: e.target.value })}
                          placeholder={t('agent.tenant', { defaultValue: 'Tenant' })}
                          className="bg-transparent"
                        />
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>

          {/* Skills */}
          <Card className="overflow-hidden py-0 gap-0">
            <div className="px-5 py-3.5 border-b bg-muted/30">
              <h2 className="text-sm font-semibold flex items-center gap-2">
                <Zap className="h-4 w-4 text-amber-500" />
                {t('agent.skills')}
              </h2>
            </div>
            <CardContent className="p-5 space-y-2.5">
              <Textarea
                value={skillsJson}
                onChange={(e) => setSkillsJson(e.target.value)}
                placeholder='[{"name": "weather_query", "description": "Query weather information"}]'
                rows={8}
                className="bg-transparent font-mono text-xs"
              />
              <p className="text-[11px] text-muted-foreground">{t('agent.skillsHelp')}</p>
            </CardContent>
          </Card>
        </div>

        {/* Right column (1/3) - Capabilities, I/O, Provider */}
        <div className="space-y-5">
          {/* Capabilities */}
          <Card className="overflow-hidden py-0 gap-0">
            <div className="px-5 py-3.5 border-b bg-muted/30">
              <h2 className="text-sm font-semibold flex items-center gap-2">
                <Zap className="h-4 w-4 text-muted-foreground" />
                {t('agent.capabilities')}
              </h2>
            </div>
            <CardContent className="p-4 space-y-2">
              <CapabilitySwitch
                icon={<Zap className="h-4 w-4 text-amber-500" />}
                label={t('agent.streaming')}
                description={t('agent.streamingDesc')}
                checked={streaming}
                onCheckedChange={setStreaming}
              />
              <CapabilitySwitch
                icon={<Radio className="h-4 w-4 text-blue-500" />}
                label={t('agent.pushNotifications')}
                description={t('agent.pushNotificationsDesc')}
                checked={pushNotifications}
                onCheckedChange={setPushNotifications}
              />
              <CapabilitySwitch
                icon={<History className="h-4 w-4 text-emerald-500" />}
                label={t('agent.stateHistory')}
                description={t('agent.stateHistoryDesc')}
                checked={stateTransitionHistory}
                onCheckedChange={setStateTransitionHistory}
              />
              <CapabilitySwitch
                icon={<ShieldCheck className="h-4 w-4 text-indigo-500" />}
                label={t('agent.extendedCardSupport')}
                description={t('agent.extendedCardSupportDesc')}
                checked={supportsAuthenticatedExtendedCard}
                onCheckedChange={setSupportsAuthenticatedExtendedCard}
              />
            </CardContent>
          </Card>

          {/* Input/Output Modes */}
          <Card className="overflow-hidden py-0 gap-0">
            <div className="px-5 py-3.5 border-b bg-muted/30">
              <h2 className="text-sm font-semibold flex items-center gap-2">
                <Globe className="h-4 w-4 text-muted-foreground" />
                {t('agent.ioModes')}
              </h2>
            </div>
            <CardContent className="p-4 space-y-3">
              <div className="space-y-2.5">
                <Label className="text-xs">{t('agent.inputModes')}</Label>
                <Input
                  value={defaultInputModes}
                  onChange={(e) => setDefaultInputModes(e.target.value)}
                  placeholder="text, audio, image"
                  className="bg-transparent h-8 text-xs"
                />
              </div>
              <div className="space-y-2.5">
                <Label className="text-xs">{t('agent.outputModes')}</Label>
                <Input
                  value={defaultOutputModes}
                  onChange={(e) => setDefaultOutputModes(e.target.value)}
                  placeholder="text, audio, image"
                  className="bg-transparent h-8 text-xs"
                />
              </div>
            </CardContent>
          </Card>
        </div>
      </div>

      {/* ===== Advanced Config (full-width) ===== */}
      <button
        onClick={() => setShowAdvanced(!showAdvanced)}
        className="flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground transition-colors w-full justify-center py-2"
      >
        {showAdvanced ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
        {showAdvanced ? t('agent.hideAdvanced') : t('agent.showAdvanced')}
      </button>

      {showAdvanced && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
          {/* Left: URLs */}
          <div className="lg:col-span-2">
            <Card className="overflow-hidden py-0 gap-0">
              <div className="px-5 py-3.5 border-b bg-muted/30">
                <h2 className="text-sm font-semibold flex items-center gap-2">
                  <Link2 className="h-4 w-4 text-muted-foreground" />
                  {t('agent.advancedConfig')}
                </h2>
              </div>
              <CardContent className="p-5 space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-2.5">
                    <Label>{t('agent.providerName')}</Label>
                    <Input
                      value={organization}
                      onChange={(e) => setOrganization(e.target.value)}
                      placeholder={t('agent.providerNamePlaceholder')}
                      className="bg-transparent"
                    />
                  </div>
                  <div className="space-y-2.5">
                    <Label>{t('agent.providerUrl')}</Label>
                    <Input
                      value={providerUrl}
                      onChange={(e) => setProviderUrl(e.target.value)}
                      placeholder="https://provider.example.com"
                      className="bg-transparent"
                    />
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-2.5">
                    <Label>{t('agent.iconUrl')}</Label>
                    <div className="flex items-center gap-2">
                      <Input
                        value={iconUrl}
                        onChange={(e) => setIconUrl(e.target.value)}
                        placeholder="https://example.com/icon.png"
                        className="bg-transparent flex-1"
                      />
                      {iconUrl && (
                        <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md border bg-white dark:bg-muted overflow-hidden">
                          <img
                            src={iconUrl}
                            alt="icon preview"
                            className="h-full w-full object-contain p-1"
                            onError={(e) => { (e.target as HTMLImageElement).style.display = 'none'; }}
                            onLoad={(e) => { (e.target as HTMLImageElement).style.display = ''; }}
                          />
                        </div>
                      )}
                    </div>
                  </div>
                  <div className="space-y-2.5">
                    <Label>{t('agent.documentationUrl')}</Label>
                    <Input
                      value={documentationUrl}
                      onChange={(e) => setDocumentationUrl(e.target.value)}
                      placeholder="https://docs.example.com/agent"
                      className="bg-transparent"
                    />
                  </div>
                </div>

              </CardContent>
            </Card>
          </div>

          {/* Right: Security */}
          <div className="space-y-5">
            <Card className="overflow-hidden py-0 gap-0">
              <div className="px-5 py-3.5 border-b bg-muted/30">
                <h2 className="text-sm font-semibold flex items-center gap-2">
                  <Shield className="h-4 w-4 text-rose-500" />
                  {t('agent.security')}
                </h2>
              </div>
              <CardContent className="p-4 space-y-3">
                <div className="space-y-2.5">
                  <Label className="text-xs">{t('agent.security')}</Label>
                  <Textarea
                    value={securityJson}
                    onChange={(e) => setSecurityJson(e.target.value)}
                    placeholder='[{"apiKey": ["read", "write"]}]'
                    rows={3}
                    className="bg-transparent font-mono text-xs"
                  />
                </div>
                <div className="space-y-2.5">
                  <Label className="text-xs">{t('agent.securitySchemes')}</Label>
                  <Textarea
                    value={securitySchemesJson}
                    onChange={(e) => setSecuritySchemesJson(e.target.value)}
                    placeholder='{"type": "apiKey"}'
                    rows={3}
                    className="bg-transparent font-mono text-xs"
                  />
                </div>
              </CardContent>
            </Card>
          </div>
        </div>
      )}

      </div>

      {/* ===== Sticky Bottom Toolbar ===== */}
      <div className="sticky bottom-0 z-10 -mx-6 -mb-6 mt-2">
        <div className="border-t bg-background/95 backdrop-blur-sm py-3 px-6">
          <div className="flex justify-end gap-3">
            <Button variant="outline" onClick={() => navigate('/agentManagement')} disabled={saving}>
              {t('common.cancel')}
            </Button>
            <Button onClick={handleSubmit} disabled={saving}>
              {saving ? t('common.loading') : t('agent.publish')}
            </Button>
          </div>
        </div>
      </div>

      {/* ===== Publish Strategy Dialog ===== */}
      <Dialog open={strategyDialogOpen} onOpenChange={setStrategyDialogOpen}>
        <DialogContent className="sm:max-w-xl">
          <DialogHeader>
            <DialogTitle>{t('agent.publishStrategy')}</DialogTitle>
            <DialogDescription>
              {t('agent.publishStrategyDesc')}{' '}
              <span className="font-mono font-semibold text-foreground">{version}</span>
            </DialogDescription>
          </DialogHeader>

          <RadioGroup
            value={publishStrategy}
            onValueChange={(val) => setPublishStrategy(val as PublishStrategy)}
            className="grid grid-cols-1 gap-2.5 py-1"
          >
            {([
              {
                key: 'new-version' as const,
                label: t('agent.strategyNewVersion'),
                desc: t('agent.strategyNewVersionDesc'),
                accent: 'border-emerald-500/70 bg-emerald-50/60 dark:bg-emerald-950/25',
                dot: 'text-emerald-600 dark:text-emerald-400',
                ring: 'ring-emerald-500/20',
              },
              {
                key: 'set-latest' as const,
                label: t('agent.strategySetLatest'),
                desc: t('agent.strategySetLatestDesc'),
                accent: 'border-blue-500/70 bg-blue-50/60 dark:bg-blue-950/25',
                dot: 'text-blue-600 dark:text-blue-400',
                ring: 'ring-blue-500/20',
              },
              {
                key: 'edit-current' as const,
                label: t('agent.strategyEditCurrent'),
                desc: t('agent.strategyEditCurrentDesc'),
                accent: 'border-orange-500/70 bg-orange-50/60 dark:bg-orange-950/25',
                dot: 'text-orange-600 dark:text-orange-400',
                ring: 'ring-orange-500/20',
              },
            ]).map(({ key, label, desc, accent, dot, ring }) => {
              const { available, reason } = strategyAvailability[key];
              const isSelected = publishStrategy === key && available;
              return (
                <label
                  key={key}
                  className={cn(
                    'relative flex items-start gap-3 rounded-lg border px-4 py-3 transition-all',
                    available ? 'cursor-pointer hover:bg-muted/40' : 'opacity-40 cursor-not-allowed',
                    isSelected ? cn(accent, 'ring-2', ring) : 'border-border'
                  )}
                >
                  <RadioGroupItem value={key} disabled={!available} className="mt-0.5" />
                  <div className="flex-1 space-y-0.5">
                    <span className={cn('text-sm font-medium', isSelected && dot)}>{label}</span>
                    <p className="text-xs text-muted-foreground leading-relaxed">
                      {!available && reason ? reason : desc}
                    </p>
                  </div>
                </label>
              );
            })}
          </RadioGroup>

          <DialogFooter className="gap-2 sm:gap-0">
            <Button variant="outline" onClick={() => setStrategyDialogOpen(false)} disabled={saving}>
              {t('common.cancel')}
            </Button>
            <Button onClick={doSubmit} disabled={saving || !strategyAvailability[publishStrategy].available}>
              {saving ? t('common.loading') : t('agent.confirmPublish')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function CapabilitySwitch({
  icon,
  label,
  description,
  checked,
  onCheckedChange,
}: {
  icon: React.ReactNode;
  label: string;
  description: string;
  checked: boolean;
  onCheckedChange: (v: boolean) => void;
}) {
  return (
    <div className={cn(
      'flex items-center gap-3 rounded-lg border px-3.5 py-2.5 transition-colors',
      checked ? 'border-primary/30 bg-primary/5' : 'border-border/60'
    )}>
      <div className="flex items-center justify-center h-8 w-8 rounded-md bg-background shrink-0">
        {icon}
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-sm font-medium leading-tight">{label}</p>
        <p className="text-[11px] text-muted-foreground">{description}</p>
      </div>
      <Switch checked={checked} onCheckedChange={onCheckedChange} className="scale-[0.75] shrink-0" />
    </div>
  );
}
