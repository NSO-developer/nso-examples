#!/usr/bin/env python3
import logging

from flask import Flask, abort, redirect, request, session, url_for
from flask.views import MethodView

from flask_saml2.idp import IdentityProvider, SPHandler

# From flask-saml2 tests.idp.base and tests.sp.base
from pathlib import Path
import attr

from flask_saml2.utils import certificate_from_file, private_key_from_file

KEY_DIR = Path(__file__).parent / 'keys'

SP_CERTIFICATE_FILE = KEY_DIR / 'sp.crt'
SP_CERTIFICATE = certificate_from_file(SP_CERTIFICATE_FILE)

CERTIFICATE_FILE = KEY_DIR / 'idp.crt'
PRIVATE_KEY_FILE = KEY_DIR / 'idp.key'
CERTIFICATE = certificate_from_file(CERTIFICATE_FILE)
PRIVATE_KEY = private_key_from_file(PRIVATE_KEY_FILE)


@attr.s
class User:
    username = attr.ib()
    email = attr.ib()


logger = logging.getLogger(__name__)


class ExampleIdentityProvider(IdentityProvider):
    def login_required(self):
        if not self.is_user_logged_in():
            next = url_for('login', next=request.url)

            abort(redirect(next))

    def is_user_logged_in(self):
        return 'user' in session and session['user'] in users

    def logout(self):
        del session['user']

    def get_current_user(self):
        return users[session['user']]


# THIS IS NSO USERS
users = {user.username: user for user in [
    User('admin', 'admin'),
    User('oper', 'oper'),
    User('baduser', 'baduser'),
]}


idp = ExampleIdentityProvider()


class Login(MethodView):
    def get(self):
        options = ''.join(
            f'<option value="{usr.username}">{usr.email}</option>'
            for usr in users.values())
        select = (f'<div><label>Select a user: '
                  f'<select name="user">{options}</select></label></div>')

        next_url = request.args.get('next')
        next = f'<input type="hidden" name="next" value="{next_url}">'

        submit = '<div><input type="submit" value="Login"></div>'

        form = f'<form action="." method="post">{select}{next}{submit}</form>'
        header = '<title>Login</title><p>Please log in to continue.</p>'

        return header + form

    def post(self):
        user = request.form['user']
        next = request.form['next']

        session['user'] = user
        logging.info("Logged user", user, "in")
        logging.info("Redirecting to", next)

        return redirect(next)


class AttributeSPHandler(SPHandler):
    """
    Add attributes required by packageauth (groups, uid, gid, gids, homedir)
    to the SAMLResponse.
    """
    # different auth data (groups, uid, gids, homedir) by user
    def build_assertion(self, request, *args, **kwargs):
        current_user = self.get_subject()
        if current_user == "admin":
            return {
                **super().build_assertion(request, *args, **kwargs),
                'ATTRIBUTES': {
                    'groups': 'admin wheel',
                    'uid': '1000',
                    'gid': '1000',
                    'gids': '100',
                    'homedir': '/home/admin'
                },
            }
        elif current_user == "oper":
            return {
                **super().build_assertion(request, *args, **kwargs),
                'ATTRIBUTES': {
                    'uid': '2000',
                    'gid': '2000',
                    'homedir': '/home/oper'
                },
            }


app = Flask(__name__)
app.debug = True
app.secret_key = 'not a secret'
app.config['SERVER_NAME'] = 'localhost:8000'
app.config['SAML2_IDP'] = {
    'autosubmit': True,
    'certificate': CERTIFICATE,
    'private_key': PRIVATE_KEY,
}
app.config['SAML2_SERVICE_PROVIDERS'] = [
    {
        'CLASS': 'idp.AttributeSPHandler',
        'OPTIONS': {
            'display_name': 'NSO Service Provider',
            'entity_id': 'http://localhost:8080/sso/saml/metadata/',
            'acs_url': 'http://localhost:8080/sso/saml/acs/',
            'certificate': SP_CERTIFICATE,
        },
    }
]

app.add_url_rule('/login/', view_func=Login.as_view('login'))
app.register_blueprint(idp.create_blueprint(), url_prefix='/saml/')


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8000)
