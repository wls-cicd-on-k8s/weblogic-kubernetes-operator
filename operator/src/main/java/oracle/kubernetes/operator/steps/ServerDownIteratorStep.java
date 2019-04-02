// Copyright 2017, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.helpers.ServerKubernetesObjects;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

public class ServerDownIteratorStep extends Step {
  private final Collection<Map.Entry<String, ServerKubernetesObjects>> c;

  public ServerDownIteratorStep(
      Collection<Map.Entry<String, ServerKubernetesObjects>> serversToStop, Step next) {
    super(next);
    this.c = serversToStop;
  }

  public List<String> getServersToStop() {
    List<String> serverNames = new ArrayList<>();
    for (Map.Entry<String, ServerKubernetesObjects> entry : c) {
      serverNames.add(entry.getKey());
    }
    return Collections.unmodifiableList(serverNames);
  }

  @Override
  protected String getDetail() {
    return String.join(",", getServersToStop());
  }

  @Override
  public NextAction apply(Packet packet) {
    Collection<StepAndPacket> startDetails = new ArrayList<>();

    for (Map.Entry<String, ServerKubernetesObjects> entry : c) {
      Packet p = packet.clone();
      p.put(ProcessingConstants.SERVER_NAME, entry.getKey());

      startDetails.add(new StepAndPacket(new ServerDownStep(entry.getValue(), null), p));
    }

    if (startDetails.isEmpty()) {
      return doNext(packet);
    }
    return doForkJoin(getNext(), packet, startDetails);
  }
}
