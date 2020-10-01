#!/usr/bin/python

#  Copyright 2019-present Open Networking Foundation
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

import argparse

from mininet.cli import CLI
from mininet.log import setLogLevel
from mininet.net import Mininet
from mininet.node import RemoteController
from mininet.topo import Topo

from stratum import StratumBmv2Switch
from host6 import IPv6Host

CPU_PORT = 255


class TutorialTopo(Topo):
    
    """
    /--------\   /----\   /----\   /----\   /----\
    | Site A |---| R1 |---| R4 |---| R5 |---| R8 |
    \________/   \____/   \____/   \____/   \____/
                   |         |       |        |
                     \     /       |        |
					   R11		R10		 R9
				     / 	   \   
				   |				
    /--------\   /----\   /----\   /----\   /----\
    | Site B |---| R2 |---| R3 |---| R6 |---| R7 |
    \________/   \____/   \____/   \____/   \____/

    """


    def __init__(self, *args, **kwargs):
        Topo.__init__(self, *args, **kwargs)

        # End routers
        r1 = self.addSwitch('r1', cls=StratumBmv2Switch,cpuport=CPU_PORT)
        r2 = self.addSwitch('r2', cls=StratumBmv2Switch,cpuport=CPU_PORT)

        # Transit routers
        r3 = self.addSwitch('r3', cls=StratumBmv2Switch, cpuport=CPU_PORT)
        r4 = self.addSwitch('r4', cls=StratumBmv2Switch, cpuport=CPU_PORT)
        r5 = self.addSwitch('r5', cls=StratumBmv2Switch, cpuport=CPU_PORT)
        r6 = self.addSwitch('r6', cls=StratumBmv2Switch, cpuport=CPU_PORT)
        r7 = self.addSwitch('r7', cls=StratumBmv2Switch, cpuport=CPU_PORT)
        r8 = self.addSwitch('r8', cls=StratumBmv2Switch, cpuport=CPU_PORT)
        r9 = self.addSwitch('r9', cls=StratumBmv2Switch, cpuport=CPU_PORT)
        r10 = self.addSwitch('r10', cls=StratumBmv2Switch, cpuport=CPU_PORT)
        r11 = self.addSwitch('r11', cls=StratumBmv2Switch, cpuport=CPU_PORT)
        r12 = self.addSwitch('r12', cls=StratumBmv2Switch, cpuport=CPU_PORT)
        r13 = self.addSwitch('r13', cls=StratumBmv2Switch, cpuport=CPU_PORT)
        r14 = self.addSwitch('r14', cls=StratumBmv2Switch, cpuport=CPU_PORT)
        

        # Switch Links
        self.addLink(r1, r4)
        self.addLink(r1, r9)

        self.addLink(r2, r3)
        self.addLink(r2, r14)

        self.addLink(r9, r4)
        self.addLink(r9, r10)
        self.addLink(r9, r13)
        self.addLink(r9, r14)

        self.addLink(r14, r10)
        self.addLink(r14, r3)
        self.addLink(r14, r13)

        self.addLink(r4, r5)
        self.addLink(r4, r10)

        self.addLink(r3, r13)
        self.addLink(r3, r6)

        self.addLink(r10, r5)
        self.addLink(r10, r11)
        self.addLink(r10, r12)
        self.addLink(r10, r13)

        self.addLink(r13, r11)
        self.addLink(r13, r12)
        self.addLink(r13, r6)

        self.addLink(r5, r8)
        self.addLink(r5, r11)

        self.addLink(r6, r7)
        self.addLink(r6, r12)

        self.addLink(r11, r8)
        self.addLink(r11, r12)

        self.addLink(r12, r7)

        self.addLink(r8, r7)


        # IPv6 hosts attached to leaf 1
        h1 = self.addHost('h1', cls=IPv6Host, mac="00:00:00:00:00:10",
                           ipv6='2001:1:1::1/64', ipv6_gw='2001:1:1::ff')
        h2 = self.addHost('h2', cls=IPv6Host, mac="00:00:00:00:00:20",
                          ipv6='2001:1:2::1/64', ipv6_gw='2001:1:2::ff')

        self.addLink(h1, r1)
        self.addLink(h2, r2)


def main():
    topo = TutorialTopo()
    controller = RemoteController('c0', ip="127.0.0.1")

    net = Mininet(topo=topo, controller=None)
    net.addController(controller)

    net.start()
    CLI(net)
    net.stop()


if __name__ == "__main__":
    main()
