# ROLLING_MIGRATION with WebLogic operator on istio

## Prerequisites

Suppose you have and old domain on pv on WebLogic operator+istio, and now you want to upgrade a web application to a new version. Suppose the old domain `domain1` is installed in namespace `ns1` and the external adminService nodePort is enabled.

Make sure you can access istio ingressgateway. Follow [istio Control Ingress Traffic](https://istio.io/docs/tasks/traffic-management/ingress/) to determin `INGRESS_HOST` and `INGRESS_PORT`, then run:

```
$ export GATEWAY_URL=$INGRESS_HOST:$INGRESS_PORT
```

Create a gateway and a virtualservice to route traffic from istio ingressgateway to admin console and to domain1-cluster-cluster-1.ns1.svc.cluster.local:

```
$ kubectl apply -f gw.yaml
```

Make sure you can access http://$GATEWAY_URL/console using browser.

Deploy [testwebapp](../../../charts/application/testwebapp.war) using Weblogic Admin Console.

Access http://$GATEWAY_URL/testwebapp/ using browser, make sure you see similar response:

```
InetAddress: domain1-managed-server2/10.1.2.107
InetAddress.hostname: domain1-managed-server2
```


## steps of ROLLING_MIGRATION / parallel style upgrade

Here we will provision a new domain `domain2` in namespace `ns2` while keeping `domain1` running, and after all the traffic goes to `domain2` which means the upgrade is done, the old domain `domain1` can be shutdown and removed cleanly.

### provision a new domain

Follow [WebLogic sample domain home on a persistent volume](../../README.md) to create a new domain `domain2` in namespace `ns2` with the external adminService nodePort `30014` is enabled.

Make sure you can access http://$INGRESS_HOST:30014/console using browser.

Deploy [the new version of testwebapp](../testwebapp-v2.war) using Weblogic domain2 Admin Console.

Create a NodePort service to test domain2 cluster traffic:

```
$ kubectl apply -f domain2-cluster-1-service-external.yaml 
```

Access http://$INGRESS_HOST:31111/testwebapp/ using browser, make sure you see similar response:

```
InetAddress: domain2-managed-server2/10.1.1.206
InetAddress.hostname: domain2-managed-server2
```

### example ways to release the new version

#### example 1: release the new version to random sample of users

Run the following command to send http requests continuously:

```
$ bash run.sh
sending requests to 10.245.252.212:31380/testwebapp/ and calculating...
--------stat of the latest 30 requests: percent%(access count)--------
ddomain1-managed-server1	domain1-managed-server2	domain2-managed-server1	domain2-managed-server2	
   53%(16)		   46%(14)		    0%( 0)		    0%( 0)		
```

Update the virtualservice to route 90% of traffic from the istio ingressgateway to domain1 and 10% to domain2.

```
$ kubectl apply -f routes-90-10.yaml
```

Check the output of run.sh:

```
domain1-managed-server1	domain1-managed-server2	domain2-managed-server1	domain2-managed-server2	
   43%(13)		   43%(13)		    6%( 2)		    6%( 2)		
```

Update the virtualservice to route 50% of traffic from the istio ingressgateway to domain1 and 50% to domain2.

```
$ kubectl apply -f routes-50-50.yaml
```

Check the output of run.sh:

```
domain1-managed-server1	domain1-managed-server2	domain2-managed-server1	domain2-managed-server2	
   26%( 8)		   30%( 9)		   26%( 8)		   16%( 5)		
```

#### example 2: release the new version to internal users

Update the virtualservice to route all of traffic from `oracle.com` from the istio ingressgateway to domain2.

```
$ kubectl apply -f routes-users.yaml
```

Check the output of run.sh:

```
domain1-managed-server1	domain1-managed-server2	domain2-managed-server1	domain2-managed-server2	
   50%(15)		   50%(15)		    0%( 0)		    0%( 0)		
```

Send requests with cookie containing `oracle.com` and check the output:

```
$ COOKIE=email=fiona.feng@oracle.com bash run.sh
domain1-managed-server1	domain1-managed-server2	domain2-managed-server1	domain2-managed-server2	
   0%( 0)		    0%( 0)		   50%(15)		   50%(15)		

```

Send requests with cookie not containing `oracle.com` and check the output:

```
$ COOKIE=email=fiona.feng@google.com bash run.sh
domain1-managed-server1	domain1-managed-server2	domain2-managed-server1	domain2-managed-server2	
   50%(15)		   50%(15)		    0%( 0)		    0%( 0)		
```

#### example 3: release the new version with session sticky

Enable istio session sticky feature.

```
$ kubectl apply -f routes-enable-session.yaml 
```

Send requests with cookies according to the set-cookie headers from server.

```
$ COOKIE=AUTO bash run.sh
curl -i http://10.245.252.212:31380/testwebapp/ -H cookie:  ...
response:
	from domain1-managed-server1
	with header set-cookie: JSESSIONID=2jSekFeOVuDTZ1RMBhgnExh8JLTqz2z2vc_clYz7uVZvh7zjUKpG!1181283673
	with header set-cookie: domain1-msid="87f3d592e833c487"

curl -i http://10.245.252.212:31380/testwebapp/ -H cookie: JSESSIONID=2jSekFeOVuDTZ1RMBhgnExh8JLTqz2z2vc_clYz7uVZvh7zjUKpG!1181283673; domain1-msid="87f3d592e833c487" ...
--------stat of the latest 30 requests: percent%(access count) --------
domain1-managed-server1	domain1-managed-server2	domain2-managed-server1	domain2-managed-server2	
  100%(30)		    0%( 0)		    0%( 0)		    0%( 0)		
```

Route all the new traffic to domain2.

```
$ kubectl apply -f routes-session-domain2.yaml 
```

Check the output of `bash run.sh`.

```
domain1-managed-server1	domain1-managed-server2	domain2-managed-server1	domain2-managed-server2	
    0%( 0)		    0%( 0)		   13%( 4)		   86%(26)		
```

Check the output of the above `COOKIE=AUTO bash run.sh`.

```
domain1-managed-server1	domain1-managed-server2	domain2-managed-server1	domain2-managed-server2	
  100%(30)		    0%( 0)		    0%( 0)		    0%( 0)		
```

Send requests with cookies according to the set-cookie headers from server.

```
$ COOKIE=AUTO bash run.sh
curl -i http://10.245.252.212:31380/testwebapp/ -H cookie:  ...
response:
	from domain2-managed-server2
	with header set-cookie: JSESSIONID=y2ye5T5V2D9WyE7w5kAY2ojj_BDFIZXazK_J-CU5eyOFNRVTIkMe!1217616759
	with header set-cookie: domain2-msid="b9b5228d01f09f7f"

curl -i http://10.245.252.212:31380/testwebapp/ -H cookie: JSESSIONID=y2ye5T5V2D9WyE7w5kAY2ojj_BDFIZXazK_J-CU5eyOFNRVTIkMe!1217616759; domain2-msid="b9b5228d01f09f7f" ...
--------stat of the latest 30 requests: percent%(access count) --------
domain1-managed-server1	domain1-managed-server2	domain2-managed-server1	domain2-managed-server2	
    0%( 0)		    0%( 0)		    0%( 0)		  100%(30)		
```

### Route all traffic to domain2

Update the virtualservice to route all traffic from the istio ingressgateway to domain2.

```
$ kubectl apply -f routes-domain2.yaml
```

Check the output of run.sh:

```
domain1-managed-server1	domain1-managed-server2	domain2-managed-server1	domain2-managed-server2	
    0%( 0)		    0%( 0)		   50%(15)		   50%(15)		
```

The above yaml file also route console access to domain2 admin server.

Make sure you can access http://$GATEWAY_URL/console using browser, and it connects to `domain2`.


### delete the old domain

Delete the old domain after you check there is no traffic routed to it.


