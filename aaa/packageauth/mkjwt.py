#!/usr/bin/env python3
import json
import jwt
import sys

payload = json.load(sys.stdin)
secret = 'secret-random-long-pre-shared-key'
print(jwt.encode(payload, secret, algorithm="HS256"))
