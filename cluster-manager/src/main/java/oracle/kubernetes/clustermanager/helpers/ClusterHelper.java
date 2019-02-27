// Copyright 2019, Oracle Corporation and/or its affiliates. All rights reserved.

package oracle.kubernetes.clustermanager.helpers;

import com.google.common.base.Strings;
import com.oracle.bmc.core.model.CreateVnicDetails;
import com.oracle.bmc.core.model.Image;
import com.oracle.bmc.core.model.Instance;
import com.oracle.bmc.core.model.InstanceSourceViaImageDetails;
import com.oracle.bmc.core.model.LaunchInstanceDetails;
import com.oracle.bmc.core.model.Shape;
import com.oracle.bmc.core.model.Subnet;
import com.oracle.bmc.core.requests.GetInstanceRequest;
import com.oracle.bmc.core.requests.LaunchInstanceRequest;
import com.oracle.bmc.core.requests.ListImagesRequest;
import com.oracle.bmc.core.requests.ListShapesRequest;
import com.oracle.bmc.core.requests.ListSubnetsRequest;
import com.oracle.bmc.core.responses.LaunchInstanceResponse;
import com.oracle.bmc.identity.Identity;
import com.oracle.bmc.identity.model.AvailabilityDomain;
import com.oracle.bmc.identity.requests.ListAvailabilityDomainsRequest;
import java.io.File;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import oracle.kubernetes.clustermanager.types.ClusterSpec;
import oracle.kubernetes.clustermanager.types.Lease;
import org.apache.commons.io.FileUtils;

/** Create and destroy Kubernetes clusters. */
public class ClusterHelper {
  private OCIServices ociServices;

  static final String IMAGE_NAME = "Oracle-Linux-7.6-2019.01.17-0";
  private File script;

  ClusterHelper(OCIServices ociServices) {
    this.ociServices = ociServices;
  }

  /** Create a new cluster. */
  void createCluster(Lease lease, ClusterSpec spec) {

    String instanceName = "ephemeral-kubernetes-" + lease.getId();
    String compartmentId = ociServices.getenv("CLUSTER_MANAGER_COMPARTMENT_OCID");
    String sshPublicKey = ociServices.getenv("CLUSTER_MANAGER_SSH_PUBLIC_KEY");

    Instance instance =
        createInstance(
            getAvailabilityDomain(lease, compartmentId),
            getImage(IMAGE_NAME, compartmentId),
            getShape(lease, compartmentId),
            compartmentId,
            getSubnet(createSubnetName(lease.getRegion()), compartmentId),
            instanceName,
            sshPublicKey,
            null); // kmsKeyId

    System.out.println("Instance is being created...");

    Instance theInstance = waitForInstanceProvisioningToComplete(instance.getId());
    if (theInstance == null) {
      throw new ClusterException("instance was null - this should not happen");
    }

    System.out.println("Instance is provisioned.");
  }

  static String createSubnetName(String region) {
    return "Public Subnet VPGL:" + region;
  }

  /** Destroy a cluster. */
  public static void destroyCluster(Lease lease) {}

  void setScript(File script) {
    this.script = script;
  }

  @SuppressWarnings("SameParameterValue")
  private Image getImage(String imageName, String compartmentId) {
    return getImages(compartmentId)
        .stream()
        .filter(i -> i.getDisplayName().equals(imageName))
        .findFirst()
        .orElseThrow(() -> new ClusterException("could not find image " + imageName));
  }

  private List<Image> getImages(String compartmentId) {
    try {
      return ociServices
          .listImages(ListImagesRequest.builder().compartmentId(compartmentId).build())
          .getItems();
    } catch (Exception e) {
      throw new ClusterException("could not get images", e);
    }
  }

  private AvailabilityDomain getAvailabilityDomain(Lease lease, String compartmentId) {
    return getAvailabilityDomains(compartmentId)
        .stream()
        .filter(a -> a.getName().equals(lease.getRegion()))
        .findFirst()
        .orElseThrow(() -> new ClusterException("did not find AD " + lease.getRegion()));
  }

  private List<AvailabilityDomain> getAvailabilityDomains(String compartmentId) {
    try (Identity identityClient = ociServices.createIdentityClient()) {
      return identityClient
          .listAvailabilityDomains(
              ListAvailabilityDomainsRequest.builder().compartmentId(compartmentId).build())
          .getItems();
    } catch (Exception e) {
      throw new ClusterException("could not get availability domains", e);
    }
  }

  private Shape getShape(Lease lease, String compartmentId) {
    return getShapes(compartmentId)
        .stream()
        .filter(s -> s.getShape().equals(lease.getShape()))
        .findFirst()
        .orElseThrow(() -> new ClusterException("could not find shape " + lease.getShape()));
  }

  private List<Shape> getShapes(String compartmentId) {
    try {
      return ociServices
          .listShapes(ListShapesRequest.builder().compartmentId(compartmentId).build())
          .getItems();
    } catch (Exception e) {
      throw new ClusterException("could not get shapes", e);
    }
  }

  private Subnet getSubnet(String subnetName, String compartmentId) {
    return getSubnets(compartmentId)
        .stream()
        .filter(s -> s.getDisplayName().equals(subnetName))
        .findFirst()
        .orElseThrow(() -> new ClusterException("did not find subnet " + subnetName));
  }

  private List<Subnet> getSubnets(String compartmentId) {
    try {
      return ociServices
          .getVncClient()
          .listSubnets(ListSubnetsRequest.builder().compartmentId(compartmentId).build())
          .getItems();
    } catch (Exception e) {
      throw new ClusterException("could not get subnets", e);
    }
  }

  private Instance createInstance(
      AvailabilityDomain availabilityDomain,
      Image image,
      Shape shape,
      String compartmentId,
      Subnet subnet,
      String instanceName,
      String sshPublicKey,
      String kmsKeyId) {

    LaunchInstanceResponse response =
        ociServices.launchInstance(
            LaunchInstanceRequest.builder()
                .launchInstanceDetails(
                    createLaunchInstanceDetails(
                        compartmentId,
                        availabilityDomain,
                        instanceName,
                        image,
                        shape,
                        subnet,
                        kmsKeyId,
                        createMetadata(sshPublicKey)))
                .build());

    return response.getInstance();
  }

  private Map<String, String> createMetadata(String sshPublicKey) {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("ssh_authorized_keys", sshPublicKey);
    metadata.put("user_data", createEncodedScript());
    return metadata;
  }

  private String createEncodedScript() {
    try {
      // TODO pick the right file
      byte[] fileContent = FileUtils.readFileToByteArray(script);
      return Base64.getEncoder().encodeToString(fileContent);
    } catch (Exception e) {
      throw new ClusterException("could not base64 encode the cloud-init script", e);
    }
  }

  private LaunchInstanceDetails createLaunchInstanceDetails(
      String compartmentId,
      AvailabilityDomain availabilityDomain,
      String instanceName,
      Image image,
      Shape shape,
      Subnet subnet,
      String kmsKeyId,
      Map<String, String> metadata) {
    return LaunchInstanceDetails.builder()
        .availabilityDomain(availabilityDomain.getName())
        .compartmentId(compartmentId)
        .displayName(instanceName)
        .faultDomain("FAULT-DOMAIN-1") // optional parameter
        .metadata(metadata)
        .shape(shape.getShape())
        .sourceDetails(getSourceDetails(image, kmsKeyId))
        .createVnicDetails(getVnicDetails(subnet))
        .build();
  }

  private InstanceSourceViaImageDetails getSourceDetails(Image image, String kmsKeyId) {
    InstanceSourceViaImageDetails.Builder builder =
        InstanceSourceViaImageDetails.builder().imageId(image.getId());
    if (!Strings.isNullOrEmpty(kmsKeyId)) builder.kmsKeyId(kmsKeyId);
    return builder.build();
  }

  private CreateVnicDetails getVnicDetails(Subnet subnet) {
    return CreateVnicDetails.builder().subnetId(subnet.getId()).build();
  }

  private Instance waitForInstanceProvisioningToComplete(String instanceId) {
    try {
      return ociServices.getProvisionedInstance(
          getInstanceRequest(instanceId), Instance.LifecycleState.Running);
    } catch (Exception e) {
      throw new ClusterException("got exception while waiting for image to be created", e);
    }
  }

  private GetInstanceRequest getInstanceRequest(String instanceId) {
    return GetInstanceRequest.builder().instanceId(instanceId).build();
  }
}
