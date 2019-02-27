// Copyright 2019 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.clustermanager.helpers;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.core.VirtualNetwork;
import com.oracle.bmc.core.VirtualNetworkClient;
import com.oracle.bmc.core.model.Instance;
import com.oracle.bmc.core.requests.GetInstanceRequest;
import com.oracle.bmc.core.requests.LaunchInstanceRequest;
import com.oracle.bmc.core.requests.ListImagesRequest;
import com.oracle.bmc.core.requests.ListShapesRequest;
import com.oracle.bmc.core.responses.LaunchInstanceResponse;
import com.oracle.bmc.core.responses.ListImagesResponse;
import com.oracle.bmc.core.responses.ListShapesResponse;
import com.oracle.bmc.identity.Identity;
import com.oracle.bmc.identity.IdentityClient;

public class OCIServicesImpl implements OCIServices {
  private AuthenticationDetailsProvider provider;
  private Region region;

  public OCIServicesImpl(AuthenticationDetailsProvider provider, Region region) {
    this.provider = provider;
    this.region = region;
  }

  @Override
  @SuppressWarnings("SameParameterValue")
  public Instance getProvisionedInstance(
      GetInstanceRequest instanceRequest, Instance.LifecycleState desiredState) throws Exception {
    return getComputeClient()
        .getWaiters()
        .forInstance(instanceRequest, desiredState)
        .execute()
        .getInstance();
  }

  @Override
  public LaunchInstanceResponse launchInstance(LaunchInstanceRequest launchInstanceRequest) {
    return getComputeClient().launchInstance(launchInstanceRequest);
  }

  @Override
  public ListShapesResponse listShapes(ListShapesRequest listShapesRequest) {
    return getComputeClient().listShapes(listShapesRequest);
  }

  @Override
  public ListImagesResponse listImages(ListImagesRequest listImagesRequest) {
    return getComputeClient().listImages(listImagesRequest);
  }

  @Override
  public VirtualNetwork getVncClient() {
    return new VirtualNetworkClient(provider);
  }

  private ComputeClientWrapper getComputeClient() {
    ComputeClientWrapper computeClient = new ComputeClientImpl(provider);
    computeClient.setRegion(region);
    return computeClient;
  }

  @Override
  public Identity createIdentityClient() {
    return new IdentityClient(provider);
  }

  @Override
  public String getenv(String name) {
    return System.getenv(name);
  }
}
