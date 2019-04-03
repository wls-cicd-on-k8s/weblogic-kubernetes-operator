#!/bin/bash

# Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at
# http://oss.oracle.com/licenses/upl.

# Waits for WebLogic Server instance to shutdown or until a timeout is reached.
# This script assumes that the server instance is already stopping or stopped,
# such as because of a request to gracefully shutdown.

SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
source ${SCRIPTPATH}/traceUtils.sh
[ $? -ne 0 ] && echo "Error: missing file ${SCRIPTPATH}/traceUtils.sh" && exit 1

function check_for_shutdown() {
  state=${SCRIPTPATH}/readState.sh
  exit_status=$?
  if [ $exit_status -ne 0 ]; then
    trace "Node manager not running or server instance not found; assuming shutdown"
    return 0
  fi

  if [ "$state" = "SHUTDOWN" ]; then
    trace "Server is shutdown"
    return 0
  fi

  if [[ "$state" =~ ^FAILED ]]; then
    trace "Server in failed state"
    return 0
  fi

  trace "Server is currently in state $state"
  return 1
}

# Read current state in a loop
MAX_ATTEMPTS=${0:-30}
attempt_num=0
while ! check_for_shutdown ; do
    trace "Attempt $attempt_num: Server instance not yet shutdown"
    ((attempt_num++));if [[ attempt_num -eq MAX_ATTEMPTS ]];then break;fi
    sleep 1
done

exit 0
