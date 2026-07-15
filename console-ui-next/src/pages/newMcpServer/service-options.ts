import type { ServiceListParams, ServiceListResponse } from '@/types/service';

const SERVICE_SELECTOR_PAGE_SIZE = 100;

type ListServices = (params: ServiceListParams) => Promise<unknown>;

type ServiceOption = {
  label: string;
  value: string;
};

function unwrapServiceListResponse(response: unknown): ServiceListResponse {
  return (response as { data?: ServiceListResponse }).data || { totalCount: 0, pageItems: [] };
}

export async function loadServiceOptions(
  namespaceId: string,
  listServices: ListServices,
  serviceNameParam?: string
): Promise<ServiceOption[]> {
  const options: ServiceOption[] = [];
  const trimmedKeyword = serviceNameParam?.trim();
  let pageNo = 1;
  let totalCount = Number.POSITIVE_INFINITY;

  while (options.length < totalCount) {
    const data = unwrapServiceListResponse(
      await listServices({
        namespaceId,
        pageNo,
        pageSize: SERVICE_SELECTOR_PAGE_SIZE,
        serviceNameParam: trimmedKeyword || undefined,
      })
    );
    const pageItems = data.pageItems || [];
    totalCount = data.totalCount ?? options.length + pageItems.length;

    options.push(
      ...pageItems.map((item) => ({
        label: `${item.groupName} / ${item.name}`,
        value: `${item.groupName}@@${item.name}`,
      }))
    );

    if (pageItems.length === 0) {
      break;
    }
    pageNo += 1;
  }

  return options;
}
