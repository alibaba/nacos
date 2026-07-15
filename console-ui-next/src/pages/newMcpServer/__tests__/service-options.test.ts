import { describe, expect, it, vi } from 'vitest';
import { loadServiceOptions } from '../service-options';

describe('newMcpServer service options', () => {
  it('loads every service page so the existing-service selector is not capped at 100 items', async () => {
    const listServices = vi.fn()
      .mockResolvedValueOnce({
        data: {
          totalCount: 101,
          pageItems: Array.from({ length: 100 }, (_, index) => ({
            groupName: 'DEFAULT_GROUP',
            name: `service-${index + 1}`,
          })),
        },
      })
      .mockResolvedValueOnce({
        data: {
          totalCount: 101,
          pageItems: [{ groupName: 'DEFAULT_GROUP', name: 'service-101' }],
        },
      });

    const options = await loadServiceOptions('public', listServices);

    expect(listServices).toHaveBeenCalledTimes(2);
    expect(listServices).toHaveBeenNthCalledWith(1, {
      namespaceId: 'public',
      pageNo: 1,
      pageSize: 100,
      serviceNameParam: undefined,
    });
    expect(listServices).toHaveBeenNthCalledWith(2, {
      namespaceId: 'public',
      pageNo: 2,
      pageSize: 100,
      serviceNameParam: undefined,
    });
    expect(options).toHaveLength(101);
    expect(options[options.length - 1]).toEqual({
      label: 'DEFAULT_GROUP / service-101',
      value: 'DEFAULT_GROUP@@service-101',
    });
  });

  it('passes the search keyword to the service list API', async () => {
    const listServices = vi.fn().mockResolvedValueOnce({
      data: {
        totalCount: 1,
        pageItems: [{ groupName: 'DEFAULT_GROUP', name: 'payment-service' }],
      },
    });

    await loadServiceOptions('public', listServices, 'payment');

    expect(listServices).toHaveBeenCalledWith({
      namespaceId: 'public',
      pageNo: 1,
      pageSize: 100,
      serviceNameParam: 'payment',
    });
  });
});
