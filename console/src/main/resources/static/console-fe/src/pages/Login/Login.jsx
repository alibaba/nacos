import React from 'react';
import { Card, Form, Input, Message } from '@alifd/next';
import { withRouter } from 'react-router-dom';

import './index.scss';
import Header from '../../layouts/Header';
import { request } from '../../globalLib';

const FormItem = Form.Item;

@withRouter
class Login extends React.Component {
  handleSubmit = values => {
    request({
      type: 'get',
      url: 'v1/auth/login',
      data: values,
      success: res => {
        if (res.code === 200) {
          const data = res.data;
          // TODO: 封装一个方法存储、读取token
          localStorage.setItem('token', data);
          // TODO: 使用react router
          this.props.history.push('/');
        }
      },
      error: () => {
        Message.error({
          content: '登录失败',
        });
      },
    });
  };

  render() {
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
            <p className="product-desc">
              an easy-to-use dynamic service discovery, configuration and service management
              platform for building cloud native applications
            </p>
          </div>
          <div className="animation animation1" />
          <div className="animation animation2" />
          <div className="animation animation3" />
          <div className="animation animation4" />
          <div className="animation animation5" />
          <Card className="login-panel" contentHeight="auto">
            <div className="login-header">请登录</div>
            <Form className="login-form">
              <FormItem>
                <Input htmlType="text" name="username" placeholder="请输入账号名" />
              </FormItem>
              <FormItem>
                <Input htmlType="password" name="password" placeholder="请输入密码" />
              </FormItem>
              <FormItem label=" ">
                <Form.Submit onClick={this.handleSubmit}>提交</Form.Submit>
              </FormItem>
            </Form>
          </Card>
        </section>
      </div>
    );
  }
}

export default Login;
