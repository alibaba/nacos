import type { AgentDetailInfo, AgentInterface } from '@/types/agent';

export interface AgentCardDisplayModel {
  primaryInterface: AgentInterface | null;
  interfaceList: AgentInterface[];
  transport: string;
  protocolVersion: string;
  serviceUrl: string;
  extendedCardSupported: boolean;
}

export function mapAgentCardForDisplay(agent: AgentDetailInfo): AgentCardDisplayModel {
  const normalizedInterfaces = normalizeInterfaces(agent.supportedInterfaces);
  const primaryInterface = normalizedInterfaces.length > 0 ? normalizedInterfaces[0] : null;
  const transport = primaryInterface?.protocolBinding || primaryInterface?.transport || '';
  const protocolVersion = primaryInterface?.protocolVersion || '';
  const serviceUrl = primaryInterface?.url || '';
  const extendedCardSupported = !!agent.capabilities?.extendedAgentCard;
  return {
    primaryInterface,
    interfaceList: normalizedInterfaces,
    transport,
    protocolVersion,
    serviceUrl,
    extendedCardSupported,
  };
}

function normalizeInterfaces(interfaces?: AgentInterface[]): AgentInterface[] {
  if (!interfaces || interfaces.length === 0) {
    return [];
  }
  return interfaces
    .filter((item) => !!item && !!item.url)
    .map((item) => ({
      ...item,
      protocolBinding: item.protocolBinding || item.transport || '',
      transport: item.transport || item.protocolBinding || '',
      protocolVersion: item.protocolVersion || '',
      tenant: item.tenant || '',
    }));
}
