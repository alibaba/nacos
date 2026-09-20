#!/usr/bin/env python3
#
# Copyright 1999-2026 Alibaba Group Holding Ltd.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""External loopback proxy for SDK transport evidence; never records credentials/body/query."""
import argparse
import hashlib
import http.client
import json
import select
import socket
import socketserver
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs


class RpcTypeCounter:
    """Count allowlisted Metadata.type markers in the fixture's plaintext gRPC stream.

    nacos_grpc_service.proto encodes Metadata.type as field 3 (wire type 2).
    Keep a bounded suffix for markers split across TCP reads. Never retain or log
    headers, request bodies, arbitrary strings, or credentials as evidence.
    This observer is for uncompressed loopback traffic, not TLS or general decoding.
    """

    TYPES = (
        'QueryAgentCardRequest', 'ReleaseAgentCardRequest', 'AgentEndpointRequest',
        'BatchAgentEndpointRequest', 'AgentDiscoveryRpcRequest', 'AgentPublishRpcRequest',
        'AgentEndpointRegisterRpcRequest', 'AgentEndpointDeregisterRpcRequest',
        'AgentSearchRpcRequest', 'AgentSubscribeRpcRequest', 'AgentUnsubscribeRpcRequest',
    )

    def __init__(self):
        self.markers = {name: bytes((26, len(name))) + name.encode('ascii')
                        for name in self.TYPES}
        self.counts = {name: 0 for name in self.TYPES}
        self.tail = b''
        self.lookbehind = max(map(len, self.markers.values())) - 1

    def accept(self, data):
        combined = self.tail + data
        for name, marker in self.markers.items():
            start = max(0, len(self.tail) - len(marker) + 1)
            self.counts[name] += combined.count(marker, start)
        self.tail = combined[-self.lookbehind:]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--port', type=int, required=True)
    parser.add_argument('--upstream-port', type=int, required=True)
    parser.add_argument('--grpc', action='store_true', help='Forward port + 1000 as raw TCP')
    parser.add_argument('--reject-watch', action='store_true')
    parser.add_argument('--drop-first-agent-publish-response', action='store_true',
                        help='Drop the first successful publish response for each Agent')
    parser.add_argument('--capability-upstream-port', type=int)
    parser.add_argument('--log', type=Path, required=True)
    args = parser.parse_args()
    args.log.parent.mkdir(parents=True, exist_ok=True)
    lock = threading.Lock()
    dropped_publications = set()

    def record(**entry):
        entry['time'] = time.time()
        with lock, args.log.open('a') as output:
            output.write(json.dumps(entry) + '\n')

    class HttpProxy(BaseHTTPRequestHandler):
        protocol_version = 'HTTP/1.1'

        def forward(self):
            path = self.path.split('?', 1)[0]
            body = self.rfile.read(int(self.headers.get('Content-Length', '0')))
            if args.reject_watch and path.endswith('/v3/client/ai/agents/watch'):
                data = b'{"code":501,"message":"Watch unavailable in transport fixture","data":null}'
                self.send_response(501)
                self.send_header('Content-Type', 'application/json')
                self.send_header('Content-Length', str(len(data)))
                self.end_headers()
                self.wfile.write(data)
                record(transport='http', method=self.command, path=path, status=501)
                return
            port = args.upstream_port
            if args.capability_upstream_port and (path.endswith('/v3/client/ai/capabilities')
                                                or '/v3/auth/' in path or '/v1/auth/' in path):
                port = args.capability_upstream_port
            connection = http.client.HTTPConnection('127.0.0.1', port, timeout=45)
            try:
                headers = {k: v for k, v in self.headers.items()
                           if k.lower() not in ('host', 'connection', 'content-length')}
                connection.request(self.command, self.path, body=body, headers=headers)
                response = connection.getresponse()
                data = response.read()
                drop = False
                if (args.drop_first_agent_publish_response and self.command == 'POST'
                        and path.endswith('/v3/client/ai/agents') and response.status == 200):
                    agent = parse_qs(body.decode('utf-8')).get('agentName', [''])[0]
                    if agent and json.loads(data).get('code') == 0:
                        identity = hashlib.sha256(agent.encode('utf-8')).digest()
                        with lock:
                            drop = identity not in dropped_publications
                            dropped_publications.add(identity)
                if drop:
                    record(transport='http', method=self.command, path=path,
                           status=response.status, responseDropped=True)
                    self.close_connection = True
                    return
                self.send_response(response.status)
                for key, value in response.getheaders():
                    if key.lower() not in ('transfer-encoding', 'connection', 'content-length'):
                        self.send_header(key, value)
                self.send_header('Content-Length', str(len(data)))
                self.end_headers()
                self.wfile.write(data)
                record(transport='http', method=self.command, path=path, status=response.status)
            except (OSError, http.client.HTTPException):
                record(transport='http', method=self.command, path=path, connectionFailure=True)
                self.close_connection = True
            finally:
                connection.close()

        do_GET = forward
        do_POST = forward
        do_PUT = forward
        do_DELETE = forward
        do_HEAD = forward

        def log_message(self, *unused):
            pass

    class TcpProxy(socketserver.BaseRequestHandler):
        def handle(self):
            sent = 0
            received = 0
            rpc_types = RpcTypeCounter()
            with socket.create_connection(('127.0.0.1', args.upstream_port + 1000), timeout=10) as upstream:
                upstream.settimeout(None)
                record(transport='grpc-tcp', event='connected')
                try:
                    while True:
                        ready, _, _ = select.select([self.request, upstream], [], [], 30)
                        for source in ready:
                            data = source.recv(65536)
                            if not data:
                                return
                            if source is self.request:
                                upstream.sendall(data)
                                sent += len(data)
                                rpc_types.accept(data)
                            else:
                                self.request.sendall(data)
                                received += len(data)
                except OSError:
                    pass
                finally:
                    record(transport='grpc-tcp', event='closed', sent=sent, received=received,
                           requestTypes=rpc_types.counts)

    class TcpServer(socketserver.ThreadingTCPServer):
        allow_reuse_address = True
        daemon_threads = True

    if args.grpc:
        tcp = TcpServer(('127.0.0.1', args.port + 1000), TcpProxy)
        threading.Thread(target=tcp.serve_forever, daemon=True).start()
    record(event='ready', port=args.port, grpc=args.grpc, rejectWatch=args.reject_watch)
    ThreadingHTTPServer(('127.0.0.1', args.port), HttpProxy).serve_forever()


if __name__ == '__main__':
    main()
