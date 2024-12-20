#!/usr/bin/env python3

import requests
import json
import time

base_url = 'http://localhost:8080/jsonrpc'
headers = {'Content-Type': 'application/json'}
session = requests.Session()
id = 0


def do_request(data):
    global id
    id = id + 1
    data['jsonrpc'] = '2.0'
    data['id'] = id
    result = session.post(
        base_url,
        data=json.dumps(data),
        headers=headers).json()
    return result


def main():
    do_request(
        {
            'method': 'login',
            'params': {
                'user': 'admin',
                'passwd': 'admin'
            }
        })

    trans_response = do_request(
        {
            'method': 'new_trans',
            'params': {
                'mode': 'read_write'
            }
        })

    th = trans_response['result']['th']

    do_request(
        {
            'method': 'delete',
            'params': {
                'th': th,
                'path': '/servers/server{server3}/ip'
            }
        })

    do_request(
        {
            'method': 'validate_commit',
            'params': {
                'th': th
            }
        })

    time.sleep(5)

    do_request(
        {
            'method': 'commit',
            'params': {
                'th': th
            }
        })
    do_request({'method': 'logout'})


if __name__ == "__main__":
    main()
