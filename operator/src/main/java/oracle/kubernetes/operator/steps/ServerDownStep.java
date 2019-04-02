// Copyright 2017, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.util.function.Function;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.helpers.ServerKubernetesObjects;
import oracle.kubernetes.operator.helpers.ServiceHelper;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

public class ServerDownStep extends Step {
  private static Function<Step, Step> STEP_FACTORY = ShutdownServerStep::createShutdownStep;

  private final ServerKubernetesObjects sko;

  public ServerDownStep(ServerKubernetesObjects sko, Step next) {
    super(next);
    this.sko = sko;
  }

  @Override
  public NextAction apply(Packet packet) {
    return doNext(
        STEP_FACTORY.apply(
            PodHelper.deletePodStep(
                sko, ServiceHelper.deleteServicesStep(sko, new ServerDownFinalizeStep(getNext())))),
        packet);
  }
}
