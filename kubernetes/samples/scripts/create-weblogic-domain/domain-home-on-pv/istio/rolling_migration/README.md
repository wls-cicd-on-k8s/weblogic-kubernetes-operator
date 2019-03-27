# ROLLING_MIGRATION upgrade with WebLogic operator + istio

This folder contains demos showing how we can do the ROLLING_MIGRATION upgrade with WebLogic operator + istio.

Suppose there is a domain(`domain1` in namespace `ns1`) which is running on WebLogic operator+istio, and now the web application in it needs to be upgraded to a new version. 

The following is the steps of ROLLING_MIGRATION upgrade:

1. provision a new domain(`domain2` in namespace `ns2`) and deploy the new version of web application in it
1. route all the traffic to `domain2` using different strategies, verify that the requests routed to `domain2` are processed correctly
1. delete old domain `domain1`

