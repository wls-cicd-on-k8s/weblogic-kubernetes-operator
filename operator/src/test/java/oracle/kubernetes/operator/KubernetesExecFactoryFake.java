// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import static com.meterware.simplestub.Stub.createStub;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.models.V1Pod;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import oracle.kubernetes.operator.utils.KubernetesExec;
import oracle.kubernetes.operator.utils.KubernetesExecFactory;

class KubernetesExecFactoryFake implements KubernetesExecFactory {
  private Map<String, String> responses = new HashMap<>();

  void defineResponse(String podName, String response) {
    responses.put(podName, response);
  }

  @Override
  public KubernetesExec create(ApiClient client, V1Pod pod, String containerName) {
    return new KubernetesExec() {
      @Override
      public Process exec(String... command) {
        return createStub(ProcessStub.class, getResponse(pod.getMetadata().getName()));
      }

      private String getResponse(String name) {
        return Optional.ofNullable(responses.get(name)).orElse("** unknown pod **");
      }
    };
  }

  abstract static class ProcessStub extends Process {
    private String response;

    public ProcessStub(String response) {
      this.response = response;
    }

    @Override
    public InputStream getInputStream() {
      return new ByteArrayInputStream(response.getBytes(Charset.forName("UTF-8")));
    }

    @Override
    public int exitValue() {
      return 0;
    }

    @Override
    public void destroy() {}
  }
}
