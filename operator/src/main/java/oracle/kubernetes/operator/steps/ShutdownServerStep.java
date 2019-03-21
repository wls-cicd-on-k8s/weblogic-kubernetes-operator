// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Service;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.http.HttpClient;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.rest.Scan;
import oracle.kubernetes.operator.rest.ScanCache;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;

public class ShutdownServerStep extends Step {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private ShutdownServerStep(Step next) {
    super(next);
  }

  /**
   * Creates asynchronous {@link Step} to shutdown a server instance.
   *
   * @param next Next processing step
   * @return asynchronous step
   */
  public static Step createShutdownStep(Step next) {
    return new ShutdownServerStep(next);
  }

  @Override
  public NextAction apply(Packet packet) {
    DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);

    Domain dom = info.getDomain();
    if (dom != null) {
      V1ObjectMeta meta = dom.getMetadata();
      DomainSpec spec = dom.getSpec();
      String namespace = meta.getNamespace();

      String serverName = (String) packet.get(ProcessingConstants.SERVER_NAME);

      String secretName =
          spec.getWebLogicCredentialsSecret() == null
              ? null
              : spec.getWebLogicCredentialsSecret().getName();

      Step getClient =
          HttpClient.createAuthenticatedClientForServer(
              namespace,
              secretName,
              new ShutdownWithHttpClientStep(info.getServerService(serverName), getNext()));
      return doNext(getClient, packet);
    }
    return doNext(packet);
  }

  private static String getShtudownUrl() {
    return "/management/weblogic/latest/serverRuntime/shutdown";
  }

  private static String getShutdownPayload(
      int timeout, boolean ignoreSessions, boolean waitForAllSessions) {
    return "{ timeout: "
        + timeout
        + ", ignoreSessions: "
        + ignoreSessions
        + ", waitForAllSessions: "
        + waitForAllSessions
        + " }";
  }

  static final class ShutdownWithHttpClientStep extends Step {
    private final V1Service service;

    ShutdownWithHttpClientStep(V1Service service, Step next) {
      super(next);
      this.service = service;
    }

    @Override
    public NextAction apply(Packet packet) {
      try {
        HttpClient httpClient = (HttpClient) packet.get(HttpClient.KEY);
        DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);
        Scan scan = ScanCache.INSTANCE.lookupScan(info.getNamespace(), info.getDomainUID());
        WlsDomainConfig domainConfig = scan.getWlsDomainConfig();
        String serverName = (String) packet.get(ProcessingConstants.SERVER_NAME);
        WlsServerConfig serverConfig = domainConfig.getServerConfig(serverName);

        String serviceURL =
            HttpClient.getServiceURL(service, serverConfig.getAdminProtocolChannelName());
        if (serviceURL != null) {
          String jsonResult =
              httpClient
                  .executePostUrlOnServiceClusterIP(
                      getShtudownUrl(),
                      serviceURL,
                      getShutdownPayload(60, false, true), // FIXME: configure
                      true)
                  .getResponse();
        }
        return doNext(packet);
      } catch (Throwable t) {
        // do not retry for shutdown -- FIXME: rethink
        LOGGER.fine(
            MessageKeys.WLS_SHUTDOWN_OP_FAILED, packet.get(ProcessingConstants.SERVER_NAME), t);
        return doNext(packet);
      }
    }
  }
}
