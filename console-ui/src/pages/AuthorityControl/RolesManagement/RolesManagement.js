/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {
  Button,
  Dialog,
  Pagination,
  Table,
  ConfigProvider,
  Form,
  Switch,
  Input,
} from '@alifd/next';
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
      role: '',
      defaultFuzzySearch: true,
    };
    this.handleDefaultFuzzySwitchChange = this.handleDefaultFuzzySwitchChange.bind(this);
  }

  componentDidMount() {
    this.getRoles();
  }

  getRoles() {
    this.setState({ loading: true });
    const { pageNo, pageSize } = this.state;
    let { username, role } = this.state;
    let search = 'accurate';

    if (this.state.defaultFuzzySearch) {
      if (username && username !== '') {
        username = `*${username}*`;
      }
      if (role && role !== '') {
        role = `*${role}*`;
      }
    }
    if (role && role.indexOf('*') !== -1) {
      search = 'blur';
    }
    if (username && username.indexOf('*') !== -1) {
      search = 'blur';
    }

    this.props
      .getRoles({ pageNo, pageSize, role, username, search })
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

  handleDefaultFuzzySwitchChange() {
    this.setState({
      defaultFuzzySearch: !this.state.defaultFuzzySearch,
    });
  }

  render() {
    const { roles, locale } = this.props;
    const { loading, pageSize, pageNo, createRoleVisible, passwordResetUser } = this.state;
    return (
      <>
        <RegionGroup left={locale.roleManagement} />

        <Form inline>
          <Form.Item label="用户名">
            <Input
              value={this.state.username}
              htmlType="text"
              placeholder={this.state.defaultFuzzySearch ? locale.defaultFuzzyd : locale.fuzzyd}
              style={{ width: 200 }}
              onChange={username => {
                this.setState({ username });
              }}
            />
          </Form.Item>
          <Form.Item label="角色名">
            <Input
              value={this.state.role}
              htmlType="text"
              placeholder={this.state.defaultFuzzySearch ? locale.defaultFuzzyd : locale.fuzzyd}
              style={{ width: 200 }}
              onChange={role => {
                this.setState({ role });
              }}
            />
          </Form.Item>
          <Form.Item label={locale.fuzzydMode}>
            <Switch
              checkedChildren=""
              unCheckedChildren=""
              defaultChecked={this.state.defaultFuzzySearch}
              onChange={this.handleDefaultFuzzySwitchChange}
              title={locale.fuzzyd}
            />
          </Form.Item>
          <Form.Item label={''}>
            <Button
              type={'primary'}
              style={{ marginRight: 10 }}
              onClick={() => {
                this.setState({ pageNo: 1 }, () => {
                  this.getRoles();
                });
              }}
              data-spm-click={'gostr=/aliyun;locaid=dashsearch'}
            >
              {locale.query}
            </Button>
          </Form.Item>
          <Form.Item style={{ float: 'right' }}>
            <Button
              type="primary"
              onClick={() => this.setState({ createRoleVisible: true })}
              style={{ marginRight: 20 }}
            >
              {locale.bindingRoles}
            </Button>
          </Form.Item>
        </Form>
        <Table dataSource={roles.pageItems} loading={loading} maxBodyHeight={476} fixedHeader>
          <Table.Column title={locale.role} dataIndex="role" />
          <Table.Column title={locale.username} dataIndex="username" />
          <Table.Column
            title={locale.operation}
            dataIndex="role"
            cell={(value, index, record) => {
              if (value === 'ROLE_ADMIN') {
                return null;
              }
              return (
                <Button
                  type="primary"
                  warning
                  onClick={() =>
                    Dialog.confirm({
                      title: locale.deleteRole,
                      content: locale.deleteRoleTip,
                      onOk: () =>
                        deleteRole(record).then(() => {
                          this.setState({ pageNo: 1 }, () => this.getRoles());
                        }),
                    })
                  }
                >
                  {locale.deleteRole}
                </Button>
              );
            }}
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
