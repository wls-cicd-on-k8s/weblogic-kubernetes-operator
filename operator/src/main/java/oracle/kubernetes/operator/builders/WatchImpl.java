// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.builders;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.util.Watch;
import java.io.IOException;
import java.util.Iterator;
import javax.annotation.Nonnull;
import oracle.kubernetes.operator.helpers.Pool;

/**
 * A pass-through implementation of the Kubernetes Watch class which implements a facade interface.
 */
public class WatchImpl<T> implements WatchI<T> {
  private final Pool<ApiClient> pool;
  private ApiClient client;
  private Watch<T> impl;

  WatchImpl(Pool<ApiClient> pool, ApiClient client, Watch<T> impl) {
    this.pool = pool;
    this.client = client;
    // trying https://github.com/kubernetes-client/java/pull/155
    client.getHttpClient().setReadTimeout(60, java.util.concurrent.TimeUnit.SECONDS);
    this.impl = impl;
  }

  @Override
  public void close() throws IOException {
    impl.close();
    if (client != null) {
      pool.recycle(client);
    }
  }

  @Override
  @Nonnull
  public Iterator<Watch.Response<T>> iterator() {
    return impl.iterator();
  }

  @Override
  public boolean hasNext() {
    try {
      return impl.hasNext();
    } catch (RuntimeException ex) {
      System.out.println(
          "For-BOB: hasNext() got RuntimeException " + ex + ", with cause " + ex.getCause());
      if (ex.getCause() != null) {
        ex.getCause().printStackTrace();
      }
      throw ex;
    }
  }

  @Override
  public Watch.Response<T> next() {
    try {
      return impl.next();
    } catch (Exception e) {
      client = null;
      throw e;
    }
  }
}
