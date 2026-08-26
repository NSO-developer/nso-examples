# -*- mode: python; python-indent: 4 -*-
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import os
from pathlib import Path
import re
import threading
from urllib.parse import urlsplit

import ncs


class ZtpHTTPRequestHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        path = urlsplit(self.path).path
        resources = {
            '/ztp/ztp-provision.py': (
                'ztp-provision.py', 'text/x-python; charset=UTF-8'),
            '/ztp/ztp-inventory.json': (
                'ztp-inventory.json', 'application/json; charset=UTF-8'),
            '/ztp/ztp-pre.cli': (
                'ztp-pre.cli', 'text/plain; charset=UTF-8'),
        }
        if path in resources:
            filename, content_type = resources[path]
        else:
            match = re.fullmatch(
                r'/ztp/config/([A-Za-z0-9-]+-ztp\.cli)', path)
            if match is None:
                self.send_error(404, 'Not Found')
                return
            filename = match.group(1)
            content_type = 'text/plain; charset=UTF-8'

        try:
            body = (self.server.ztp_root / filename).read_bytes()
        except OSError:
            self.send_error(404, 'Not Found')
            return

        self.send_response(200)
        self.send_header('Content-Type', content_type)
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, message, *args):
        self.server.ncs_log.debug('ZTP HTTP: ', message % args)


def default_http_root():
    return Path(__file__).resolve().parents[4] / 'device-config'


class Main(ncs.application.Application):
    def setup(self):
        self.http_server = None
        self.http_thread = None

        root = Path(os.environ.get(
            'ZTP_HTTP_ROOT', default_http_root())).resolve()
        if not root.is_dir():
            raise RuntimeError(f'ZTP HTTP root does not exist: {root}')

        address = os.environ.get('ZTP_HTTP_ADDR', '127.0.0.1')
        port = int(os.environ.get('ZTP_HTTP_PORT', '30604'))
        self.http_server = ThreadingHTTPServer(
            (address, port), ZtpHTTPRequestHandler)
        self.http_server.ztp_root = root
        self.http_server.ncs_log = self.log
        self.http_thread = threading.Thread(
            target=self.http_server.serve_forever,
            kwargs={'poll_interval': 0.1},
            name='ZTP-HTTP-server', daemon=True)
        self.http_thread.start()
        self.log.info('ZTP HTTP server started at ', (address, port),
                      ' serving ', root)

    def teardown(self):
        if self.http_server is not None:
            self.log.info('Stopping ZTP HTTP server')
            self.http_server.shutdown()
            self.http_server.server_close()
        if self.http_thread is not None:
            self.http_thread.join()
        self.log.info('ZTP HTTP server stopped')
