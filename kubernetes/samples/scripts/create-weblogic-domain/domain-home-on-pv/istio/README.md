# Basic features of WebLogic operator on istio

## Prerequisites

Suppose you have successfully create a domain on pv on WebLogic operator+istio. 

Make sure you can access istio ingressgateway. Follow [istio Control Ingress Traffic](https://istio.io/docs/tasks/traffic-management/ingress/) to determin `INGRESS_HOST` and `INGRESS_PORT`, then run:

```
$ export GATEWAY_URL=$INGRESS_HOST:$INGRESS_PORT
```

## Access admin console and testwebapp through istio ingressgateway

### Create a gateway and a virtualservice

Create a gateway and a virtualservice to route traffic from istio ingressgateway to domain1-cluster-cluster-1.default.svc.cluster.local:

```
$ kubectl create -f gw.yaml
```

### Enable external adminService nodePort

Enable external adminService nodePort by uncommenting the following settings in domain.xml, then restart the domain.

```
    adminService:
      channels:
        - channelName: default
          nodePort: 30701
```

### Change external adminService to use ClusterIP

Edit the external adminService to remove line `nodePort: 30701` and change the `type` from `NodePort` to `ClusterIP`.

```
$ kubectl edit service domain1-admin-server-external
```

### Access admin console through istio ingressgateway

You can access http://$GATEWAY_URL/console using browser.


### Deploy testwebapp to weblogic cluster

Deploy [testwebapp](../../../charts/application/testwebapp.war) using Weblogic Admin Console.

### Access testwebapp through istio ingressgateway

If you access http://$GATEWAY_URL/testwebapp/ using browser, you can see the following response:

```
InetAddress: domain1-managed-server1/10.244.0.22
InetAddress.hostname: domain1-managed-server1
```

You can also run the following command to send more requests:

```
$ sh run.sh 
sending 30 requests to slc09xwj.us.oracle.com:31380/testwebapp/ ...
 
the access count of pod domain1-managed-server1 is 14
the access count of pod domain1-managed-server2 is 16
```


## Test istio traffic shifting on operator

Refter to [istio traffic shifting](https://istio.io/docs/tasks/traffic-management/traffic-shifting/) for the feature.

### Apply weight-based routing

Deploy the destination rule for cluster-cluster-1 service, which defines separate subsets `ms1` and `ms2`.

```
$ kubectl create -f destination-rule-cluster1.yaml
```

Update the virtual service to route 80% of traffic from the istio ingressgateway to ms1 and 20% to ms2.

```
$ kubectl replace -f virtual-service-cluster-80-20.yaml
```

Run the following command to send requests:

```
$ sh run.sh
sending 30 requests to slc09xwj.us.oracle.com:31380/testwebapp/ ...
 
the access count of pod domain1-managed-server1 is 25
the access count of pod domain1-managed-server2 is 5
```

### Route all the traffic to ms1


```
$ kubectl replace -f virtual-service-cluster-ms1.yaml
```

Run the following command to send requests:

```
$ sh run.sh 
sending 30 requests to slc09xwj.us.oracle.com:31380/testwebapp/ ...
 
the access count of pod domain1-managed-server1 is 30
the access count of pod domain1-managed-server2 is 0
```
