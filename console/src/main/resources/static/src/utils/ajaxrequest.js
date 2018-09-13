import $ from 'jquery';

export default function ajaxrequest(options) {
	let promise = $.ajax({
		url: options.url,
		timeout : options.timeout, //超时时间设置，单位毫秒设置为1小时
		dataType: options.dataType,//返回的数据格式
		type : options.type
	});
	return promise.done(data => ({ data }));
}


