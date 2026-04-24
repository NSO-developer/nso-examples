FROM debian:testing-slim

ENV DEBIAN_FRONTEND noninteractive

# Fix timezone so install always works
ENV TZ UTC
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

ADD ldifs /root/ldifs

# use debconf to do basic config of slapd deb
RUN apt-get update && \
    apt-get -y install slapd debconf-utils && \
    echo "slapd slapd/domain string example.com" | debconf-set-selections && \
    echo "slapd shared/organization string admin" | debconf-set-selections && \
    dpkg-reconfigure -phigh slapd && \
    mkdir -p /var/run/slapd

# setup directory
RUN slapd -h "ldap:/// ldapi:///" && \
    /root/ldifs/configure.sh && \
    /root/ldifs/setup-engineering.sh
