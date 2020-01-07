/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import { Button, Dialog, Pagination, Table, ConfigProvider } from '@alifd/next';
import { connect } from 'react-redux';
import { getPermissions, createPermission, deletePermission } from '../../../reducers/authority';
import RegionGroup from '../../../components/RegionGroup';
import NewPermissions from './NewPermissions';

import './PermissionsManagement.scss';

@connect(state => ({ permissions: state.authority.permissions }), { getPermissions })
@ConfigProvider.config
class PermissionsManagement extends React.Component {
  static displayName = 'PermissionsManagement';

  static propTypes = {
    locale: PropTypes.object,
    permissions: PropTypes.object,
    getPermissions: PropTypes.func,
  };

  constructor(props) {
    super(props);
    this.state = {
      loading: true,
      pageNo: 1,
      pageSize: 9,
      createPermission: false,
    };
  }

  componentDidMount() {
    this.getPermissions();
  }

  getPermissions() {
    const { pageNo, pageSize } = this.state;
    this.props
      .getPermissions({ pageNo, pageSize })
      .then(() => {
        if (this.state.loading) {
          this.setState({ loading: false });
        }
      })
      .catch(() => this.setState({ loading: false }));
  }

  colseCreatePermission() {
    this.setState({ createPermissionVisible: false });
  }

  render() {
    const { permissions } = this.props;
    const { loading, pageSize, pageNo, createPermissionVisible } = this.state;
    return (
      <>
        <RegionGroup left={'权限管理'} />
        <div className="filter-panel">
          <Button type="primary" onClick={() => this.setState({ createPermissionVisible: true })}>
            添加权限
          </Button>
        </div>
        <Table dataSource={permissions.pageItems} loading={loading} maxBodyHeight={476} fixedHeader>
          <Table.Column title="角色" dataIndex="role" />
          <Table.Column title="资源" dataIndex="resource" />
          <Table.Column title="动作" dataIndex="action" />
          <Table.Column
            title="操作"
            cell={(value, index, record) => (
              <>
                <Button
                  type="primary"
                  warning
                  onClick={() =>
                    Dialog.confirm({
                      title: '确认',
                      content: '是否要删除该权限？',
                      onOk: () =>
                        deletePermission(record).then(() =>
                          this.setState({ pageNo: 1 }, () => this.getPermissions())
                        ),
                    })
                  }
                >
                  刪除
                </Button>
              </>
            )}
          />
        </Table>
        {permissions.totalCount > pageSize && (
          <Pagination
            className="users-pagination"
            current={pageNo}
            total={permissions.totalCount}
            pageSize={pageSize}
            onChange={pageNo => this.setState({ pageNo }, () => this.getPermissions())}
          />
        )}
        <NewPermissions
          visible={createPermissionVisible}
          onOk={permission =>
            createPermission(permission).then(res => {
              this.setState({ pageNo: 1 }, () => this.getPermissions());
              return res;
            })
          }
          onCancel={() => this.colseCreatePermission()}
        />
      </>
    );
  }
}

export default PermissionsManagement;
