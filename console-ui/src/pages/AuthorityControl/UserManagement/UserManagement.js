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
import { Button, Dialog, Pagination, Table, ConfigProvider } from '@alifd/next';
import { connect } from 'react-redux';
import { getUsers, createUser, deleteUser, passwordReset } from '../../../reducers/authority';
import RegionGroup from '../../../components/RegionGroup';
import NewUser from './NewUser';
import PasswordReset from './PasswordReset';

import './UserManagement.scss';

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
    this.state = {
      loading: true,
      pageNo: 1,
      pageSize: 9,
    };
  }

  componentDidMount() {
    this.getUsers();
  }

  getUsers() {
    this.setState({ loading: true });
    const { pageNo, pageSize } = this.state;
    this.props
      .getUsers({ pageNo, pageSize })
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

  render() {
    const { users, locale } = this.props;
    const { loading, pageSize, pageNo, createUserVisible, passwordResetUser } = this.state;
    return (
      <>
        <RegionGroup left={locale.userManagement} />
        <div className="filter-panel">
          <Button
            type="primary"
            onClick={() => this.setState({ createUserVisible: true })}
            style={{ marginRight: 20 }}
          >
            {locale.createUser}
          </Button>
          <Button type="secondary" onClick={() => this.getUsers()}>
            {locale.refresh}
          </Button>
        </div>
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
                  onClick={() => this.setState({ passwordResetUser: username })}
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
          username={passwordResetUser}
          onOk={user =>
            passwordReset(user).then(res => {
              this.getUsers();
              return res;
            })
          }
          onCancel={() => this.setState({ passwordResetUser: undefined })}
        />
      </>
    );
  }
}

export default UserManagement;
