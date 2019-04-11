# ROLLING_MIGRATION/Canary mode upgrade with session sticky with WebLogic operator behind istio

## Overview

This is a demo showing how we can do the ROLLING_MIGRATION/Canary mode upgrade with session sticky with WebLogic operator behind istio.

Suppose there is a domain(`domain1` in namespace `ns1`) which is running on WebLogic operator behind istio, and now the web application in it needs to be upgraded to a new version. 

The following is the steps of ROLLING_MIGRATION/Canary mode upgrade:

1. provision a new domain(`domain2` in namespace `ns2`) and deploy the new version of web application in it
1. route new requests to `domain2` incrementally, verify that the requests routed to `domain2` are processed correctly, while the old requests with sessions are still routed to the same server
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

Deploy [testwebapp](../testwebapp.war) using Weblogic Admin Console. This web application is configured with in-memory HTTP session state replication enabled.

Access http://$GATEWAY_URL/testwebapp/ using browser, make sure you see similar response:

```
InetAddress: domain1-managed-server2/10.1.2.107
InetAddress.hostname: domain1-managed-server2
SessionAttributes: domain1-managed-server2(1)
```

## step 1: provision a new domain

Follow [WebLogic sample domain home on a persistent volume](../../README.md) to create a new domain `domain2` in namespace `ns2` with the external adminService nodePort `30014` enabled.

Make sure you can access http://$INGRESS_HOST:30014/console using browser.

Deploy [the new version of testwebapp](../testwebapp-v2.war) using Weblogic domain2 Admin Console. This version is also configured with in-memory HTTP session state replication enabled.

Create a NodePort service to test domain2 cluster traffic:

```
$ kubectl apply -f ../domain2-cluster-1-service-external.yaml 
```

Access http://$INGRESS_HOST:31111/testwebapp/ using browser, make sure you see similar response:

```
Version: V2
InetAddress: domain2-managed-server2/10.1.2.121
InetAddress.hostname: domain2-managed-server2
SessionAttributes: domain2-managed-server2(1)
```

## step 2: route new requests to `domain2` incrementally

Send new requests, and verify that all new requests are routed to `domain1`:

```
$ sh send-requests.sh 
curl -i http://10.245.252.212:31380/testwebapp/ -H cookie:  ...
--------stat of the latest 30 requests: percent%(access count)--------
domain1-managed-server1	domain1-managed-server2	domain2-managed-server1	domain2-managed-server2	
   43%(13)		   56%(17)		    0%( 0)		    0%( 0)	
```

Start http session A, and verify that all requests in the session are routed to the same server in `domain1`:

```
$ sh start-session.sh 
curl -i http://10.245.252.212:31380/testwebapp/ -H cookie:  ...
response:
	from domain1-managed-server2
	with header set-cookie: JSESSIONID=Al8LmVV6eupBCH-NaNepC0-MHiKMbUAROBAUS4ZhbGcqDM3DCG6_!816636894!-1301334157
	with header set-cookie: domain1-msid="99527696f4556fcc"
	with header set-cookie: domain-name=domain1

curl -i http://10.245.252.212:31380/testwebapp/ -H cookie: domain1-msid="99527696f4556fcc"; domain-name=domain1; JSESSIONID=Al8LmVV6eupBCH-NaNepC0-MHiKMbUAROBAUS4ZhbGcqDM3DCG6_!816636894!-1301334157 ...
response:
	from domain1-managed-server2

curl -i http://10.245.252.212:31380/testwebapp/ -H cookie: domain1-msid="99527696f4556fcc"; domain-name=domain1; JSESSIONID=Al8LmVV6eupBCH-NaNepC0-MHiKMbUAROBAUS4ZhbGcqDM3DCG6_!816636894!-1301334157 ...
--------stat of the latest 30 requests: percent%(access count) --------
domain1-managed-server1	domain1-managed-server2	domain2-managed-server1	domain2-managed-server2	
    0%( 0)		  100%(20)		    0%( 0)		    0%( 0)			
```

### route 10% of traffic to `domain2`

```
$ kubectl apply -f routes-90-10.yaml
```

Verify that around 10% of new requests are routed to `domain2`:

```
domain1-managed-server1 domain1-managed-server2 domain2-managed-server1 domain2-managed-server2
   43%(13)                 43%(13)                  6%( 2)                  6%( 2)
```

then verify that the requests in the old `domain1` http session A are still routed to the same server in `domain1`.

Start another http session B, and verify that all the requests are routed to the same server in `domain1`:

```
$ sh start-session.sh 
curl -i http://10.245.252.212:31380/testwebapp/ -H cookie:  ...
response:
	from domain1-managed-server1
	with header set-cookie: JSESSIONID=RT0LoBGnq-shvwOuQvBKi-z0Tv_K029f4zbywZ7UDnF4tf1s1fWH!-1301334157!816636894
	with header set-cookie: domain1-msid="7f365561a609d787"
	with header set-cookie: domain2-msid="7f365561a609d787"
	with header set-cookie: domain-name=domain1

curl -i http://10.245.252.212:31380/testwebapp/ -H cookie: domain1-msid="7f365561a609d787"; domain2-msid="7f365561a609d787"; domain-name=domain1; JSESSIONID=RT0LoBGnq-shvwOuQvBKi-z0Tv_K029f4zbywZ7UDnF4tf1s1fWH!-1301334157!816636894 ...
response:
	from domain1-managed-server1

curl -i http://10.245.252.212:31380/testwebapp/ -H cookie: domain1-msid="7f365561a609d787"; domain2-msid="7f365561a609d787"; domain-name=domain1; JSESSIONID=RT0LoBGnq-shvwOuQvBKi-z0Tv_K029f4zbywZ7UDnF4tf1s1fWH!-1301334157!816636894 ...
--------stat of the latest 30 requests: percent%(access count) --------
domain1-managed-server1	domain1-managed-server2	domain2-managed-server1	domain2-managed-server2	
  100%(27)		    0%( 0)		    0%( 0)		    0%( 0)		
```

The output may look like this when session replication takes effect in the managed servers:

```
$ sh start-session.sh 
curl -i http://10.245.252.212:31380/testwebapp/ -H cookie:  ...
response:
	from domain1-managed-server2
	with header set-cookie: JSESSIONID=Re0MCKfT5_Mf_lKPFhi6tHLN-6cVcRH-yz6OWMa9705nWmU_Q2eZ!816636894!-1301334157
	with header set-cookie: domain1-msid="181ef00d65bf73f4"
	with header set-cookie: domain2-msid="181ef00d65bf73f4"
	with header set-cookie: domain-name=domain1

curl -i http://10.245.252.212:31380/testwebapp/ -H cookie: domain1-msid="181ef00d65bf73f4"; domain2-msid="181ef00d65bf73f4"; domain-name=domain1; JSESSIONID=Re0MCKfT5_Mf_lKPFhi6tHLN-6cVcRH-yz6OWMa9705nWmU_Q2eZ!816636894!-1301334157 ...
response:
	from domain1-managed-server1
	with header set-cookie: JSESSIONID=Re0MCKfT5_Mf_lKPFhi6tHLN-6cVcRH-yz6OWMa9705nWmU_Q2eZ!-1301334157!816636894

curl -i http://10.245.252.212:31380/testwebapp/ -H cookie: domain1-msid="181ef00d65bf73f4"; domain2-msid="181ef00d65bf73f4"; domain-name=domain1; JSESSIONID=Re0MCKfT5_Mf_lKPFhi6tHLN-6cVcRH-yz6OWMa9705nWmU_Q2eZ!-1301334157!816636894 ...
--------stat of the latest 30 requests: percent%(access count) --------
domain1-managed-server1	domain1-managed-server2	domain2-managed-server1	domain2-managed-server2
   92%(13)		    7%( 1)		    0%( 0)		    0%( 0)
```

Start another http session C, and verify that all the requests are routed to the same server in `domain2`:

```
$ sh start-session.sh 
curl -i http://10.245.252.212:31380/testwebapp/ -H cookie:  ...
response:
	from domain2-managed-server2
	with header set-cookie: JSESSIONID=24YMBxzkZej3UpX-vkTOPs9wnHMQaVuP1RHbIhjC2XwGcu4AbvHk!-144010169!1059050950
	with header set-cookie: domain1-msid="c338b97a1d2e741c"
	with header set-cookie: domain2-msid="c338b97a1d2e741c"
	with header set-cookie: domain-name=domain2

curl -i http://10.245.252.212:31380/testwebapp/ -H cookie: domain1-msid="c338b97a1d2e741c"; domain2-msid="c338b97a1d2e741c"; domain-name=domain2; JSESSIONID=24YMBxzkZej3UpX-vkTOPs9wnHMQaVuP1RHbIhjC2XwGcu4AbvHk!-144010169!1059050950 ...
response:
	from domain2-managed-server1
	with header set-cookie: JSESSIONID=24YMBxzkZej3UpX-vkTOPs9wnHMQaVuP1RHbIhjC2XwGcu4AbvHk!1059050950!-144010169

curl -i http://10.245.252.212:31380/testwebapp/ -H cookie: domain1-msid="c338b97a1d2e741c"; domain2-msid="c338b97a1d2e741c"; domain-name=domain2; JSESSIONID=24YMBxzkZej3UpX-vkTOPs9wnHMQaVuP1RHbIhjC2XwGcu4AbvHk!1059050950!-144010169 ...
--------stat of the latest 30 requests: percent%(access count) --------
domain1-managed-server1	domain1-managed-server2	domain2-managed-server1	domain2-managed-server2
    0%( 0)		    0%( 0)		   91%(11)		    8%( 1)
```


### route 50% of traffic to `domain2`

```
$ kubectl apply -f routes-50-50.yaml
```

Verify that around 50% of new requests are routed to `domain2`:

```
domain1-managed-server1	domain1-managed-server2	domain2-managed-server1	domain2-managed-server2
   33%(10)		   23%( 7)		   20%( 6)		   23%( 7)		
```

then verify that the requests in the old `domain1` http session A & B are still routed to the same server in `domain1`, and the requests in the old `domain2` http session C are still routed to the same server in `domain2`.

Start another http session D, and verify that all the requests are routed to the same server in `domain1`:

```
$ sh start-session.sh 
curl -i http://10.245.252.212:31380/testwebapp/ -H cookie:  ...
response:
	from domain1-managed-server2
	with header set-cookie: JSESSIONID=5ZkMD_mwuUOibwh7XNv7fadEGe9XhR2yNPkcdIsvQf2kpB7JCbeo!816636894!-1301334157
	with header set-cookie: domain1-msid="9ec7c1d911d42516"
	with header set-cookie: domain2-msid="9ec7c1d911d42516"
	with header set-cookie: domain-name=domain1

curl -i http://10.245.252.212:31380/testwebapp/ -H cookie: domain1-msid="9ec7c1d911d42516"; domain2-msid="9ec7c1d911d42516"; domain-name=domain1; JSESSIONID=5ZkMD_mwuUOibwh7XNv7fadEGe9XhR2yNPkcdIsvQf2kpB7JCbeo!816636894!-1301334157 ...
response:
	from domain1-managed-server1
	with header set-cookie: JSESSIONID=5ZkMD_mwuUOibwh7XNv7fadEGe9XhR2yNPkcdIsvQf2kpB7JCbeo!-1301334157!816636894

curl -i http://10.245.252.212:31380/testwebapp/ -H cookie: domain1-msid="9ec7c1d911d42516"; domain2-msid="9ec7c1d911d42516"; domain-name=domain1; JSESSIONID=5ZkMD_mwuUOibwh7XNv7fadEGe9XhR2yNPkcdIsvQf2kpB7JCbeo!-1301334157!816636894 ...
--------stat of the latest 30 requests: percent%(access count) --------
domain1-managed-server1	domain1-managed-server2	domain2-managed-server1	domain2-managed-server2
   91%(11)		    8%( 1)		    0%( 0)		    0%( 0)		
```

Start another http session E, and verify that all the requests are routed to the same server in `domain2`:

```
$ sh start-session.sh 
curl -i http://10.245.252.212:31380/testwebapp/ -H cookie:  ...
response:
	from domain2-managed-server1
	with header set-cookie: JSESSIONID=tyQMDxevwn1nWA-t2jUoy4g6to1e4De7pNSvuCYu411FXLsr_QHA!1059050950!-144010169
	with header set-cookie: domain1-msid="b35d5abfea628cdb"
	with header set-cookie: domain2-msid="b35d5abfea628cdb"
	with header set-cookie: domain-name=domain2

curl -i http://10.245.252.212:31380/testwebapp/ -H cookie: domain1-msid="b35d5abfea628cdb"; domain2-msid="b35d5abfea628cdb"; domain-name=domain2; JSESSIONID=tyQMDxevwn1nWA-t2jUoy4g6to1e4De7pNSvuCYu411FXLsr_QHA!1059050950!-144010169 ...
response:
	from domain2-managed-server1

curl -i http://10.245.252.212:31380/testwebapp/ -H cookie: domain1-msid="b35d5abfea628cdb"; domain2-msid="b35d5abfea628cdb"; domain-name=domain2; JSESSIONID=tyQMDxevwn1nWA-t2jUoy4g6to1e4De7pNSvuCYu411FXLsr_QHA!1059050950!-144010169 ...
--------stat of the latest 30 requests: percent%(access count) --------
domain1-managed-server1	domain1-managed-server2	domain2-managed-server1	domain2-managed-server2
    0%( 0)		    0%( 0)		  100%(13)		    0%( 0)
```

## step3: route all new traffic to `domain2`

```
$ kubectl apply -f routes-all-domain2.yaml
```

Verify that all new requests are routed to `domain2`:

```
domain1-managed-server1	domain1-managed-server2	domain2-managed-server1	domain2-managed-server2
    0%( 0)		    0%( 0)		   46%(14)		   53%(16)		
```

then verify that the requests in the old `domain1` http session A & B & D are still routed to the same server in `domain1`, and the requests in the old `domain2` http session C & E are still routed to the same server in `domain2`.

Start another http session F, and verify that all the requests are routed to the same server in `domain2`:

```
$ sh start-session.sh 
curl -i http://10.245.252.212:31380/testwebapp/ -H cookie:  ...
response:
	from domain2-managed-server1
	with header set-cookie: JSESSIONID=8kMMFIdFJ6dxy6PXj-lfnAERLnOkw5XRis56HHu9Ky1SuPrdkk7i!1059050950!-144010169
	with header set-cookie: domain2-msid="aee94dd73aef4bea"
	with header set-cookie: domain-name=domain2

curl -i http://10.245.252.212:31380/testwebapp/ -H cookie: domain2-msid="aee94dd73aef4bea"; domain-name=domain2; JSESSIONID=8kMMFIdFJ6dxy6PXj-lfnAERLnOkw5XRis56HHu9Ky1SuPrdkk7i!1059050950!-144010169 ...
response:
	from domain2-managed-server1

curl -i http://10.245.252.212:31380/testwebapp/ -H cookie: domain2-msid="aee94dd73aef4bea"; domain-name=domain2; JSESSIONID=8kMMFIdFJ6dxy6PXj-lfnAERLnOkw5XRis56HHu9Ky1SuPrdkk7i!1059050950!-144010169 ...
--------stat of the latest 30 requests: percent%(access count) --------
domain1-managed-server1	domain1-managed-server2	domain2-managed-server1	domain2-managed-server2
    0%( 0)		    0%( 0)		  100%(12)		    0%( 0)
```

## step3: disconnect to `domain1`

Stop the old `domain1` http sessions A & B & D.

When there is no more traffic to `domain1`, cut the connection from istiio ingressgateway to `domain1`, route all traffic to `domain2`:

```
$ kubectl apply -f disconnect-domain1.yaml
```

verify that all the new requests are still routed to `domain2`, and all the requests in the old `domain2` http session C & E are still routed to the same server in `domain2`.


## step4: delete the old domain `domain1`

Delete the old domain after you check there is no traffic routed to it.


