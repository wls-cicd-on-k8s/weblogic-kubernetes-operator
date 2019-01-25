# Tutorial

1. [Introduction](#introduction)
1. [Directory Structure Explained](#directory-structure-explained)
1. [Prerequisites Before Run](#prerequisites-before-run)
1. [Run with Shell Wrapper](#run-with-shell-wrapper)
1. [Run with Step-By-Step](#run-with-step-by-step)

## Introduction
This tutorial will teach you how to run WebLogic domains in a Kubernetes environment using the operator.  
This tutorial covers following steps:

1. Install WebLogic domains with the operator.
   1. Install the operator.
   1. Install WebLogic domains.  
   Three domains with different configurations will be covered:
      - `domain1`: domain-home-in-image 
   
      - `domain2`: domain-home-in-image and server-logs-on-pv
   
      - `domain3`: domain-home-on-pv
   
1. Configure load balancer to WebLogic domains.
   1. Install an Ingress controller: Traefik or Voyager.
   1. Install Ingress.

## Directory Structure Explained
The following is the directory structure of this tutorial:
```
$ tree
.
├── clean.sh
├── clean-v.sh
├── domain1
│   └── domain1.yaml
├── domain2
│   ├── domain2.yaml
│   ├── pvc.yaml
│   └── pv.yaml
├── domain3
│   ├── domain3.yaml
│   ├── pvc.yaml
│   └── pv.yaml
├── domainHomeBuilder
│   ├── build.sh
│   ├── Dockerfile
│   ├── generate.sh
│   └── scripts
│       ├── create-domain.py
│       └── create-domain.sh
├── domain.sh
├── ings
│   └── voyager-ings.yaml
├── operator.sh
├── README.md
├── setup.sh
├── setup-v.sh
├── traefik.sh
└── voyager.sh

5 directories, 17 files
```

An overview of what each of these does:
- `domainHomeBuilder`: This folder contains one Dockfile, one WLST file and some shell scripts to create domain home.

  - `build.sh`: To build a docker image with a domain home in it via calling `docker build`. The generated image name is `<domainName>-image` which will be used in domain-home-in-image case.  
    `usage: ./build.sh domainName adminUser adminPwd`
    
  - `generate.sh`: To create a domain home on a host folder via calling `docker run`. And later this folder will be mounted via a PV and used in domain-home-on-pv case.  
    `usage: ./generate.sh domainName adminUser adminPwd`
    
  - `Dockerfile`: Simple docker file to build a image with a domain home in it.
  
  - `scripts/create-domain.py`: A python script which uses offline WLST to create a domain home.
  
  - `scripts/create-domain.sh`: A simple shell wrapper to call create-domain.py.

- yaml files

  - folder `domain1`: contains yaml files for domain1.
  
  - folder `domain2`: contains yaml files for domain2.
  
  - folder `domain3`: contains yaml files for domain3.
  
  - folder `ings`: contains Ingress yaml files.
  
- shell scripts

  - `operator.sh`: To create and delete the wls operator.
  
  - `traefik.sh`: To create and delete Traefik controller and Ingresses.
  
  - `voyager.sh`: To create and delete Voyager controller and Ingresses.
  
  - `domain.sh`: To create and delete WebLogic domain related resources.
  
  - `setup.sh&clean.sh`: a couple of shell wrappers to create all from scratch and do cleanup. Use Traefik as the load balancer. 
  
  - `setup-v.sh&clean-v.sh`: a couple of shell wrappers to create all from scratch and do cleanup. Use Voyager as the load balancer. 
  
## Prerequisites Before Run
  - Have Docker installed, a Kubernetes cluster running and have `kubectl` installed and configured. If you need help on this, check out our [cheat sheet](../../site/k8s_setup.md).
  - Have Helm installed: both the Helm client (helm) and the Helm server (Tiller). See [official helm doc](https://github.com/helm/helm/blob/master/docs/install.md) for detail.

## Run with Shell Wrapper
We provide reliable and automated scripts to setup everything from scratch.  
It takes less than 20 minutes to complete. After finished, you'll have three running WebLogic domains which 
cover the three typical domain configurations and with load balancer configured.  
With the running domains, you can experiment other operator features, like scale up/down, rolling restart etc.  
See detail [here](shell-wrapper.md).

## Run with Step-By-Step
We also provide step-by-step guide to guide you setup everything from scratch. This will help you understand the essential steps and you'll be able to do customization to meet your own requirements.
See detail [here](step-by-step.md).
