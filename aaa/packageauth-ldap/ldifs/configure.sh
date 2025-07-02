#!/bin/sh

ldifdir="$(dirname $(readlink -f $0))"

# root password and root dn
ldapmodify -H ldapi:// -w admin -Y EXTERNAL -f $ldifdir/root.ldif

# memberOf attribute
ldapadd -H ldapi:// -w admin -Y EXTERNAL -f $ldifdir/memberof_config.ldif

# refint module
ldapmodify -H ldapi:// -w admin -Y EXTERNAL -f $ldifdir/refint1.ldif
ldapadd -H ldapi:// -w admin -Y EXTERNAL -f $ldifdir/refint2.ldif

# add gidsExtra attribute
ldapadd -H ldapi:// -w admin -Y EXTERNAL -f $ldifdir/gidsextra_schema.ldif
