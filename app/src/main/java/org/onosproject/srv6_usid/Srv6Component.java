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
package org.onosproject.srv6_usid;

import com.google.common.collect.Lists;
import org.onlab.packet.Ip6Address;
import org.onosproject.core.ApplicationId;
import org.onosproject.mastership.MastershipService;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.config.NetworkConfigService;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleOperations;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.criteria.PiCriterion;
import org.onosproject.net.pi.model.PiActionId;
import org.onosproject.net.pi.model.PiActionParamId;
import org.onosproject.net.pi.model.PiMatchFieldId;
import org.onosproject.net.pi.model.PiTableId;
import org.onosproject.net.pi.runtime.PiAction;
import org.onosproject.net.pi.runtime.PiActionParam;
import org.onosproject.net.pi.runtime.PiTableAction;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.onosproject.srv6_usid.common.Srv6DeviceConfig;
import org.onosproject.srv6_usid.common.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.onlab.packet.MacAddress;


import java.util.List;
import java.util.Optional;

import static com.google.common.collect.Streams.stream;
import static org.onosproject.srv6_usid.AppConstants.INITIAL_SETUP_DELAY;

/**
 * Application which handles SRv6 segment routing.
 */
@Component(
        immediate = true,
        enabled = true,
        service = Srv6Component.class
)
public class Srv6Component {

    private static final Logger log = LoggerFactory.getLogger(Srv6Component.class);

    //--------------------------------------------------------------------------
    // ONOS CORE SERVICE BINDING
    //
    // These variables are set by the Karaf runtime environment before calling
    // the activate() method.
    //--------------------------------------------------------------------------

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private MastershipService mastershipService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private NetworkConfigService networkConfigService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private MainComponent mainComponent;

    private final DeviceListener deviceListener = new Srv6Component.InternalDeviceListener();

    private ApplicationId appId;

    //--------------------------------------------------------------------------
    // COMPONENT ACTIVATION.
    //
    // When loading/unloading the app the Karaf runtime environment will call
    // activate()/deactivate().
    //--------------------------------------------------------------------------

    @Activate
    protected void activate() {
        appId = mainComponent.getAppId();

        // Register listeners to be informed about device and host events.
        deviceService.addListener(deviceListener);

        // Schedule set up for all devices.
        mainComponent.scheduleTask(this::setUpAllDevices, INITIAL_SETUP_DELAY);

        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        deviceService.removeListener(deviceListener);

        log.info("Stopped");
    }

    /**
     * Populate the My micro SID table from the network configuration for the
     * specified device.
     *
     * @param deviceId the device Id
     */
    private void setUpMyUSidTable(DeviceId deviceId) {
        //TODO: add to the listenet
        Ip6Address myUSid = getMyUSid(deviceId);
        Ip6Address myUDX = getMyUDX(deviceId);

        log.info("Adding two myUSid rules on {} (sid {})...", deviceId, myUSid);

        String tableId = "IngressPipeImpl.srv6_localsid_table";

        PiCriterion match = PiCriterion.builder()
                .matchLpm(
                        PiMatchFieldId.of("hdr.ipv6.dst_addr"),
                        myUSid.toOctets(), 48)
                .build();

        PiTableAction action = PiAction.builder()
                .withId(PiActionId.of("IngressPipeImpl.srv6_usid_un"))
                .build();

        FlowRule myStationRule = Utils.buildFlowRule(
                deviceId, appId, tableId, match, action);

        flowRuleService.applyFlowRules(myStationRule);

        match = PiCriterion.builder()
                .matchLpm(
                        PiMatchFieldId.of("hdr.ipv6.dst_addr"),
                        myUSid.toOctets(), 64)
                .build();
        action = PiAction.builder()
                .withId(PiActionId.of("IngressPipeImpl.srv6_end"))
                .build();
        myStationRule = Utils.buildFlowRule(
                 deviceId, appId, tableId, match, action);
        flowRuleService.applyFlowRules(myStationRule);

        if (myUDX != null) {
            match = PiCriterion.builder()
                .matchLpm(
                        PiMatchFieldId.of("hdr.ipv6.dst_addr"),
                        myUDX.toOctets(), 64)
                .build();
            action = PiAction.builder()
                .withId(PiActionId.of("IngressPipeImpl.srv6_end_dx6"))
                .build();
            myStationRule = Utils.buildFlowRule(
                 deviceId, appId, tableId, match, action);
            flowRuleService.applyFlowRules(myStationRule);
        }
    }

    /*
     * Insert a uA instruction 
     */
    public void insertUARule(DeviceId routerId, Ip6Address uAInstruction,
                                    Ip6Address nextHopIpv6, MacAddress nextHopMac) {
        log.info("Adding a uAInstruction on {}...", routerId);

        final String uATableId = "IngressPipeImpl.srv6_localsid_table";
        final String uAActionName = "IngressPipeImpl.srv6_usid_ua";

        final String xconnTableId = "IngressPipeImpl.xconnect_table";
        final String xconnActionName = "IngressPipeImpl.xconnect_act";

        final int mask = 64;

        PiCriterion match = PiCriterion.builder()
                .matchLpm(
                        PiMatchFieldId.of("hdr.ipv6.dst_addr"),
                        uAInstruction.toOctets(),
                        mask)
                .build();

        PiAction action = PiAction.builder()
                    .withId(PiActionId.of(uAActionName))
                    .withParameter(new PiActionParam(
                            // Action param name.
                            PiActionParamId.of("next_hop"),
                            // Action param value.
                            nextHopIpv6.toOctets()))
                    .build();

        flowRuleService.applyFlowRules(Utils
                 .buildFlowRule(routerId, appId, uATableId, match, action));


        match = PiCriterion.builder()
                    .matchLpm(
                                PiMatchFieldId.of("local_metadata.ua_next_hop"),
                                nextHopIpv6.toOctets(),
                                mask)
                    .build();

        action = PiAction.builder()
                    .withId(PiActionId.of(xconnActionName))
                    .withParameter(new PiActionParam(
                                    PiActionParamId.of("next_hop"),
                                    nextHopMac.toBytes()))
                    .build();

        flowRuleService.applyFlowRules(Utils
                    .buildFlowRule(routerId, appId, xconnTableId, match, action));
    }    



    /**
     * Insert a micro SID encap insert policy that will inject an IPv6 in IPv6 header for
     * packets destined to destIp.
     *
     * @param deviceId     device ID
     * @param destIp       target IP address for the SRv6 policy
     * @param prefixLength prefix length for the target IP
     * @param segmentList  list of SRv6 SIDs that make up the path
     */
    public void insertSrv6InsertRule(DeviceId deviceId, Ip6Address destIp, int prefixLength,
                                     List<Ip6Address> segmentList) {

        String tableId = "IngressPipeImpl.srv6_encap";
        Ip6Address myUSid= getMyUSid(deviceId);
        
        PiCriterion match = PiCriterion.builder()
                .matchLpm(PiMatchFieldId.of("hdr.ipv6.dst_addr"), destIp.toOctets(), prefixLength)
                .build();

        List<PiActionParam> actionParams = Lists.newArrayList();

        PiActionParamId paramId = PiActionParamId.of("src_addr");
        PiActionParam param = new PiActionParam(paramId, myUSid.toOctets());
        actionParams.add(param);

        for (int i = 0; i < segmentList.size()-1; i++) {
            paramId = PiActionParamId.of("s" + (i + 1));
            param = new PiActionParam(paramId, segmentList.get(i).toOctets());
            actionParams.add(param);
        }

        PiAction action = PiAction.builder()
                .withId(PiActionId.of("IngressPipeImpl.usid_encap_" + (segmentList.size() - 1)))
                .withParameters(actionParams)
                .build();

        final FlowRule rule = Utils.buildFlowRule(
                deviceId, appId, tableId, match, action);

        flowRuleService.applyFlowRules(rule);
    }

    /**
     * Remove all SRv6 transit insert polices for the specified device.
     *
     * @param deviceId device ID
     */
    public void clearSrv6InsertRules(DeviceId deviceId) {
        String tableId = "IngressPipeImpl.srv6_encap";

        FlowRuleOperations.Builder ops = FlowRuleOperations.builder();
        stream(flowRuleService.getFlowEntries(deviceId))
                .filter(fe -> fe.appId() == appId.id())
                .filter(fe -> fe.table().equals(PiTableId.of(tableId)))
                .forEach(ops::remove);
        flowRuleService.apply(ops.build());
    }

    // ---------- END METHODS TO COMPLETE ----------------

    //--------------------------------------------------------------------------
    // EVENT LISTENERS
    //
    // Events are processed only if isRelevant() returns true.
    //--------------------------------------------------------------------------

    /**
     * Listener of device events.
     */
    public class InternalDeviceListener implements DeviceListener {

        @Override
        public boolean isRelevant(DeviceEvent event) {
            switch (event.type()) {
                case DEVICE_ADDED:
                case DEVICE_AVAILABILITY_CHANGED:
                    break;
                default:
                    // Ignore other events.
                    return false;
            }
            // Process only if this controller instance is the master.
            final DeviceId deviceId = event.subject().id();
            return mastershipService.isLocalMaster(deviceId);
        }

        @Override
        public void event(DeviceEvent event) {
            final DeviceId deviceId = event.subject().id();
            if (deviceService.isAvailable(deviceId)) {
                // A P4Runtime device is considered available in ONOS when there
                // is a StreamChannel session open and the pipeline
                // configuration has been set.
                mainComponent.getExecutorService().execute(() -> {
                    log.info("{} event! deviceId={}", event.type(), deviceId);

                    setUpMyUSidTable(event.subject().id());
                });
            }
        }
    }


    //--------------------------------------------------------------------------
    // UTILITY METHODS
    //--------------------------------------------------------------------------

    /**
     * Sets up SRv6 My SID table on all devices known by ONOS and for which this
     * ONOS node instance is currently master.
     */
    private synchronized void setUpAllDevices() {
        // Set up host routes
        stream(deviceService.getAvailableDevices())
                .map(Device::id)
                .filter(mastershipService::isLocalMaster)
                .forEach(deviceId -> {
                    log.info("*** SRV6 - Starting initial set up for {}...", deviceId);
                    this.setUpMyUSidTable(deviceId);
                });
    }

    /**
     * Returns the Srv6 config for the given device.
     *
     * @param deviceId the device ID
     * @return Srv6  device config
     */
    private Optional<Srv6DeviceConfig> getDeviceConfig(DeviceId deviceId) {
        Srv6DeviceConfig config = networkConfigService.getConfig(deviceId, Srv6DeviceConfig.class);
        return Optional.ofNullable(config);
    }
    
    /**
     * Returns Srv6 uSID for the given device.
     *
     * @param deviceId the device ID
     * @return SID for the device
     */
    private Ip6Address getMyUSid(DeviceId deviceId) {
        return getDeviceConfig(deviceId)
                .map(Srv6DeviceConfig::myUSid)
                .orElseThrow(() -> new RuntimeException(
                        "Missing myUSid config for " + deviceId));
    }

    /**
     * Returns Srv6 uDX for the given device.
     *
     * @param deviceId the device ID
     * @return uDX for the device
     */
    private Ip6Address getMyUDX(DeviceId deviceId) {
        return getDeviceConfig(deviceId)
                .map(Srv6DeviceConfig::myUDX)
                .orElse(null);
    }
}
