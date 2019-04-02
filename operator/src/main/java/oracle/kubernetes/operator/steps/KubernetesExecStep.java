// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import static oracle.kubernetes.operator.KubernetesConstants.CONTAINER_NAME;

import com.google.common.base.Charsets;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1Pod;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.concurrent.TimeUnit;
import oracle.kubernetes.operator.helpers.ClientPool;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.utils.KubernetesExec;
import oracle.kubernetes.operator.utils.KubernetesExecFactory;
import oracle.kubernetes.operator.utils.KubernetesExecFactoryImpl;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

public abstract class KubernetesExecStep<S> extends Step {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private static KubernetesExecFactory EXEC_FACTORY = new KubernetesExecFactoryImpl();

  protected final V1Pod pod;
  protected final long timeoutSeconds;

  protected KubernetesExecStep(V1Pod pod, long timeoutSeconds, Step next) {
    super(next);
    this.pod = pod;
    this.timeoutSeconds = timeoutSeconds;
  }

  protected abstract String[] getCommand();

  protected abstract S readStdout(Reader reader) throws IOException;

  protected abstract void processStdoutResult(Packet packet, S s);

  @Override
  public NextAction apply(Packet packet) {
    // Even though we don't need input data for this call, the API server is
    // returning 400 Bad Request any time we set these to false.  There is likely some bug in the
    // client
    final boolean stdin = true;
    final boolean tty = true;

    return doSuspend(
        fiber -> {
          Process proc = null;
          S result = null;
          ClientPool helper = ClientPool.getInstance();
          ApiClient client = helper.take();
          try {
            KubernetesExec kubernetesExec = EXEC_FACTORY.create(client, pod, CONTAINER_NAME);
            kubernetesExec.setStdin(stdin);
            kubernetesExec.setTty(tty);
            proc = kubernetesExec.exec(getCommand());

            InputStream in = proc.getInputStream();
            if (proc.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
              try (final Reader reader = new InputStreamReader(in, Charsets.UTF_8)) {
                result = readStdout(reader);
              }
            }
          } catch (InterruptedException ignore) {
            Thread.currentThread().interrupt();
          } catch (IOException | ApiException e) {
            LOGGER.warning(MessageKeys.EXCEPTION, e);
          } finally {
            helper.recycle(client);
            if (proc != null) {
              proc.destroy();
            }
          }

          processStdoutResult(packet, result);
          fiber.resume(packet);
        });
  }
}
