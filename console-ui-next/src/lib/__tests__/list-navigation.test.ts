import { describe, expect, it } from 'vitest';

import {
  buildConfigDetailPath,
  buildConfigListPathFromDetail,
  buildServiceDetailPath,
  buildServiceListPathFromDetail,
} from '../list-navigation';

describe('list navigation state', () => {
  it('preserves config list pagination when opening and returning from detail', () => {
    const detailPath = buildConfigDetailPath('a.yaml', 'DEFAULT_GROUP', 'public', {
      dataId: 'a',
      groupName: 'DEFAULT',
      appName: 'console',
      searchMode: 'accurate',
      pageNo: 3,
      pageSize: 20,
    });

    const detailParams = new URLSearchParams(detailPath.split('?')[1]);

    expect(detailParams.get('dataId')).toBe('a.yaml');
    expect(detailParams.get('listPageNo')).toBe('3');
    expect(detailParams.get('listPageSize')).toBe('20');
    expect(buildConfigListPathFromDetail(detailParams)).toBe(
      '/configurationManagement?dataId=a&groupName=DEFAULT&appName=console&searchMode=accurate&pageNo=3&pageSize=20'
    );
  });

  it('preserves service list pagination when opening and returning from detail', () => {
    const detailPath = buildServiceDetailPath('svc', 'DEFAULT_GROUP', 'public', {
      serviceNameParam: 'svc',
      groupNameParam: 'DEFAULT',
      ignoreEmptyService: false,
      pageNo: 4,
      pageSize: 50,
    });

    const detailParams = new URLSearchParams(detailPath.split('?')[1]);

    expect(detailParams.get('serviceName')).toBe('svc');
    expect(detailParams.get('listPageNo')).toBe('4');
    expect(detailParams.get('listPageSize')).toBe('50');
    expect(buildServiceListPathFromDetail(detailParams, 'public')).toBe(
      '/serviceManagement?namespace=public&serviceNameParam=svc&groupNameParam=DEFAULT&ignoreEmptyService=false&pageNo=4&pageSize=50'
    );
  });
});
