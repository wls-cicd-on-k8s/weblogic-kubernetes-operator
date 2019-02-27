// Copyright 2019 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.clustermanager.helpers;

import com.oracle.bmc.Region;
import com.oracle.bmc.core.ComputeWaiters;
import com.oracle.bmc.core.requests.LaunchInstanceRequest;
import com.oracle.bmc.core.requests.ListImagesRequest;
import com.oracle.bmc.core.requests.ListShapesRequest;
import com.oracle.bmc.core.responses.LaunchInstanceResponse;
import com.oracle.bmc.core.responses.ListImagesResponse;
import com.oracle.bmc.core.responses.ListShapesResponse;

public interface ComputeClientWrapper {
  void setRegion(Region region);

  ListImagesResponse listImages(ListImagesRequest request);

  ListShapesResponse listShapes(ListShapesRequest request);

  LaunchInstanceResponse launchInstance(LaunchInstanceRequest request);

  ComputeWaiters getWaiters();
}
