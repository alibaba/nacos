import React from 'react'; 
import { Balloon, Button, Dialog, Form, Icon, Select, Upload } from '@alifd/next';
const FormItem = Form.Item; 

/*****************************此行为标记行, 请勿删和修改此行, 文件和组件依赖请写在此行上面, 主体代码请写在此行下面的class中*****************************/
class ImportDialog extends React.Component {
    constructor(props) {
        super(props);
        this.formItemLayout = {
            labelCol: {
                fixedSpan: 4
            },
            wrapperCol: {
                span: 20
            }
        };
        this.allPolicy = [{ value: 'abort', label: window.aliwareIntl.get('nacos.component.ImportDialog.To_terminate_the_import0') }, { value: 'skip', label: window.aliwareIntl.get('nacos.component.ImportDialog.skip1') }, {
            value: 'overwrite',
            label: window.aliwareIntl.get('nacos.component.ImportDialog.cover2')
        }];
        this.defaultPolicy = 'abort';
        this.state = {
            visible: false,
            serverId: '',
            tenant: '',
            policy: this.defaultPolicy,
            policyLabel: window.aliwareIntl.get('nacos.component.ImportDialog.To_terminate_the_import0')
        };
    }

    componentDidMount() {}

    openDialog(payload, callback) {
        this.callback = callback;
        this.setState({
            visible: true,
            serverId: payload.serverId,
            tenant: payload.tenant
        });
    }

    closeDialog() {
        this.setState({
            visible: false
        });
    }

    setPolicy(...value) {
        this.setState({
            policyLabel: value[1].label,
            policy: value[0]
        });
    }

    formatter(res) {
        if (res.code === 200) {
            return {
                code: '0',
                retData: res
            };
        } else {
            return {
                code: '1',
                error: {
                    message: res.message
                },
                retData: res
            };
        }
    }

    render() {
        let uploadLink = `/diamond-ops/batch/import/serverId/${this.state.serverId}/tenant/${this.state.tenant.id}?policy=${this.state.policy}`;

        if (window.globalConfig.isParentEdas()) {
            uploadLink = '/authgw/' + window.edasprefix + uploadLink;
        }
        const helpTip = <Balloon trigger={<span>Data ID <Icon type={"help"} size={'small'} style={{ color: '#1DC11D', marginRight: 5, verticalAlign: 'middle' }} /></span>} align={"t"} style={{ marginRight: 5 }} triggerType={"hover"}>	
        <a href={window._getLink && window._getLink("knowDataid")} target={"_blank"}>{window.aliwareIntl.get('nacos.component.ImportDialog.You_can_only_upload._zip_file_format0') /*只能上传.zip格式的文件*/}</a>	
    </Balloon>;
        const footer = <div><Upload language={window.pageLanguage || 'zh-cn'} listType={"text"} action={uploadLink} limit={1} accept={".zip"} onSuccess={(...args) => {
                this.callback(args[0].retData, this.state.policyLabel);
                this.closeDialog();
            }} onError={(...args) => {
                this.callback(args[0].response.retData, this.state.policyLabel);
                this.closeDialog();
            }} formatter={this.formatter.bind(this)} headers={{ poweredBy: "simpleMVC", projectName: "nacos" }}>
                <Button type={"primary"}>{window.aliwareIntl.get('nacos.component.ImportDialog.Upload_File3')}</Button>
            </Upload></div>;

        return <div>
            <Dialog visible={this.state.visible} footer={footer} footerAlign={"center"} language={window.pageLanguage || 'zh-cn'} style={{ width: 480 }} onCancel={this.closeDialog.bind(this)} onClose={this.closeDialog.bind(this)} title={window.aliwareIntl.get('nacos.component.ImportDialog.Import_configuration4') + this.state.serverId + "）"}>
                <Form>
                    <FormItem label={window.aliwareIntl.get('nacos.component.ImportDialog.target_space5')} {...this.formItemLayout}>
                        <p><span style={{ color: '#33cde5' }}>{this.state.tenant.name}</span>{" | " + this.state.tenant.id}
                        </p>
                    </FormItem>
                    <FormItem label={window.aliwareIntl.get('nacos.component.ImportDialog.the_same_configuration6')} {...this.formItemLayout}>
                        <Select size={"medium"} hasArrow defaultValue={this.defaultPolicy} dataSource={this.allPolicy} onChange={this.setPolicy.bind(this)} language={window.aliwareIntl.currentLanguageCode}>
                        </Select>
                    </FormItem>
                </Form>
                
                <div style={{ textAlign: "center" }}><Icon type={"warning"} style={{ color: '#ff8a00', marginRight: 5, verticalAlign: 'middle' }} />{window.aliwareIntl.get('nacos.component.ImportDialog.file_upload_directly_after_importing_the_configuration,_please_be_sure_to_exercise_caution7')}{helpTip}</div>
            </Dialog>
        </div>;
    }
}
/*****************************此行为标记行, 请勿删和修改此行, 主体代码请写在此行上面的class中, 组件导出语句及其他信息请写在此行下面*****************************/
export default ImportDialog;