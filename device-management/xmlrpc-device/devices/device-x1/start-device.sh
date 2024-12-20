#!/bin/sh

JAR_DIR=${NCS_DIR}/java/jar
LOG4JAPI=${JAR_DIR}/log4j-api.jar
LOG4JCORE=${JAR_DIR}/log4j-core.jar
JUNIXSOCKC=${JAR_DIR}/junixsocket-common.jar
JUNIXSOCKNC=${JAR_DIR}/junixsocket-native-common.jar
DEVICE_JAR=../device-common/build/jar/xmlrpc-device.jar
DEVICE_MODEL=../device-common/build/jar/device-model.jar
XMLRPC_SERVER=../../packages/common/shared-jar/xmlrpc-server-3.1.3.jar
XMLRPC_COMMON=../../packages/common/shared-jar/xmlrpc-common-3.1.3.jar
XMLRPC_CLIENT=../../packages/common/shared-jar/xmlrpc-client-3.1.3.jar
COMMON_LOGGING=../../packages/common/shared-jar/commons-logging-1.1.jar
WS_COMMONS_UTIL=../../packages/common/shared-jar/ws-commons-util-1.0.2.jar

CLASSPATH=.:${LOG4JAPI}:${LOG4JCORE}:${JUNIXSOCKC}:${JUNIXSOCKNC}:build/classes:${XMLRPC_SERVER}:${XMLRPC_COMMON}:${XMLRPC_CLIENT}:${COMMON_LOGGING}:${WS_COMMONS_UTIL}:${DEVICE_JAR}:${DEVICE_MODEL}

java -classpath ${CLASSPATH} com.example.xmlrpcdevice.servers.XMLRpcServer1
