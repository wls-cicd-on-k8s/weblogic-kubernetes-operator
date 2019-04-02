// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

public class ServerDownFinalizeStep extends Step {

  public ServerDownFinalizeStep(Step next) {
    super(next);
  }

  @Override
  public NextAction apply(Packet packet) {
    String serverName = (String) packet.get(ProcessingConstants.SERVER_NAME);

    DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);
    info.getServers().remove(serverName);
    return doNext(getNext(), packet);
  }
}
