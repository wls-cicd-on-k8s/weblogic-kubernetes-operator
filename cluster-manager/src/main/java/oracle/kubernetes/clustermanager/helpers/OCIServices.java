// Copyright 2019 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.clustermanager.helpers;

import com.oracle.bmc.core.VirtualNetwork;
import com.oracle.bmc.core.model.Instance;
import com.oracle.bmc.core.requests.GetInstanceRequest;
import com.oracle.bmc.core.requests.LaunchInstanceRequest;
import com.oracle.bmc.core.requests.ListImagesRequest;
import com.oracle.bmc.core.requests.ListShapesRequest;
import com.oracle.bmc.core.responses.LaunchInstanceResponse;
import com.oracle.bmc.core.responses.ListImagesResponse;
import com.oracle.bmc.core.responses.ListShapesResponse;
import com.oracle.bmc.identity.Identity;

public interface OCIServices {
  Instance getProvisionedInstance(
      GetInstanceRequest instanceRequest, Instance.LifecycleState desiredState) throws Exception;

  LaunchInstanceResponse launchInstance(LaunchInstanceRequest launchInstanceRequest);

  ListShapesResponse listShapes(ListShapesRequest listShapesRequest);

  ListImagesResponse listImages(ListImagesRequest listImagesRequest);

  VirtualNetwork getVncClient();

  Identity createIdentityClient();

  String getenv(String name);
}
