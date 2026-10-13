#!/usr/bin/env python3
"""Exercise an isolated development database with real MySQL/Redis and HTTP.

Run from this repository after starting the app, e.g.:
  python3 scripts/smoke_live.py http://127.0.0.1:8081
Reads .env.local (or overriding environment variables). It reads CAPTCHA from
Redis for this test only, creates uniquely named records, and deletes them in
finally. Never run against production. No passwords/tokens are printed.
"""
import json
import os
from pathlib import Path
import socket
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
CONFIG = dict(line.split('=', 1) for line in (ROOT / '.env.local').read_text().splitlines()
              if line.strip() and not line.startswith('#') and '=' in line)
CONFIG.update(os.environ)
BASE = sys.argv[1] if len(sys.argv) > 1 else 'http://127.0.0.1:8081'


def redis(*args):
    with socket.create_connection((CONFIG['REDIS_HOST'], int(CONFIG.get('REDIS_PORT', 6379))), timeout=10) as conn:
        stream = conn.makefile('rb')
        def command(*parts):
            values = [str(part).encode() for part in parts]
            conn.sendall(b'*' + str(len(values)).encode() + b'\r\n' + b''.join(
                b'$' + str(len(value)).encode() + b'\r\n' + value + b'\r\n' for value in values))
            response = stream.readline().rstrip(b'\r\n')
            if response.startswith(b'-'):
                raise RuntimeError('Redis command failed')
            if response.startswith(b'$'):
                length = int(response[1:])
                if length < 0:
                    return None
                value = stream.read(length)
                stream.read(2)
                return value.decode()
            return response[1:].decode()
        if CONFIG.get('REDIS_PASSWORD'):
            command('AUTH', CONFIG['REDIS_PASSWORD'])
        command('SELECT', CONFIG.get('REDIS_DATABASE', 0))
        return command(*args)


def api(path, body=None, token=None, expected=200, form=False):
    headers = {}
    if token:
        headers['Authorization'] = token
    data = None
    if body is not None:
        data = urllib.parse.urlencode(body).encode() if form else json.dumps(body).encode()
        headers['Content-Type'] = 'application/x-www-form-urlencoded' if form else 'application/json'
    request = urllib.request.Request(BASE + path, data=data, headers=headers)
    try:
        response = urllib.request.urlopen(request, timeout=15)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        payload = json.loads(response.read())
        assert response.status == expected, f'{path}: expected {expected}, got {response.status}: {payload.get("msg")}'
        return payload.get('data')


def login(username, password):
    captcha = api('/captcha')
    key = 'liushao:admin:captcha:' + captcha['token']
    ttl = int(redis('TTL', key))
    assert 0 < ttl <= 120, 'CAPTCHA must expire within two minutes'
    code = redis('GET', key)
    token = api('/login', dict(username=username, password=password, token=captcha['token'], code=code), form=True)['token']
    assert redis('GET', key) is None, 'CAPTCHA must be consumed'
    return token


def main():
    admin = login(CONFIG.get('ADMIN_USERNAME', 'admin'), CONFIG['ADMIN_PASSWORD'])
    suffix = uuid.uuid4().hex[:10]
    username, password = 'smoke_' + suffix, uuid.uuid4().hex
    user = role = menu = None
    try:
        api('/sys/user/list', expected=401)
        assert 'password' not in api('/sys/userInfo', token=admin)
        nav = api('/sys/menu/nav', token=admin)
        assert 'ROLE_ADMIN' in nav['authoritys'] and len(nav['nav'][0]['children']) >= 3
        user = api('/sys/user/save', dict(username=username, password=password, statu=1), admin)
        role = api('/sys/role/save', dict(name=username, code=username, statu=1), admin)
        menu = api('/sys/menu/save', dict(parentId=0, name=username, perms='smoke:' + suffix, type=0, orderNum=1, statu=1), admin)
        api('/sys/role/perm/' + str(role), [2], admin)
        api('/sys/user/role/' + str(user), [role], admin)
        member = login(username, password)
        api('/sys/user/list', token=member)
        api('/sys/user/delete', [user], member, expected=403)
        api('/sys/role/perm/' + str(role), [], admin)
        api('/sys/user/list', token=member, expected=403)
        api('/sys/user/repass', dict(id=user, password=uuid.uuid4().hex), admin)
        api('/sys/userInfo', token=member, expected=401)
        print('PASS: real CAPTCHA TTL/consumption, login, CRUD, role grants, immediate permission removal, 401/403, password revocation')
    finally:
        try:
            if user is not None:
                api('/sys/user/delete', [user], admin)
            if role is not None:
                api('/sys/role/delete', [role], admin)
            if menu is not None:
                api('/sys/menu/delete/' + str(menu), {}, admin)
        finally:
            api('/logout', {}, admin)
            api('/sys/userInfo', token=admin, expected=401)
    print('PASS: test records removed; logout invalidates token')


if __name__ == '__main__':
    main()
