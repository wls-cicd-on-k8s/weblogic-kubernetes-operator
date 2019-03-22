# ROLLING_MIGRATION/Canary mode upgrade with WebLogic operator on istio

## Overview

This is a demo showing how we can do the ROLLING_MIGRATION/Canary mode upgrade with WebLogic operator on istio.

Suppose there is a domain(`domain1` in namespace `ns1`) which is running on WebLogic operator+istio, and now the web application in it needs to be upgraded to a new version. 

The following is the steps of ROLLING_MIGRATION/Canary mode upgrade:

1. provision a new domain(`domain2` in namespace `ns2`) and deploy the new version of web application in it
1. route new requests to `domain2` incrementally, verify that the requests routed to `domain2` are processed correctly
1. route all the traffic to `domain2`
1. delete old domain `domain1`


## Prerequisites

Make sure you can access istio ingressgateway. Follow [istio Control Ingress Traffic](https://istio.io/docs/tasks/traffic-management/ingress/) to determin `INGRESS_HOST` and `INGRESS_PORT`, then run:

```
$ export GATEWAY_URL=$INGRESS_HOST:$INGRESS_PORT
```

Create a gateway and a virtualservice to route traffic from istio ingressgateway to admin console and to domain1-cluster-cluster-1.ns1.svc.cluster.local:

```
$ kubectl apply -f connect-domain1.yaml
```

Make sure you can access http://$GATEWAY_URL/console using browser.

Deploy [testwebapp](../../../charts/application/testwebapp.war) using Weblogic Admin Console.

Access http://$GATEWAY_URL/testwebapp/ using browser, make sure you see similar response:

```
InetAddress: domain1-managed-server2/10.1.2.107
InetAddress.hostname: domain1-managed-server2
```

## step 1: provision a new domain

Follow [WebLogic sample domain home on a persistent volume](../../README.md) to create a new domain `domain2` in namespace `ns2` with the external adminService nodePort `30014` enabled.

Make sure you can access http://$INGRESS_HOST:30014/console using browser.

Deploy [the new version of testwebapp](../testwebapp-v2.war) using Weblogic domain2 Admin Console.

Create a NodePort service to test domain2 cluster traffic:

```
$ kubectl apply -f ../domain2-cluster-1-service-external.yaml 
```

Access http://$INGRESS_HOST:31111/testwebapp/ using browser, make sure you see similar response:

```
Version: V2
InetAddress: domain2-managed-server2/10.1.2.121
InetAddress.hostname: domain2-managed-server2
```

## step 2: route new requests to `domain2` incrementally

Send new requests, and verify that all the requests are routed to `domain1`:

```
$ sh send-requests.sh
sending requests to 10.245.252.212:31380/testwebapp/ and calculating...
--------stat of the latest 30 requests: percent%(access count)--------
ddomain1-managed-server1        domain1-managed-server2 domain2-managed-server1 domain2-managed-server2
   53%(16)                 46%(14)                  0%( 0)                  0%( 0)
```

### route to random traffic based on weight

Route 10% of traffic to `domain2`:

```
$ kubectl apply -f routes-90-10.yaml
```

Verify that around 10% of requests are routed to `domain2`:

```
domain1-managed-server1 domain1-managed-server2 domain2-managed-server1 domain2-managed-server2
   43%(13)                 43%(13)                  6%( 2)                  6%( 2)
```

Route 50% of traffic to `domain2`:

```
$ kubectl apply -f routes-50-50.yaml
```

Verify that around 50% of requests are routed to `domain2`:

```
domain1-managed-server1 domain1-managed-server2 domain2-managed-server1 domain2-managed-server2
   26%( 8)                 30%( 9)                 26%( 8)                 16%( 5)
```

### route to internal users

Route all of traffic from `oracle.com` to `domain2`:

```
$ kubectl apply -f routes-users.yaml
```

Verify that the requests without user information are still routed to `domain1`:

```
domain1-managed-server1 domain1-managed-server2 domain2-managed-server1 domain2-managed-server2
   50%(15)                 50%(15)                  0%( 0)                  0%( 0)
```

Send requests with cookie containing `oracle.com`, verify that those requests are routed to `domain2`:

```
$ sh send-requests-with-user-info.sh
domain1-managed-server1 domain1-managed-server2 domain2-managed-server1 domain2-managed-server2
   0%( 0)                   0%( 0)                 50%(15)                 50%(15)

```

## step3: route all traffic to `domain2`

Stop the old `domain1` http sessions.

When there is no more traffic to `domain1`, cut the connection from istiio ingressgateway to `domain1`, route all traffic to `domain2`:

```
$ kubectl apply -f disconnect-domain1.yaml
```

verify that all the new requests are routed to `domain2`:

```
domain1-managed-server1	domain1-managed-server2	domain2-managed-server1	domain2-managed-server2	
    0%( 0)		    0%( 0)		   13%( 4)		   86%(26)		
```

## step4: delete the old domain `domain1`

Delete the old domain after you check there is no traffic routed to it.


