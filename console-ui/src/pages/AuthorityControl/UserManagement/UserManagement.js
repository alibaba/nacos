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
  Input,
  Switch,
} from '@alifd/next';
import { connect } from 'react-redux';
import { getUsers, createUser, deleteUser, passwordReset } from '../../../reducers/authority';
import RegionGroup from '../../../components/RegionGroup';
import NewUser from './NewUser';
import PasswordReset from './PasswordReset';

import './UserManagement.scss';
import { getParams } from '../../../globalLib';

@connect(state => ({ users: state.authority.users }), { getUsers })
@ConfigProvider.config
class UserManagement extends React.Component {
  static displayName = 'UserManagement';

  static propTypes = {
    locale: PropTypes.object,
    users: PropTypes.object,
    getUsers: PropTypes.func,
    createUser: PropTypes.func,
  };

  constructor(props) {
    super(props);
    this.username = getParams('username');
    this.state = {
      loading: true,
      pageNo: 1,
      pageSize: 9,
      username: this.username,
      defaultFuzzySearch: true,
    };
    this.handleDefaultFuzzySwitchChange = this.handleDefaultFuzzySwitchChange.bind(this);
  }

  componentDidMount() {
    this.getUsers();
  }

  getUsers() {
    this.setState({ loading: true });
    const params = {
      pageNo: this.state.pageNo,
      pageSize: this.state.pageSize,
      username: this.username,
      search: 'blur',
    };
    if (this.state.defaultFuzzySearch) {
      if (params.username && params.username !== '') {
        params.username = `*${params.username}*`;
      }
    }
    if (params.username && params.username.indexOf('*') !== -1) {
      params.search = 'blur';
    } else {
      params.search = 'accurate';
    }
    this.props
      .getUsers({
        pageNo: params.pageNo,
        pageSize: params.pageSize,
        username: params.username,
        search: params.search,
      })
      .then(() => {
        if (this.state.loading) {
          this.setState({ loading: false });
        }
      })
      .catch(() => this.setState({ loading: false }));
  }

  colseCreateUser() {
    this.setState({ createUserVisible: false });
  }

  handleDefaultFuzzySwitchChange() {
    this.setState({
      defaultFuzzySearch: !this.state.defaultFuzzySearch,
    });
  }

  render() {
    const { users, locale } = this.props;
    const {
      loading,
      pageSize,
      pageNo,
      createUserVisible,
      passwordResetUserVisible,
      passwordResetUser,
    } = this.state;
    return (
      <>
        <RegionGroup left={locale.userManagement} />
        <Form inline>
          <Form.Item label="用户名">
            <Input
              value={this.username}
              htmlType="text"
              placeholder={this.state.defaultFuzzySearch ? locale.defaultFuzzyd : locale.fuzzyd}
              style={{ width: 200 }}
              onChange={username => {
                this.username = username;
                this.setState({ username });
              }}
            />
          </Form.Item>
          <Form.Item label="默认模糊匹配">
            <Switch
              checkedChildren=""
              unCheckedChildren=""
              defaultChecked={this.state.defaultFuzzySearch}
              onChange={this.handleDefaultFuzzySwitchChange}
              title={'自动在搜索参数前后加上*'}
            />
          </Form.Item>
          <Form.Item label={''}>
            <Button
              type={'primary'}
              style={{ marginRight: 10 }}
              onClick={() => this.getUsers()}
              data-spm-click={'gostr=/aliyun;locaid=dashsearch'}
            >
              {locale.query}
            </Button>
          </Form.Item>
          <Form.Item style={{ float: 'right' }}>
            <Button
              type="primary"
              onClick={() => this.setState({ createUserVisible: true })}
              style={{ marginRight: 20 }}
            >
              {locale.createUser}
            </Button>
          </Form.Item>
        </Form>

        <Table dataSource={users.pageItems} loading={loading} maxBodyHeight={476} fixedHeader>
          <Table.Column title={locale.username} dataIndex="username" />
          <Table.Column
            title={locale.password}
            dataIndex="password"
            cell={value => value.replace(/\S/g, '*')}
          />
          <Table.Column
            title={locale.operation}
            dataIndex="username"
            cell={username => (
              <>
                <Button
                  type="primary"
                  onClick={() =>
                    this.setState({ passwordResetUser: username, passwordResetUserVisible: true })
                  }
                >
                  {locale.resetPassword}
                </Button>
                &nbsp;&nbsp;&nbsp;
                <Button
                  type="primary"
                  warning
                  onClick={() =>
                    Dialog.confirm({
                      title: locale.deleteUser,
                      content: locale.deleteUserTip,
                      onOk: () =>
                        deleteUser(username).then(() => {
                          this.setState({ pageNo: 1 }, () => this.getUsers());
                        }),
                    })
                  }
                >
                  {locale.deleteUser}
                </Button>
              </>
            )}
          />
        </Table>
        {users.totalCount > pageSize && (
          <Pagination
            className="users-pagination"
            current={pageNo}
            total={users.totalCount}
            pageSize={pageSize}
            onChange={pageNo => this.setState({ pageNo }, () => this.getUsers())}
          />
        )}
        <NewUser
          visible={createUserVisible}
          onOk={user =>
            createUser(user).then(res => {
              this.setState({ pageNo: 1 }, () => this.getUsers());
              return res;
            })
          }
          onCancel={() => this.colseCreateUser()}
        />
        <PasswordReset
          visible={passwordResetUserVisible}
          username={passwordResetUser}
          onOk={user =>
            passwordReset(user).then(res => {
              this.getUsers();
              return res;
            })
          }
          onCancel={() =>
            this.setState({ passwordResetUser: undefined, passwordResetUserVisible: false })
          }
        />
      </>
    );
  }
}

export default UserManagement;
