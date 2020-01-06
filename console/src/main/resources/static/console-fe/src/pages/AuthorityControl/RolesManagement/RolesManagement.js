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
import { getRoles, createRole, deleteRole } from '../../../reducers/authority';
import RegionGroup from '../../../components/RegionGroup';
import NewRole from './NewRole';

import './RolesManagement.scss';

@connect(state => ({ roles: state.authority.roles }), { getRoles })
@ConfigProvider.config
class RolesManagement extends React.Component {
  static displayName = 'RolesManagement';

  static propTypes = {
    locale: PropTypes.object,
    roles: PropTypes.object,
    getRoles: PropTypes.func,
  };

  constructor(props) {
    super(props);
    this.state = {
      loading: true,
      pageNo: 1,
      pageSize: 9,
    };
  }

  componentDidMount() {
    this.getRoles();
  }

  getRoles() {
    const { pageNo, pageSize } = this.state;
    this.props
      .getRoles({ pageNo, pageSize })
      .then(() => {
        if (this.state.loading) {
          this.setState({ loading: false });
        }
      })
      .catch(() => this.setState({ loading: false }));
  }

  colseCreateRole() {
    this.setState({ createRoleVisible: false });
  }

  render() {
    const { roles } = this.props;
    const { loading, pageSize, pageNo, createRoleVisible, passwordResetUser } = this.state;
    return (
      <>
        <RegionGroup left={'角色管理'} />
        <div className="filter-panel">
          <Button
            type="primary"
            className="create-user-btn"
            onClick={() => this.setState({ createRoleVisible: true })}
          >
            绑定角色
          </Button>
        </div>
        <Table dataSource={roles.pageItems} loading={loading} maxBodyHeight={476} fixedHeader>
          <Table.Column title="角色名" dataIndex="role" />
          <Table.Column title="用户名" dataIndex="username" />
          <Table.Column
            title="操作"
            dataIndex="username"
            cell={(value, index, record) => (
              <>
                <Button
                  type="primary"
                  warning
                  onClick={() =>
                    Dialog.confirm({
                      title: '确认',
                      content: '是否要删除该角色？',
                      onOk: () =>
                        deleteRole(record).then(() =>
                          this.setState({ pageNo: 1 }, () => this.getRoles())
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
        {roles.totalCount > pageSize && (
          <Pagination
            className="users-pagination"
            current={pageNo}
            total={roles.totalCount}
            pageSize={pageSize}
            onChange={pageNo => this.setState({ pageNo }, () => this.getRoles())}
          />
        )}
        <NewRole
          visible={createRoleVisible}
          onOk={role =>
            createRole(role).then(res => {
              this.getRoles();
              return res;
            })
          }
          onCancel={() => this.colseCreateRole()}
        />
      </>
    );
  }
}

export default RolesManagement;
