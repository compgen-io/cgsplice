#!/bin/sh
MYSELF=`which "$0" 2>/dev/null`
if [ "$?" -gt 0 ]; then
	MYSELF="./$0"
fi

if [ -e $(dirname $0)/.cgsplicerc ]; then
    . $(dirname $0)/.cgsplicerc
fi
if [ -e $HOME/.cgsplicerc ]; then
    . $HOME/.cgsplicerc
fi

if [ ! -t 0 ]; then 
    JAVA_OPTS="${JAVA_OPTS} -Dio.compgen.common.tty.fd0=F"
fi

if [ ! -t 1 ]; then 
    JAVA_OPTS="${JAVA_OPTS} -Dio.compgen.common.tty.fd1=F"
fi

if [ ! -t 2 ]; then 
    JAVA_OPTS="${JAVA_OPTS} -Dio.compgen.common.tty.fd2=F"
fi

JAVABIN=`which java`
if [ "${JAVA_HOME}" != "" ]; then
    JAVABIN="$JAVA_HOME/bin/java"
fi
exec "${JAVABIN}" ${JAVA_OPTS} -jar $0 "$@"
exit 1
