package oracle.kubernetes.clustermanager.helpers;

import com.meterware.simplestub.Stub;
import com.oracle.bmc.core.VirtualNetwork;
import com.oracle.bmc.core.model.Image;
import com.oracle.bmc.core.model.Instance;
import com.oracle.bmc.core.model.LaunchInstanceDetails;
import com.oracle.bmc.core.model.Shape;
import com.oracle.bmc.core.model.Subnet;
import com.oracle.bmc.core.requests.GetInstanceRequest;
import com.oracle.bmc.core.requests.LaunchInstanceRequest;
import com.oracle.bmc.core.requests.ListImagesRequest;
import com.oracle.bmc.core.requests.ListShapesRequest;
import com.oracle.bmc.core.requests.ListSubnetsRequest;
import com.oracle.bmc.core.responses.LaunchInstanceResponse;
import com.oracle.bmc.core.responses.ListImagesResponse;
import com.oracle.bmc.core.responses.ListShapesResponse;
import com.oracle.bmc.core.responses.ListSubnetsResponse;
import com.oracle.bmc.identity.Identity;
import com.oracle.bmc.identity.model.AvailabilityDomain;
import com.oracle.bmc.identity.requests.ListAvailabilityDomainsRequest;
import com.oracle.bmc.identity.responses.ListAvailabilityDomainsResponse;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import oracle.kubernetes.clustermanager.types.Lease;
import org.junit.Before;
import org.junit.Test;

public class ClusterHelperTest {

  private static final int LEASE_ID = 73;
  private static final String SHAPE = "my-shape";
  private static final String REGION = "my-region";
  private static final String TENANT = "my-tenant";
  private static final int BUILD_NUMBER = 13;
  private static final Date EXPIRY_TIME = new Date();
  private static final String COMPARTMENT = "compartment";
  private static final String PUBLIC_KEY = "public key";
  private OCIServicesStub oci = new OCIServicesStub();
  private ClusterHelper clusterHelper = new ClusterHelper(oci);
  private Lease lease = new Lease(LEASE_ID, SHAPE, REGION, TENANT, BUILD_NUMBER, EXPIRY_TIME);

  @Before
  public void setUp() {
    oci.setenv("CLUSTER_MANAGER_COMPARTMENT_OCID", COMPARTMENT);
    oci.setenv("CLUSTER_MANAGER_SSH_PUBLIC_KEY", PUBLIC_KEY);

    oci.defineAvailabilityDomains(REGION);
    oci.defineImages(ClusterHelper.IMAGE_NAME);
    oci.defineShapes(SHAPE);
    oci.defineSubnets(ClusterHelper.createSubnetName(REGION));
  }

  @Test(expected = ClusterException.class)
  public void whenUnableToReadAvailabilityDomains_throwException() {
    oci.throwOnListAvailabilityDomains(new RuntimeException("test"));

    clusterHelper.createCluster(lease, null);
  }

  @Test(expected = ClusterException.class)
  public void whenNoAvailabilityDomainNameMatchesLeaseRegion_throwException() {
    oci.defineAvailabilityDomains("aa", "bb");

    clusterHelper.createCluster(lease, null);
  }

  @Test(expected = ClusterException.class)
  public void whenUnableToReadImages_throwException() {
    oci.throwOnListImages(new RuntimeException("test"));

    clusterHelper.createCluster(lease, null);
  }

  @Test(expected = ClusterException.class)
  public void whenNoImageDisplayNameMatchesImageName_throwException() {
    oci.defineImages("aa", "bb");

    clusterHelper.createCluster(lease, null);
  }

  @Test(expected = ClusterException.class)
  public void whenUnableToReadShapes_throwException() {
    oci.throwOnListShapes(new RuntimeException("test"));

    clusterHelper.createCluster(lease, null);
  }

  @Test(expected = ClusterException.class)
  public void whenNoShapeMatchesLeaseShape_throwException() {
    oci.defineShapes("aa", "bb");

    clusterHelper.createCluster(lease, null);
  }

  @Test(expected = ClusterException.class)
  public void whenUnableToReadSubnets_throwException() {
    oci.throwOnListSubnets(new RuntimeException("test"));

    clusterHelper.createCluster(lease, null);
  }

  @Test(expected = ClusterException.class)
  public void whenNoSubnetDisplayNameMatchesSubnetName_throwException() {
    oci.defineSubnets("aa", "bb");

    clusterHelper.createCluster(lease, null);
  }

  @Test
  public void whenNoProblems_uhhh() throws URISyntaxException {
    clusterHelper.setScript(getFile("src/main/cloud-init/olcs-19.sh"));
    clusterHelper.createCluster(lease, null);
  }

  @SuppressWarnings("SameParameterValue")
  private File getFile(String filePath) throws URISyntaxException {
    return new File(getModuleDir(getClass()), filePath);
  }

  private static File getModuleDir(Class<?> aClass) throws URISyntaxException {
    return getTargetDir(aClass).getParentFile();
  }

  private static File getTargetDir(Class<?> aClass) throws URISyntaxException {
    File dir = getPackageDir(aClass);
    while (dir.getParent() != null && !dir.getName().equals("target")) {
      dir = dir.getParentFile();
    }
    return dir;
  }

  private static File getPackageDir(Class<?> aClass) throws URISyntaxException {
    URL url = aClass.getResource(aClass.getSimpleName() + ".class");
    return Paths.get(url.toURI()).toFile().getParentFile();
  }

  static class OCIServicesStub implements OCIServices {
    private Map<String,String> environmentOverrides = new HashMap<>();
    private RuntimeException availabilityDomainsException;
    private RuntimeException listImagesException;
    private RuntimeException listShapesException;
    private RuntimeException listSubnetsException;

    private List<AvailabilityDomain> availabilityDomains = new ArrayList<>();
    private List<Image> images = new ArrayList<>();
    private List<Shape> shapes = new ArrayList<>();
    private List<Subnet> subnets = new ArrayList<>();

    @Override
    public Instance getProvisionedInstance(GetInstanceRequest instanceRequest, Instance.LifecycleState desiredState) {
      throw new RuntimeException("tests not done");
    }

    @Override
    public LaunchInstanceResponse launchInstance(LaunchInstanceRequest launchInstanceRequest) {
      LaunchInstanceDetails details = launchInstanceRequest.getLaunchInstanceDetails();

      Instance instance = Instance.builder()
          .availabilityDomain(details.getAvailabilityDomain())
          .compartmentId(details.getCompartmentId())
          .id(details.getDisplayName())
          .imageId(details.getImageId())
          .shape(details.getShape())
          .build();
      return LaunchInstanceResponse.builder().instance(instance).build();
    }

    @Override
    public ListShapesResponse listShapes(ListShapesRequest listShapesRequest) {
      if (listShapesException != null) throw listShapesException;

      return ListShapesResponse.builder().items(shapes).build();
    }

    @Override
    public ListImagesResponse listImages(ListImagesRequest listImagesRequest) {
      if (listImagesException != null) throw listImagesException;

      return ListImagesResponse.builder().items(images).build();
    }

    @Override
    public VirtualNetwork getVncClient() {
      return Stub.createStrictStub(VirtualNetworkStub.class, this);
    }

    @Override
    public Identity createIdentityClient() {
      return Stub.createStrictStub(IdentityClientStub.class, this);
    }

    @Override
    public String getenv(String name) {
      return environmentOverrides.containsKey(name)
          ? environmentOverrides.get(name)
          : System.getenv(name);
    }

    void setenv(String name, String value) {
      environmentOverrides.put(name, value);
    }

    void throwOnListAvailabilityDomains(RuntimeException availabilityDomainsException) {
      this.availabilityDomainsException = availabilityDomainsException;
    }

    void throwOnListImages(RuntimeException listImagesException) {
      this.listImagesException = listImagesException;
    }

    void throwOnListShapes(RuntimeException listShapesException) {
      this.listShapesException = listShapesException;
    }

    void throwOnListSubnets(RuntimeException listSubnetsException) {
      this.listSubnetsException = listSubnetsException;
    }

    void defineAvailabilityDomains(String... names) {
      availabilityDomains.clear();
      Arrays.stream(names)
          .forEach(n -> availabilityDomains.add(AvailabilityDomain.builder().name(n).build()));
    }

    void defineImages(String... imageNames) {
      images.clear();
      Arrays.stream(imageNames).forEach(n -> images.add(Image.builder().displayName(n).build()));
    }

    void defineShapes(String... shapeName) {
      shapes.clear();
      Arrays.stream(shapeName).forEach(s -> shapes.add(Shape.builder().shape(s).build()));
    }

    void defineSubnets(String... subnetNames) {
      subnets.clear();
      Arrays.stream(subnetNames).forEach(n -> subnets.add(Subnet.builder().displayName(n).build()));
    }

    abstract class VirtualNetworkStub implements VirtualNetwork {
      @Override
      public ListSubnetsResponse listSubnets(ListSubnetsRequest listSubnetsRequest) {
        if (listSubnetsException != null) throw listSubnetsException;
        return ListSubnetsResponse.builder().items(subnets).build();
      }
    }

    abstract class IdentityClientStub implements Identity {
      @Override
      public ListAvailabilityDomainsResponse listAvailabilityDomains(ListAvailabilityDomainsRequest listAvailabilityDomainsRequest) {
        if (availabilityDomainsException != null) throw availabilityDomainsException;
        
        return ListAvailabilityDomainsResponse.builder().items(availabilityDomains).build();
      }

      @Override
      public void close() {

      }
    }
  }

}