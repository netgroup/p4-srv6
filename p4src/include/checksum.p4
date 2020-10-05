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

#ifndef __CHECKSUM__
#define __CHECKSUM__

control ComputeChecksumImpl(inout parsed_headers_t hdr,
                            inout local_metadata_t meta)
{
    apply {
        update_checksum(hdr.ndp.isValid(),
            {
                hdr.ipv6.src_addr,
                hdr.ipv6.dst_addr,
                hdr.ipv6.payload_len,
                8w0,
                hdr.ipv6.next_hdr,
                hdr.icmpv6.type,
                hdr.icmpv6.code,
                hdr.ndp.flags,
                hdr.ndp.target_addr,
                hdr.ndp_option.type,
                hdr.ndp_option.length,
                hdr.ndp_option.value
            },
            hdr.icmpv6.checksum,
            HashAlgorithm.csum16
        );

        update_checksum(meta.ipv4_update, 
            {
                hdr.ipv4.version,
                hdr.ipv4.ihl,
                hdr.ipv4.dscp,
                hdr.ipv4.ecn,
                hdr.ipv4.total_len,
                hdr.ipv4.identification,
                hdr.ipv4.flags,
                hdr.ipv4.frag_offset,
                hdr.ipv4.ttl,
                hdr.ipv4.protocol,
                16w0,
                hdr.ipv4.src_addr,
                hdr.ipv4.dst_addr
            }, 
            hdr.ipv4.hdr_checksum, 
            HashAlgorithm.csum16
        );

    }
}

control VerifyChecksumImpl(inout parsed_headers_t hdr,
                           inout local_metadata_t meta)
{
    apply {}
}

#endif
