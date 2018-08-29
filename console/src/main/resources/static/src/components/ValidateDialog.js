import React, { Component } from 'react'; import { Affix, Animate, Badge, Balloon, Breadcrumb, Button, Calendar, Card, Cascader, CascaderSelect, Checkbox, Collapse, ConfigProvider, DatePicker, Dialog, Dropdown, Field, Form, Grid, Icon, Input, Loading, Menu, MenuButton, Message, Nav, NumberPicker, Overlay, Pagination, Paragragh, Progress, Radio, Range, Rating, Search, Select, Slider, SplitButton, Step, Switch, Tab, Table, Tag, TimePicker, Timeline, Transfer, Tree, TreeSelect, Upload, Validate } from '@alife/next'; const Accordion = Collapse; const TabPane = Tab.Item; const FormItem = Form.Item; const { RangePicker } = DatePicker; const { Item: StepItem } = Step; const { Row, Col } = Grid; const { Node: TreeNode } = Tree; const { Item } = Nav; const { Panel } = Collapse; const { Gateway } = Overlay; const { Group: CheckboxGroup } = Checkbox; const { Group: RadioGroup } = Radio; const { Item: TimelineItem } = Timeline; const { AutoComplete: Combobox } = Select;

/*****************************此行为标记行, 请勿删和修改此行, 文件和组件依赖请写在此行上面, 主体代码请写在此行下面的class中*****************************/
class ValidateDialog extends React.Component {
	constructor(props) {
		super(props);
		this.state = {
			dialCode: '86',
			phoneNumber: '',
			isValid: false,
			errorInfoCode: 0,
			visible: false,
			btnText: aliwareIntl.get("newDiamond.component.validateDialog.Click_to_get_verification_code"),
			defaultBtnText: aliwareIntl.get("newDiamond.component.validateDialog.Click_to_get_verification_code"),
			disabled: false,
			submitDisabled: false,
			verifyCode: '',
			codeType: '',
			verifyDetail: '',
			config: {},
			requestId: ''
		};
		this.telRef = null;
		this.disabled = false;
		this.submitDisabled = false;
		this.timer = null;
		this.countTime = 60;
		this.eventName = "validate";
	}
	componentDidMount() {
		window.narutoEvent && window.narutoEvent.listen(this.eventName, _obj => {
			console.log("_OBJ: ", _obj);
			this.setState({
				codeType: _obj.codeType,
				verifyDetail: _obj.verifyDetail,
				visible: true,
				config: _obj.config || {}
			});
		});
		// let country = this.getCountryNameByDialCode(this.state.dialCode);
		// if (this.telRef) {
		//     $(this.telRef).intlTelInput({
		//         initialCountry: country,
		//         preferredCountries: ['cn'],
		//         // formatOnDisplay: false
		//         // utilsScript: "http://midwayfe.oss-cn-shanghai.aliyuncs.com/egg-multipart-test/intlTellInputUtils.js"
		//     })

		//     // $(this.telRef).intlTelInput("handleUtils");
		//     $(this.telRef).intlTelInput("setNumber", `${this.state.phoneNumber}`);
		//     $(this.telRef).on("countrychange", (e, dialCode) => {
		//         console.log("countrychange: ", dialCode)
		//         this.setState({
		//             dialCode
		//         });
		//         setTimeout(() => {
		//             this.onChangePhoneNumber()
		//         })
		//     })
		// }
	}
	componentWillUnmount() {
		if (this.telRef) {
			$(this.telRef).unbind("countrychange");
			$(this.telRef).intlTelInput("destroy");
		}
		window.narutoEvent && window.narutoEvent.remove(this.eventName);
		clearInterval(this.timer);
	}
	getCountryNameByDialCode(dialCode) {
		let countryName = 'cn';
		let countryData = $.fn.intlTelInput.getCountryData();
		countryData.some(obj => {
			if (obj.dialCode == dialCode) {
				countryName = obj.iso2;
				return true;
			}
			return false;
		});

		return countryName;
	}
	onChangePhoneNumber() {
		let dataObj = this.getTelInputInfo();
		console.log(dataObj);
		this.props.changeValue && this.props.changeValue(dataObj);
	}
	onClose() {
		this.setState({
			visible: false
		});
	}
	onClickBtn() {
		this.disabled = true;
		this.setState({
			disabled: true
		});

		request({
			url: 'com.alibaba.newDiamond.service.sendVerifyCode', //以 com.alibaba. 开头最终会转换为真正的url地址
			data: {
				codeType: this.state.codeType
			},
			$data: {}, //替换{}中的内容
			success: res => {
				if (res && res.code === 200) {
					this.setState({
						requestId: res.data && res.data.requestId || ''
					});
					let count = this.countTime;
					let btnText = this.state.btnText;
					clearInterval(this.timer);
					this.timer = setInterval(() => {
						if (count === -1) {
							this.initBtn();
							return;
						}
						this.setState({
							btnText: count
						});
						count--;
					}, 1000);
				}
			}
		});
	}
	initBtn() {
		clearInterval(this.timer);
		this.disabled = false;
		this.setState({
			disabled: false,
			btnText: this.state.defaultBtnText
		});
	}
	onValidateVerifyCode() {
		if (!this.state.verifyCode) {
			Dialog.alert({
				content: aliwareIntl.get("newDiamond.component.validateDialog.fill_the_code"),
				language: aliwareIntl.currentLanguageCode
			});
			return;
		}
		let config = this.state.config;
		let data = Object.assign({}, config.data, {
			codeType: this.state.codeType,
			verifyCode: this.state.verifyCode,
			requestId: this.state.requestId
		});
		let preSucess = config.success;
		this.setState({
			submitDisabled: true
		});
		this.submitDisabled = true;
		request(Object.assign({}, config, {
			data: data,
			success: res => {
				this.setState({
					submitDisabled: false
				});
				this.submitDisabled = false;
				if (res && res.code === 200) {
					// window.location.reload && window.location.reload();
					this.onClose();
					this.initBtn();
					typeof preSucess === "function" && preSucess(res);
				} else {
					Dialog.alert({
						content: res.code === 400 ? aliwareIntl.get("newDiamond.component.validateDialog.verification_code_error") : res.message,
						language: aliwareIntl.currentLanguageCode
					});
				}
			}
		}));
	}
	onChangeVerifyCode(verifyCode) {
		this.setState({
			verifyCode
		});
	}
	render() {
		let footer = <div><Button type="primary" onClick={this.onValidateVerifyCode.bind(this)} disabled={this.submitDisabled}>{aliwareIntl.get("newDiamond.component.validateDialog.confirm")}</Button><Button type="normal" onClick={this.onClose.bind(this)}>{aliwareIntl.get("newDiamond.component.validateDialog.cancel")}</Button></div>;
		return <Dialog title={aliwareIntl.get("newDiamond.component.validateDialog.title")} style={{ color: '#73777A', width: 550 }} visible={this.state.visible} onOk={this.onValidateVerifyCode.bind(this)} onCancel={this.onClose.bind(this)} onClose={this.onClose.bind(this)} footer={footer} language={aliwareIntl.currentLanguageCode}>
			<div>
				<div style={{ marginBottom: 20 }}>
					<span style={{ display: 'inline-block', verticalAlign: 'middle', width: 100, textAlign: 'right', marginRight: 10 }}>{aliwareIntl.get("newDiamond.component.validateDialog.phoneNumber")}</span><span>{this.state.verifyDetail}</span>
				</div>
				<div>
					<span style={{ display: 'inline-block', verticalAlign: 'middle', width: 100, textAlign: 'right' }}>{aliwareIntl.get("newDiamond.component.validateDialog.Please_fill_out_the_verification_code")} </span>
					<Input style={{ margin: '0 10px', height: 32, width: 200, verticalAlign: 'top' }} onChange={this.onChangeVerifyCode.bind(this)} />
					<Button onClick={this.onClickBtn.bind(this)} disabled={this.disabled} style={{ minWidth: 150 }}>{this.state.btnText}</Button>
					<p style={{ margin: "12px", color: "#999" }}>{aliwareIntl.get("newDiamond.component.validateDialog.remark")}</p>
				</div>
			</div>
		</Dialog>;
	}
}
/*****************************此行为标记行, 请勿删和修改此行, 主体代码请写在此行上面的class中, 组件导出语句及其他信息请写在此行下面*****************************/
export default ValidateDialog;