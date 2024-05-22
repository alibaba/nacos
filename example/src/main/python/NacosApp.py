#!/usr/bin/python3
import pathlib

import uvicorn
from fastapi import FastAPI
from apscheduler.schedulers.asyncio import AsyncIOScheduler
from starlette.middleware.cors import CORSMiddleware
import json
import custom_api
from NacosLib import NacosClient

service_port = 12345
def load_json(file_path):
    with open(file_path, "r", encoding="utf-8") as fp:
        read_data = json.load(fp)
    return read_data


def load_config(content):
    _config = json.loads(content)
    return _config


def nacos_config_callback(args):
    content = args['raw_content']
    load_config(content)


def get_app() -> FastAPI:
    fastapi_kwargs = {
        "debug":False,
        "docs_url":"/docs",
        "openapi_prefix":"",
        "openapi_url":"/openapi.json",
        "title":"leotest",
        "version":"1.0.0",
    }
    app = FastAPI(**fastapi_kwargs)
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )
    nacos_config = load_json(pathlib.Path(__file__).parent.joinpath("config.json"))
    nacos_data_id = nacos_config["nacos_data_id"]
    SERVER_ADDRESSES = nacos_config["nacos_server_ip"]
    NAMESPACE = nacos_config["nacos_namespace"]
    groupName = nacos_config["nacos_groupName"]
    user = nacos_config["nacos_user"]
    password = nacos_config["nacos_password"]
    # todo 将另一个路由对象（通常定义在其他模块或文件中）合并到主应用（app）中。
    app.include_router(custom_api.router, tags=['test'])
    service_ip = get_host_ip()
    client = NacosClient(SERVER_ADDRESSES, NAMESPACE, user, password)
    client.add_conf_watcher(nacos_data_id, groupName, nacos_config_callback)

    # 启动时，强制同步一次配置
    data_stream = client.load_conf(nacos_data_id, groupName)
    json_config = load_config(data_stream)
    client.set_service(json_config["service_name"], json_config.get("service_ip", service_ip), service_port, groupName)
    client.register()

    # 注册配置变更监控回调
    @app.on_event('startup')
    def init_scheduler():
        scheduler = AsyncIOScheduler()
        scheduler.add_job(client.beat_callback, 'interval', seconds=json_config["beat_interval"])
        scheduler.start()

    @app.on_event('shutdown')
    def offline():
        client.unregister()

    return app


if __name__ == '__main__':
    uvicorn.run(app=get_app(), host='0.0.0.0', port=service_port,reload=True)
