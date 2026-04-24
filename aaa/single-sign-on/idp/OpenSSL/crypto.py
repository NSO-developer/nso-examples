from cryptography.x509 import (
    Certificate as X509,
    load_pem_x509_certificate,
)
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric.padding import PKCS1v15
from cryptography.hazmat.primitives.asymmetric.rsa import RSAPrivateKey as PKey
from cryptography.hazmat.primitives.serialization import (
    load_pem_private_key,
    Encoding,
)

FILETYPE_PEM = "PEM"

def dump_certificate(format, certificate):
    assert format == FILETYPE_PEM
    return certificate.public_bytes(encoding=Encoding.PEM)

def load_certificate(format, certificate):
    assert format == FILETYPE_PEM
    return load_pem_x509_certificate(certificate.encode('ascii'))

def load_privatekey(format, private_key):
    assert format == FILETYPE_PEM
    return load_pem_private_key(private_key.encode('ascii'), password=None)

def sign(key, data, hash):
    assert hash == "sha256"
    return key.sign(data, PKCS1v15(), hashes.SHA256())
