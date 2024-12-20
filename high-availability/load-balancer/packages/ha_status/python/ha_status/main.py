# -*- mode: python; python-indent: 4 -*-
from http.server import BaseHTTPRequestHandler, HTTPServer
import ncs
import threading
import os


class HAStatusHTTPHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        log = self.server.ncs_pylog
        log.debug('do_GET: ', self.path, ' for ', self.client_address)

        status = None
        body = None
        try:
            if self.path in ['/ha_status', '/ha_writable']:
                with ncs.maapi.single_read_trans('admin', 'system') as t:
                    mode = ncs.maagic.get_node(t, '/ncs-state/tfnm2:ha/mode')

                    if self.path == '/ha_writable':
                        if mode == 'primary':
                            body = 'true'
                        else:
                            body = 'false'
                            status = self.server.ncs_nonwrite_status
                    else:
                        body = str(mode).lower()
            else:
                status = (404, 'Not Found')
        except Exception as err:
            log.error(err)
            status = (503, 'Service Unavailable')

        try:
            if status is None:
                status = (200, 'OK')

            if body is None:
                body = '{} {}'.format(status[0], status[1])

            self.send_response(status[0], status[1])
            self.send_header('Content-Type', 'text/plain;charset=UTF-8')
            self.end_headers()
            self.wfile.write(bytes(body, 'utf-8'))
            log.debug('do_GET response sent')
        except Exception as err:
            log.error(err)


def start_server(logger):
    addr = os.environ.get('HASTATUS_ADDR', '')
    port = os.environ.get('HASTATUS_PORT', 8765)
    nwc = os.environ.get('HASTATUS_NWCODE', '409 Conflict').partition(' ')
    logger.info('Starting HTTP server at ', (addr, port))

    server = HTTPServer((addr, port), HAStatusHTTPHandler)
    server.ncs_pylog = logger
    server.ncs_nonwrite_status = (int(nwc[0]), nwc[2])
    threading.Thread(target=server.serve_forever, name='Webserver').start()
    logger.info('Webserver started')

    return server


# ---------------------------------------------
# COMPONENT THREAD THAT WILL BE STARTED BY NCS.
# ---------------------------------------------
class Main(ncs.application.Application):
    def setup(self):
        # Make sure this is set even if start_server() crashes
        # because teardown() still gets called
        self.http_server = None
        self.http_server = start_server(self.log)

    def teardown(self):
        if self.http_server is not None:
            self.log.info('Stopping HTTP server')
            self.http_server.shutdown()
            self.http_server.server_close()

        self.log.info('Webserver stopped')
