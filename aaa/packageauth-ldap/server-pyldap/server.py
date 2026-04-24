import sys
import io

from twisted.application import service
from twisted.internet.endpoints import serverFromString
from twisted.internet.protocol import ServerFactory
from twisted.python.components import registerAdapter
from twisted.python import log
from ldaptor.inmemory import fromLDIFFile
from ldaptor.interfaces import IConnectedLDAPEntry
from ldaptor.protocols.ldap.ldapserver import LDAPServer

LDIF = b"""\
dn: dc=com
dc: com
objectClass: dcObject

dn: dc=example,dc=com
dc: example
objectClass: dcObject
objectClass: organization

dn: ou=groups,dc=example,dc=com
objectClass: organizationalUnit
ou: groups

dn: cn=titan,ou=groups,dc=example,dc=com
objectclass: groupOfNames
cn: titan
member: uid=sbrown,ou=engineering,dc=example,dc=com

dn: cn=onyx,ou=groups,dc=example,dc=com
objectclass: groupOfNames
cn: onyx
member: uid=sbrown,ou=engineering,dc=example,dc=com

dn: ou=engineering,dc=example,dc=com
objectClass: organizationalUnit
ou: engineering

dn: uid=sbrown,ou=engineering,dc=example,dc=com
cn: Sally Brown
uid: sbrown
memberOf: cn=titan,ou=groups,dc=example,dc=com
memberOf: cn=onyx,ou=groups,dc=example,dc=com
uidNumber: 1006
gidNumber: 1001
gidsExtra: 2000 3000
homeDirectory: /home/sally
objectClass: top
objectClass: person
userPassword: sbrown

"""


class Tree:
    def __init__(self):
        global LDIF
        self.f = io.BytesIO(LDIF)
        d = fromLDIFFile(self.f)
        d.addCallback(self.ldifRead)

    def ldifRead(self, result):
        self.f.close()
        self.db = result


class LDAPServerFactory(ServerFactory):
    protocol = LDAPServer

    def __init__(self, root):
        self.root = root

    def buildProtocol(self, addr):
        proto = self.protocol()
        proto.debug = self.debug
        proto.factory = self
        return proto


if __name__ == "__main__":
    from twisted.internet import reactor

    if len(sys.argv) == 2:
        port = int(sys.argv[1])
    else:
        port = 1389
    # First of all, to show logging info in stdout :
    log.startLogging(sys.stdout)
    # We initialize our tree
    tree = Tree()
    # When the LDAP Server protocol wants to manipulate the DIT, it invokes
    # `root = interfaces.IConnectedLDAPEntry(self.factory)` to get the root
    # of the DIT.  The factory that creates the protocol must therefore
    # be adapted to the IConnectedLDAPEntry interface.
    registerAdapter(lambda x: x.root, LDAPServerFactory, IConnectedLDAPEntry)
    factory = LDAPServerFactory(tree.db)
    factory.debug = True
    application = service.Application("ldaptor-server")
    myService = service.IServiceCollection(application)
    serverEndpointStr = f"tcp:{port}"
    e = serverFromString(reactor, serverEndpointStr)
    d = e.listen(factory)
    reactor.run()
