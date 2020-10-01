/*
 * Copyright 2019-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onosproject.srv6_usid.cli;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.onlab.packet.Ip6Address;
import org.onlab.packet.IpAddress;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.cli.net.DeviceIdCompleter;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.device.DeviceService;
import org.onlab.packet.MacAddress;
import org.onosproject.srv6_usid.Srv6Component;

/**
 *  uA Insert Command
 */
@Service
@Command(scope = "onos", name = "uA-insert",
         description = "Insert a uA rule into the IPv6 Routing table and xconnect table")
public class UAInsertCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "uri", description = "Device ID",
              required = true, multiValued = false)
    @Completion(DeviceIdCompleter.class)
    String uri = null;

    @Argument(index = 1, name = "uAInstruction",
            description = "IPv6 address of the uA Instruction",
            required = true, multiValued = false)
    String uAInstruction = null;

    @Argument(index = 2, name = "ipv6NextHop",
            description = "IPv6 address",
            required = true, multiValued = false)
    String ipv6NextHop = null;

    @Argument(index = 3, name = "macDstAddr",
            description = "MAC destination address",
            required = true, multiValued = false)
    String macDstAddr = null;

    @Override
    protected void doExecute() {
        DeviceService deviceService = get(DeviceService.class);
        Srv6Component app = get(Srv6Component.class);

        Device device = deviceService.getDevice(DeviceId.deviceId(uri));
        if (device == null) {
            print("Device \"%s\" is not found", uri);
            return;
        }
        
        Ip6Address uAInst = Ip6Address.valueOf(uAInstruction);
        Ip6Address nextHop = Ip6Address.valueOf(ipv6NextHop);
        MacAddress nextHopMac = MacAddress.valueOf(macDstAddr);

        print("Installing uA Instruction on device %s", uri);

        app.insertUARule(device.id(), uAInst, nextHop, nextHopMac);
    }

}
