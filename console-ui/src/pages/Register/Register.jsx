import React from 'react';
import { Card, Form, Input, Message, ConfigProvider, Field, Dialog } from '@alifd/next';
import { withRouter } from 'react-router-dom';

import './index.scss';
import Header from '../../layouts/Header';
import PropTypes from 'prop-types';
import { admin, guide, state } from '../../reducers/base';
import { connect } from 'react-redux';
import { generateRandomPassword, goLogin } from '../../globalLib';
import { LOGINPAGE_ENABLED } from '../../constants';

const FormItem = Form.Item;

@withRouter
@ConfigProvider.config
@connect(state => ({ ...state.locale }))
class Register extends React.Component {
  static displayName = 'Register';

  static propTypes = {
    locale: PropTypes.object,
    history: PropTypes.object,
  };

  constructor(props) {
    super(props);
    this.state = {
      consoleUiEnable: true,
      guideMsg: '',
    };
    this.field = new Field(this);
  }

  componentDidMount() {
    if (localStorage.getItem('token')) {
      const [baseUrl] = location.href.split('#');
      location.href = `${baseUrl}#/`;
    }
    this.handleSearch();
  }

  handleSearch = () => {
    state().then(res => {
      if (res?.console_ui_enabled === 'false') {
        this.setState({ consoleUiEnable: true });
        guide().then(res => {
          this.setState({ guideMsg: res?.data });
        });
      } else {
        this.setState({ consoleUiEnable: false });
      }
    });
  };

  handleSubmit = () => {
    const { locale = {} } = this.props;
    this.field.validate((errors, values) => {
      if (errors) {
        return;
      }

      const data = {
        password: generateRandomPassword(10),
        ...values
      };

      admin(data)
        .then(res => {
          if (res.username && res.password) {
            localStorage.setItem('token', JSON.stringify(res));
            Dialog.alert({
              title: locale.Login.initPassword + locale.ListeningToQuery.success,
              content: locale.Password.newPassword + '：' + res.password,
              onOk: () => {
                this.props.history.push('/');
              }
            });
          } else {
            Dialog.alert({
              title: locale.Login.initPassword + locale.ListeningToQuery.failure,
              content: res.data,
              onOk: () => {
                const _LOGINPAGE_ENABLED = localStorage.getItem(LOGINPAGE_ENABLED);

                if (_LOGINPAGE_ENABLED !== 'false') {
                  let token = {};
                  try {
                    token = JSON.parse(localStorage.token);
                  } catch (e) {
                    console.log('Token Error', localStorage.token, e);
                    goLogin();
                  }
                } else {
                  this.props.history.push('/');
                }
              }
            });
          }
        })
        .catch(() => {
          Message.error({
            content: locale.Login.invalidUsernameOrPassword,
          });
        });
    });
  };

  onKeyDown = event => {
    // 'keypress' event misbehaves on mobile so we track 'Enter' key via 'keydown' event
    if (event.key === 'Enter') {
      event.preventDefault();
      event.stopPropagation();
      this.handleSubmit();
    }
  };

  render() {
    const { locale = {} } = this.props;
    const { consoleUiEnable, guideMsg } = this.state;

    return (
      <div className="home-page">
        <Header />
        <section
          className="top-section"
          style={{
            background: 'url(img/black_dot.png) repeat',
            backgroundSize: '14px 14px',
          }}
        >
          <div className="vertical-middle product-area">
            <img className="product-logo" src="img/nacos.png" />
            <p className="product-desc">{locale.Login.productDesc}</p>
          </div>
          <div className="animation animation1" />
          <div className="animation animation2" />
          <div className="animation animation3" />
          <div className="animation animation4" />
          <div className="animation animation5" />
          <Card className="login-panel" contentHeight="auto">
            <div className="login-header">{locale.Login.initPassword}</div>
            <div className="internal-sys-tip">
              <div>{locale.Login.internalSysTip1}</div>
              <div>{locale.Login.internalSysTip2}</div>
            </div>
            {!consoleUiEnable && (
              <Form className="login-form" field={this.field}>
                <FormItem>
                  <Input
                    value="nacos"
                    readOnly
                    placeholder={locale.Login.pleaseInputUsername}
                  />
                </FormItem>
                <FormItem>
                  <Input
                    htmlType="password"
                    placeholder={locale.Login.pleaseInputPasswordTips}
                    {...this.field.init('password', {})}
                    onKeyDown={this.onKeyDown}
                  />
                </FormItem>
                <FormItem label=" ">
                  <Form.Submit onClick={this.handleSubmit}>{locale.Login.submit}</Form.Submit>
                </FormItem>
              </Form>
            )}
            {consoleUiEnable && (
              <Message type="notice" style={{ marginTop: 30 }}>
                <div dangerouslySetInnerHTML={{ __html: guideMsg }} />
              </Message>
            )}
          </Card>
        </section>
      </div>
    );
  }
}

export default Register;
