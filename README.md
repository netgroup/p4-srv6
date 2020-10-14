# IPv6 Segment Routing SRv6 <br/>
SRv6 is a network architecture that encodes a list of instructions in the IPv6 packet header to define a network wide packet processing program. <br/>
Each instruction defines a node to process the packet and the behavior to be applied to that packet by that node.<br/>
The [SRv6 network programming](https://tools.ietf.org/html/draft-ietf-spring-srv6-network-programming-24) framework is being defined in IETF.<br/>

# Implementation
In the project we provide an open source data plane of SRv6 in P4. We Leverage the Open Network Operating System (ONOS) for the control plane. <br/>
We augmented ONOS implementation with the necessary extensions to support SRv6. <br/>

This work is done as part of the Research on Open SRv6 Ecosystem ([ROSE](https://netgroup.github.io/rose/)) project. 

This work is based on the P4 tutorial by the Open Networking Foundation. For more information about the above listed software modules you can visit the Open Networking Foundation original [repository](https://github.com/opennetworkinglab/ngsdn-tutorial). <br/>

There you can also find useful material like the slides explaining the tutorial and a prepared Ubuntu virtual machine with all the software installed. It is strongly recommended to download the prepared VM and run the DEMO inside it, as it contains the several dependencies needed to run the software. <br/>


# Repository structure
This repository is structured as follows: <br/>
 * `app/` ONOS app Java implementation <br/>
 * `config/` configuration files <br/>
 * `mininet/` Mininet script to emulate a topology of `stratum_bmv2` devices <br/>
 * `p4src/` P4 implementation <br/>
 * `test/` test packets <br/>
 * `utils/` utilities include docker file <br/>

## Usage 
TBD 
<!--
In the section we show the steps needed to run the SRv6 micro SID demo, starting from the downloaded VM. <br/>

The demo runs on a mininet topology made up of fourteen P4 enabled switches (based on [bmv2](https://github.com/p4lang/behavioral-model) P4 software implementation) and two hosts that represent Site A and Site B. For this demo we rely on static routing for simplicity. <br/>
The Onos controller is used to configure the P4 software switches with the various table entries, e.g. SRv6 Micro SID routes, L2 forwarding entries, etc. <br/>

## DEMO commands
To ease the execution of the commands needed to setup the required software, we make use of the Makefile prepared by the ONF for their [P4 tutorial](https://github.com/opennetworkinglab/ngsdn-tutorial). <br/>

```
| Make command        | Description                                            | <br/>
|---------------------|------------------------------------------------------- | <br/>
| `make start`        | Runs ONOS and Mininet containers                       | <br/>
| `make onos-cli`     | Access the ONOS command line interface (CLI)           | <br/>
| `make app-build`    | Builds the tutorial app and pipeconf                   | <br/>
| `make app-reload`   | Load the app in ONOS                                   | <br/>
| `make mn-cli`       | Access the Mininet CLI                                 | <br/>
| `make netcfg`       | Pushes netcfg.json file (network config) to ONOS       | <br/>
| `make stop`         | Resets the tutorial environment                        | <br/>
 ```
 
## Detailed DEMO description

### 1. Start ONOS
In a terminal window, start the ONOS main process by running and connect to the logs: <br/>
```bash <br/>
$> make start <br/>
$> make onos-log <br/>
``` 
### 2. Build and load the application 
An application is provided to ONOS as an executable in .oar format. To build the source code contained in `app/` issue the following command: <br/>
```bash <br/>
$> make app-build <br/>
``` 
This will create the `srv6-uSID-1.0-SNAPSHOT.oar` application binary in the `app/target/` folder. <br/>
Moreover, it will compile the p4 code contained in `p4src` creating two output files: <br/>
- `bmv2.json` is the JSON description of the dataplane programmed in P4; <br/>
- `p4info.txt` contains the information about the southbound interface used by the controller to program the switches. <br/>
These two files are symlinked inside the `app/src/main/resources/` folder and used to build the application. <br/>
After the creation of the binary, we have to load it inside ONOS: <br/>

```bash
$> make app-reload <br/>
```
The app should now be registered in ONOS. <br/>

### 3. Push the network configuration to ONOS
ONOS gets its global network view thanks to a JSON configuration file in which it is possible to encode several information about the switch configuration. <br/>
This file is parsed at runtime by the application and it is needed to configure, e.g. the MAC addresses, SID and uSID addresses assigned to each P4 switch. <br/>
Let's push it to ONOS by prompting the following command: <br/>
```bash <br/>
$> make netcfg 
```
Now ONOS knows how to connect to the switches set up in mininet. <br/>

### 4. Insert the SRv6 micro SID routing directives
In a new window open the ONOS CLI with the following command: <br/>
```bash <br/>
$> make onos-cli <br/>
```
For the purpose of this DEMO, we statically configured the IPv6 routes of each router inside the `config/routing_tables.txt` file consisting of a list of `route-insert` commands. Also the uA Instructions are contained in the `config/ua-config.txt` in the form of a list of `uA-insert` commands. Configure them inside the switches by sourcing this file inside the CLI: <br/>
```bash 
onos-cli> source /config/routing_tables.txt
onos-cli> source /config/ua_config.txt
```
Then, we can insert the uSID routing directive to the the two end routers, one for the path H1 ===> H2 and one for the reverse path H2 ===> H1: <br/>

```bash <br/>
onos-cli> srv6-insert device:r1 fcbb:bb00:8:7:2:fd00:: 2001:1:2::1 <br/>
onos-cli> srv6-insert device:r2 fcbb:bb00:7:8:1:fd00:: 2001:1:1::1 <br/>
```
Essentially, these commands specify to the end routers (R1 and R2) to insert an SRv6 header with a list of SIDs. The first represents the list of uSID that the packet must traverse while the last is the IPv6 address of the host the packet is destined to.  <br/>
### 6. Test
Test the communication between the two hosts with ping inside mininet. <br/>
```bash <br/>
$> make mn-cli <br/>
mininet> h2 ping h1 <br/>
mininet> h1 ping h2 <br/>
```
The first pings will not work since the switch will not know how to reach the host at L2 layer. After learning on both paths it will work. <br/>
It is also possible to have a graphical representation of the running topology thanks to the ONOS web UI. Type in a browser `localhost:8181/onos/ui` and enter as user `onos` with password `rocks`. It will display the graphical representation of the topology. <br/>
Now, let's make some faster pings: 
```bash 
mininet> h1 ping h2 -i 0.1 
``` 
Then, return to the UI and press <br/>
* `h` to show the hosts <br/>
* `l` to display the nodes labels <br/>
* `a` a few times until it displays link utilization in packets per second <br/>
-->
