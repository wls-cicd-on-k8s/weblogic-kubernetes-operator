// Copyright 2019 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.clustermanager.helpers;

class ClusterException extends RuntimeException {
  ClusterException(String message, Exception e) {
    super(message, e);
  }

  ClusterException(String message) {
    super(message);
  }
}
