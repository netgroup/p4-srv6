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

#include <core.p4>
#include <v1model.p4>

#include "include/header.p4"
#include "include/parser.p4"
#include "include/checksum.p4"

#define CPU_CLONE_SESSION_ID 99
#define UN_BLOCK_MASK     0xffffffff000000000000000000000000


control IngressPipeImpl (inout parsed_headers_t hdr,
                         inout local_metadata_t local_metadata,
                         inout standard_metadata_t standard_metadata) {

    action drop() {
        mark_to_drop(standard_metadata);
    }

    action set_output_port(port_num_t port_num) {
        standard_metadata.egress_spec = port_num;
    }
    action set_multicast_group(group_id_t gid) {
        standard_metadata.mcast_grp = gid;
        local_metadata.is_multicast = true;
    }

    direct_counter(CounterType.packets_and_bytes) unicast_counter; 
    table unicast {
        key = {
            hdr.ethernet.dst_addr: exact; 
        }
        actions = {
            set_output_port;
            drop;
            NoAction;
        }
        counters = unicast_counter;
        default_action = NoAction();
    }

    direct_counter(CounterType.packets_and_bytes) multicast_counter;
    table multicast {
        key = {
            hdr.ethernet.dst_addr: ternary;
        }
        actions = {
            set_multicast_group;
            drop;
        }
        counters = multicast_counter;
        const default_action = drop;
    }

    direct_counter(CounterType.packets_and_bytes) l2_firewall_counter;
    table l2_firewall {
	    key = {
	        hdr.ethernet.dst_addr: exact;
	    }
	    actions = {
	        NoAction;
	    }
    	counters = l2_firewall_counter;
    }

    action set_next_hop(mac_addr_t next_hop) {
	    hdr.ethernet.src_addr = hdr.ethernet.dst_addr;
	    hdr.ethernet.dst_addr = next_hop;
	    hdr.ipv6.hop_limit = hdr.ipv6.hop_limit - 1;
    }

    direct_counter(CounterType.packets_and_bytes) routing_v6_counter;
    table routing_v6 {
	    key = {
	        hdr.ipv6.dst_addr: lpm;
	    }
        actions = {
	        set_next_hop;
        }
        counters = routing_v6_counter;
    }

    /*
     * NDP reply table and actions.
     * Handles NDP router solicitation message and send router advertisement to the sender.
     */
    action ndp_ns_to_na(mac_addr_t target_mac) {
        hdr.ethernet.src_addr = target_mac;
        hdr.ethernet.dst_addr = IPV6_MCAST_01;
        bit<128> host_ipv6_tmp = hdr.ipv6.src_addr;
        hdr.ipv6.src_addr = hdr.ndp.target_addr;
        hdr.ipv6.dst_addr = host_ipv6_tmp;
        hdr.icmpv6.type = ICMP6_TYPE_NA;
        hdr.ndp.flags = NDP_FLAG_ROUTER | NDP_FLAG_OVERRIDE;
        hdr.ndp_option.setValid();
        hdr.ndp_option.type = NDP_OPT_TARGET_LL_ADDR;
        hdr.ndp_option.length = 1;
        hdr.ndp_option.value = target_mac;
        hdr.ipv6.next_hdr = PROTO_ICMPV6;
        standard_metadata.egress_spec = standard_metadata.ingress_port;
        local_metadata.skip_l2 = true;
    }

    direct_counter(CounterType.packets_and_bytes) ndp_reply_table_counter;
    table ndp_reply_table {
        key = {
            hdr.ndp.target_addr: exact;
        }
        actions = {
            ndp_ns_to_na;
        }
        counters = ndp_reply_table_counter;
    }

    action end_action() {}

    action end_action_un() {
        hdr.ipv6.dst_addr = (hdr.ipv6.dst_addr & UN_BLOCK_MASK) | ((hdr.ipv6.dst_addr << 16) & ~((bit<128>)UN_BLOCK_MASK));
    }

    action end_action_ua(ipv6_addr_t next_hop) {
        hdr.ipv6.dst_addr = (hdr.ipv6.dst_addr & UN_BLOCK_MASK) | ((hdr.ipv6.dst_addr << 32) & ~((bit<128>)UN_BLOCK_MASK));
        local_metadata.xconnect = true;

        local_metadata.ua_next_hop = next_hop;
    }

    action end_action_DX6() {
        hdr.ipv6.version = hdr.ipv6_inner.version;
        hdr.ipv6.traffic_class = hdr.ipv6_inner.traffic_class;
        hdr.ipv6.flow_label = hdr.ipv6_inner.flow_label;
        hdr.ipv6.payload_len = hdr.ipv6_inner.payload_len;
        hdr.ipv6.next_hdr = hdr.ipv6_inner.next_hdr;
        hdr.ipv6.hop_limit = hdr.ipv6_inner.hop_limit;
        hdr.ipv6.src_addr = hdr.ipv6_inner.src_addr;
        hdr.ipv6.dst_addr = hdr.ipv6_inner.dst_addr;

        hdr.ipv6_inner.setInvalid();
        hdr.srv6h.setInvalid();
        hdr.srv6_list[0].setInvalid();
    }

    direct_counter(CounterType.packets_and_bytes) my_sid_table_counter;
    table my_sid_table {
        key = {
            hdr.ipv6.dst_addr: lpm;
        }
        actions = {
            end_action;
            end_action_un;
            end_action_ua;
            end_action_DX6;
            NoAction;
        }
        default_action = NoAction;
        counters = my_sid_table_counter;
    }

    action xconnect_act(mac_addr_t next_hop) {
        hdr.ethernet.src_addr = hdr.ethernet.dst_addr;
        hdr.ethernet.dst_addr = next_hop;
    }

    direct_counter(CounterType.packets_and_bytes) xconnect_table_counter;
    table xconnect_table {
        key = {
            local_metadata.ua_next_hop: lpm;
        }
        actions = {
            xconnect_act;
            NoAction;
        }
        default_action = NoAction;
        counters = xconnect_table_counter;
    }

    action usid_encap_1(ipv6_addr_t src_addr, ipv6_addr_t s1) {
        hdr.ipv6_inner.setValid();

        hdr.ipv6_inner.version = 6;
        hdr.ipv6_inner.traffic_class = hdr.ipv6.traffic_class;
        hdr.ipv6_inner.flow_label = hdr.ipv6.flow_label;
        hdr.ipv6_inner.payload_len = hdr.ipv6.payload_len;
        hdr.ipv6_inner.next_hdr = hdr.ipv6.next_hdr;
        hdr.ipv6_inner.hop_limit = hdr.ipv6.hop_limit;
        hdr.ipv6_inner.src_addr = hdr.ipv6.src_addr;
        hdr.ipv6_inner.dst_addr = hdr.ipv6.dst_addr;

        hdr.ipv6.payload_len = hdr.ipv6.payload_len + 40;
        hdr.ipv6.next_hdr = PROTO_IPV6;
        hdr.ipv6.src_addr = src_addr;
        hdr.ipv6.dst_addr = s1;
    }

    action usid_encap_2(ipv6_addr_t src_addr, ipv6_addr_t s1, ipv6_addr_t s2) {
        hdr.ipv6_inner.setValid();

        hdr.ipv6_inner.version = 6;
        hdr.ipv6_inner.traffic_class = hdr.ipv6.traffic_class;
        hdr.ipv6_inner.flow_label = hdr.ipv6.flow_label;
        hdr.ipv6_inner.payload_len = hdr.ipv6.payload_len;
        hdr.ipv6_inner.next_hdr = hdr.ipv6.next_hdr;
        hdr.ipv6_inner.hop_limit = hdr.ipv6.hop_limit;
        hdr.ipv6_inner.src_addr = hdr.ipv6.src_addr;
        hdr.ipv6_inner.dst_addr = hdr.ipv6.dst_addr;

        hdr.ipv6.payload_len = hdr.ipv6.payload_len + 40 + 24;
        hdr.ipv6.next_hdr = PROTO_SRV6;
        hdr.ipv6.src_addr = src_addr;
        hdr.ipv6.dst_addr = s1;

        hdr.srv6h.setValid();
        hdr.srv6h.next_hdr = PROTO_IPV6;
        hdr.srv6h.hdr_ext_len = 0x2;
        hdr.srv6h.routing_type = 0x4;
        hdr.srv6h.segment_left = 0;
        hdr.srv6h.last_entry = 0;
        hdr.srv6h.flags = 0;
        hdr.srv6h.tag = 0;

        hdr.srv6_list[0].setValid();
        hdr.srv6_list[0].segment_id = s2;
    }

    direct_counter(CounterType.packets_and_bytes) srv6_encap_table_counter;
    table srv6_encap {
        key = {
           hdr.ipv6.dst_addr: lpm;       
        }
        actions = {
            usid_encap_1;
            usid_encap_2;
            NoAction;
        }
        default_action = NoAction;
        counters = srv6_encap_table_counter;
    }


    /*
     * ACL table  and actions.
     * Clone the packet to the CPU (PacketIn) or drop.
     */

    action clone_to_cpu() {
        clone3(CloneType.I2E, CPU_CLONE_SESSION_ID, standard_metadata);
    }

    direct_counter(CounterType.packets_and_bytes) acl_counter;
    table acl {
        key = {
            standard_metadata.ingress_port: ternary;
            hdr.ethernet.dst_addr: ternary;
            hdr.ethernet.src_addr: ternary;
            hdr.ethernet.ether_type: ternary;
            local_metadata.ip_proto: ternary;
            local_metadata.icmp_type: ternary;
            local_metadata.l4_src_port: ternary;
            local_metadata.l4_dst_port: ternary;
        }
        actions = {
            clone_to_cpu;
            drop;
        }
        counters = acl_counter;
    }

    apply {
        if (hdr.packet_out.isValid()) {
            standard_metadata.egress_spec = hdr.packet_out.egress_port;
            hdr.packet_out.setInvalid();
            exit;
        }

        if (hdr.icmpv6.isValid() && hdr.icmpv6.type == ICMP6_TYPE_NS) {
            ndp_reply_table.apply();
        }

	    if (hdr.ipv6.hop_limit == 0) {
	        drop();
	    }

	    if (l2_firewall.apply().hit) {
            switch(my_sid_table.apply().action_run) {
                end_action: {
                    // support for reduced SRH
                    if (hdr.srv6h.segment_left > 0) {
                        // set destination IP address to next segment
                        hdr.ipv6.dst_addr = local_metadata.next_srv6_sid;
                        // decrement segments left
                        hdr.srv6h.segment_left = hdr.srv6h.segment_left - 1;
                    } else {
                        // set destination IP address to next segment
                        hdr.ipv6.dst_addr = hdr.srv6_list[0].segment_id;
                    }
                }
            }
            
            if (!local_metadata.xconnect) {
                srv6_encap.apply();                       
	            routing_v6.apply();
	        } else {
                xconnect_table.apply();
            }
        }
        
	    if (!local_metadata.skip_l2) {
            if (!unicast.apply().hit) {
       	      	multicast.apply();
	        }	
	    }

        acl.apply();
    
    }
}

control EgressPipeImpl (inout parsed_headers_t hdr,
                        inout local_metadata_t local_metadata,
                        inout standard_metadata_t standard_metadata) {
    apply {
        if (standard_metadata.egress_port == CPU_PORT) {
		    hdr.packet_in.setValid();
		    hdr.packet_in.ingress_port = standard_metadata.ingress_port;		
        }

        if (local_metadata.is_multicast == true
             && standard_metadata.ingress_port == standard_metadata.egress_port) {
            mark_to_drop(standard_metadata);
        }
    }
}

V1Switch(
    ParserImpl(),
    VerifyChecksumImpl(),
    IngressPipeImpl(),
    EgressPipeImpl(),
    ComputeChecksumImpl(),
    DeparserImpl()
) main;
