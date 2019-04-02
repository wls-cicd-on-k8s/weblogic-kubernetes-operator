// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import io.kubernetes.client.models.V1Pod;
import java.io.Reader;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

public class WaitForShutdownStep extends KubernetesExecStep<Void> {
  private static String COMMAND = "/weblogic-operator/scripts/waitForShutdown.sh";

  protected WaitForShutdownStep(V1Pod pod, long timeoutSeconds, Step next) {
    super(pod, timeoutSeconds, next);
  }

  @Override
  protected String[] getCommand() {
    return new String[] {COMMAND, String.valueOf(timeoutSeconds)};
  }

  @Override
  protected Void readStdout(Reader reader) {
    return null;
  }

  @Override
  protected void processStdoutResult(Packet packet, Void aVoid) {
    // no-op
  }
}
