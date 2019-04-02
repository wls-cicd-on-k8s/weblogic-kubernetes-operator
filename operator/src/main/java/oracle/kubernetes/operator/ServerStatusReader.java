// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import com.google.common.io.CharStreams;
import io.kubernetes.client.models.V1Pod;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.ServerKubernetesObjects;
import oracle.kubernetes.operator.steps.KubernetesExecStep;
import oracle.kubernetes.operator.steps.ReadHealthStep;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.ServerHealth;

/** Creates an asynchronous step to read the WebLogic server state from a particular pod. */
public class ServerStatusReader {
  private static Function<Step, Step> STEP_FACTORY = ReadHealthStep::createReadHealthStep;
  private static String[] COMMAND = new String[] {"/weblogic-operator/scripts/readState.sh"};

  private ServerStatusReader() {}

  static Step createDomainStatusReaderStep(
      DomainPresenceInfo info, long timeoutSeconds, Step next) {
    return new DomainStatusReaderStep(info, timeoutSeconds, next);
  }

  private static class DomainStatusReaderStep extends Step {
    private final DomainPresenceInfo info;
    private final long timeoutSeconds;

    DomainStatusReaderStep(DomainPresenceInfo info, long timeoutSeconds, Step next) {
      super(next);
      this.info = info;
      this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public NextAction apply(Packet packet) {
      ConcurrentMap<String, String> serverStateMap = new ConcurrentHashMap<>();
      packet.put(ProcessingConstants.SERVER_STATE_MAP, serverStateMap);

      ConcurrentMap<String, ServerHealth> serverHealthMap = new ConcurrentHashMap<>();
      packet.put(ProcessingConstants.SERVER_HEALTH_MAP, serverHealthMap);

      Collection<StepAndPacket> startDetails = new ArrayList<>();
      for (Map.Entry<String, ServerKubernetesObjects> entry : info.getServers().entrySet()) {
        String serverName = entry.getKey();
        ServerKubernetesObjects sko = entry.getValue();
        if (sko != null) {
          V1Pod pod = sko.getPod().get();
          if (pod != null) {
            Packet p = packet.clone();
            startDetails.add(
                new StepAndPacket(
                    createServerStatusReaderStep(sko, pod, serverName, timeoutSeconds), p));
          }
        }
      }

      if (startDetails.isEmpty()) {
        return doNext(packet);
      }
      return doForkJoin(getNext(), packet, startDetails);
    }
  }

  /**
   * Creates asynchronous step to read WebLogic server state from a particular pod.
   *
   * @param sko Server objects
   * @param pod The pod
   * @param serverName Server name
   * @param timeoutSeconds Timeout in seconds
   * @return Created step
   */
  private static Step createServerStatusReaderStep(
      ServerKubernetesObjects sko, V1Pod pod, String serverName, long timeoutSeconds) {
    return new ServerStatusReaderStep(
        sko, pod, serverName, timeoutSeconds, new ServerHealthStep(serverName, null));
  }

  private static class ServerStatusReaderStep extends KubernetesExecStep<String> {
    private final ServerKubernetesObjects sko;
    private final V1Pod pod;
    private final String serverName;

    ServerStatusReaderStep(
        ServerKubernetesObjects sko, V1Pod pod, String serverName, long timeoutSeconds, Step next) {
      super(pod, timeoutSeconds, next);
      this.sko = sko;
      this.pod = pod;
      this.serverName = serverName;
    }

    protected String[] getCommand() {
      return COMMAND;
    }

    protected String readStdout(Reader reader) throws IOException {
      return CharStreams.toString(reader);
    }

    protected void processStdoutResult(Packet packet, String state) {
      ConcurrentMap<String, String> serverStateMap =
          (ConcurrentMap<String, String>) packet.get(ProcessingConstants.SERVER_STATE_MAP);

      serverStateMap.put(
          serverName, state != null ? state.trim() : WebLogicConstants.UNKNOWN_STATE);
    }

    @Override
    public NextAction apply(Packet packet) {
      @SuppressWarnings("unchecked")
      ConcurrentMap<String, String> serverStateMap =
          (ConcurrentMap<String, String>) packet.get(ProcessingConstants.SERVER_STATE_MAP);

      if (PodWatcher.getReadyStatus(pod)) {
        sko.getLastKnownStatus().set(WebLogicConstants.RUNNING_STATE);
        serverStateMap.put(serverName, WebLogicConstants.RUNNING_STATE);
        return doNext(packet);
      } else {
        String lastKnownState = sko.getLastKnownStatus().get();
        if (lastKnownState != null) {
          serverStateMap.put(serverName, lastKnownState);
          return doNext(packet);
        }
      }

      return super.apply(packet);
    }
  }

  private static class ServerHealthStep extends Step {
    private final String serverName;

    ServerHealthStep(String serverName, Step next) {
      super(next);
      this.serverName = serverName;
    }

    @Override
    public NextAction apply(Packet packet) {
      @SuppressWarnings("unchecked")
      ConcurrentMap<String, String> serverStateMap =
          (ConcurrentMap<String, String>) packet.get(ProcessingConstants.SERVER_STATE_MAP);
      String state = serverStateMap.get(serverName);

      if (WebLogicConstants.STATES_SUPPORTING_REST.contains(state)) {
        packet.put(ProcessingConstants.SERVER_NAME, serverName);
        return doNext(STEP_FACTORY.apply(getNext()), packet);
      }

      return doNext(packet);
    }
  }
}
