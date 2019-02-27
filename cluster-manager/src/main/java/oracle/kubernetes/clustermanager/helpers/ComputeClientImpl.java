// Copyright 2019 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.clustermanager.helpers;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.core.ComputeClient;
import com.oracle.bmc.core.ComputeWaiters;
import com.oracle.bmc.core.requests.LaunchInstanceRequest;
import com.oracle.bmc.core.requests.ListImagesRequest;
import com.oracle.bmc.core.requests.ListShapesRequest;
import com.oracle.bmc.core.responses.LaunchInstanceResponse;
import com.oracle.bmc.core.responses.ListImagesResponse;
import com.oracle.bmc.core.responses.ListShapesResponse;

public class ComputeClientImpl implements ComputeClientWrapper {
  private ComputeClient client;

  public ComputeClientImpl(AuthenticationDetailsProvider provider) {
    client = new ComputeClient(provider);
  }

  @Override
  public void setRegion(Region region) {
    client.setRegion(region);
  }

  @Override
  public ListImagesResponse listImages(ListImagesRequest request) {
    return client.listImages(request);
  }

  @Override
  public ListShapesResponse listShapes(ListShapesRequest request) {
    return client.listShapes(request);
  }

  @Override
  public LaunchInstanceResponse launchInstance(LaunchInstanceRequest request) {
    return client.launchInstance(request);
  }

  @Override
  public ComputeWaiters getWaiters() {
    return client.getWaiters();
  }
}
