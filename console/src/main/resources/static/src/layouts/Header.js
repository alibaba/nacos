import React from 'react';
import classnames from 'classnames';
import siteConfig from '../config';
import { getLink } from '../utils/nacosutil';
import './index.css';



const languageSwitch = [
  {
    text: '中',
    value: 'en-us',
  },
  {
    text: 'En',
    value: 'zh-cn',
  },
];
const noop = () => {};

const defaultProps = {
  type: 'primary',
  language: 'en-us',
  onLanguageChange: noop,
};


class Header extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      menuBodyVisible: false,
      language: props.language,
    };
    
    this.switchLang = this.switchLang.bind(this);
  }

  toggleMenu() {
    this.setState({
      menuBodyVisible: !this.state.menuBodyVisible,
    });
  }

  switchLang() {
    let language;
    if (this.state.language === 'zh-cn') {
      language = 'en-us';
    } else {
      language = 'zh-cn';
    }
    this.setState({
      language,
    });
    this.props.onLanguageChange(language);
  }

  UNSAFE_componentWillReceiveProps(nextProps) {
    this.setState({
      language: nextProps.language,
    });
  }

  render() {
    const { type, logo, onLanguageChange, currentKey } = this.props;
    const { menuBodyVisible, language } = this.state;
    return (
      <header
        className={
          classnames({
            'header-container': true,
            [`header-container-${type}`]: true,
          })
        }
      >
        <div className="header-body">
          <a href={'https://nacos.io/zh-cn/'} target="_blank" rel="noopener noreferrer">
            <img className="logo" alt={siteConfig.name} title={siteConfig.name} src={logo} />
          </a>
          {
            onLanguageChange !== noop ?
            (<span
              className={
                classnames({
                  'language-switch': true,
                  [`language-switch-${type}`]: true,
                })
              }
              onClick={this.switchLang}
            >
              {languageSwitch.find(lang => lang.value === language).text}
            </span>)
            :
            null
          }
          <div
            className={
              classnames({
                'header-menu': true,
                'header-menu-open': menuBodyVisible,
              })
            }
          >
            <img
              className="header-menu-toggle"
              onClick={this.toggleMenu}
              alt=""
              src={type === 'primary' ? getLink('/img/menu_white.png') : getLink('/img/menu_gray.png')}
            />
            <ul>
              {siteConfig[language].pageMenu.map((item) => (
                  <li
                    key={item.link}
                    className={classnames({
                      'menu-item': true,
                      [`menu-item-${type}`]: true,
                      [`menu-item-${type}-active`]: currentKey === item.key,
                    })}
                  >
                    <a href={getLink(item.link)}  target="_blank" rel="noopener noreferrer">{item.text}</a>
                  </li>
              ))}
            </ul>
          </div>
        </div>
      </header>
    );
  }
}

Header.defaultProps = defaultProps;
export default Header;
