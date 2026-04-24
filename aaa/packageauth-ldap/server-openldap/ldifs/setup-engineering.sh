#!/bin/sh

set -e

ldifdir="$(dirname $(readlink -f $0))"

# Add the Engineering organisation
ldapadd -x -D "cn=admin,dc=example,dc=com" -w admin -H ldap:// \
        -f $ldifdir/engineering.ldif

# Add the groups entry
ldapadd -x -D "cn=admin,dc=example,dc=com" -w admin -H ldap:// \
        -f $ldifdir/groups.ldif

# Add some engineers
ldapadd -x -D "cn=admin,dc=example,dc=com" -w admin -H ldap:// \
        -f $ldifdir/engineers.ldif

# Add the 'titan' group + one member
ldapadd -x -D "cn=admin,dc=example,dc=com" -w admin -H ldap:// \
        -f $ldifdir/titan.ldif

# Add the 'onyx' group + one member
ldapadd -x -D "cn=admin,dc=example,dc=com" -w admin -H ldap:// \
        -f $ldifdir/onyx.ldif
