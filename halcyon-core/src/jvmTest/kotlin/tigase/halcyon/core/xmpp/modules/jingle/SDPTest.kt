/*
 * halcyon-core
 * Copyright (C) 2018 Tigase, Inc. (office@tigase.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */
package tigase.halcyon.core.xmpp.modules.jingle

import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class SDPTest {

    @Ignore
    @Test
    fun test() {
        val source = "v=0\r\n" +
                "o=- 1805356199272590025 3 IN IP4 127.0.0.1\r\n" +
                "s=-\r\n" +
                "t=0 0\r\n" +
                "a=group:BUNDLE 0 1\r\n" +
                "a=msid-semantic: WMS RTCmS\r\n" +
                "m=audio 9 RTP/SAVPF 111 9 102 0 8 13 110\r\n" +
                "c=IN IP4 0.0.0.0\r\n" +
                "a=rtcp:9 IN IP4 0.0.0.0\r\n" +
                "a=ice-ufrag:BPqK\r\n" +
                "a=ice-pwd:Z0zG0vtLZvLbKXjvXeTZ+QPv\r\n" +
                "a=ice-options:trickle renomination\r\n" +
                "a=fingerprint:sha-256 57:04:17:D7:BC:38:21:3D:B0:F6:5C:9C:37:1C:FE:9A:A7:CE:65:21:AF:C6:C3:DF:76:C0:65:67:1F:F0:3A:B3\r\n" +
                "a=setup:passive\r\n" +
                "a=mid:0\r\n" +
                "a=extmap:1 urn:ietf:params:rtp-hdrext:ssrc-audio-level\r\n" +
                "a=extmap:2 http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time\r\n" +
                "a=extmap:3 http://www.ietf.org/id/draft-holmer-rmcat-transport-wide-cc-extensions-01\r\n" +
                "a=extmap:4 urn:ietf:params:rtp-hdrext:sdes:mid\r\n" +
                "a=sendrecv\r\n" +
                "a=rtcp-mux\r\n" +
                "a=rtpmap:111 opus/48000/2\r\n" +
                "a=rtcp-fb:111 transport-cc\r\n" +
                "a=fmtp:111 minptime=10;useinbandfec=1\r\n" +
                "a=rtpmap:9 G722/8000\r\n" +
                "a=rtpmap:102 ILBC/8000\r\n" +
                "a=rtpmap:0 PCMU/8000\r\n" +
                "a=rtpmap:8 PCMA/8000\r\n" +
                "a=rtpmap:13 CN/8000\r\n" +
                "a=rtpmap:110 telephone-event/48000\r\n" +
                "a=ssrc:452635800 cname:RdbphLPYF760FCFi\r\n" +
                "a=ssrc:452635800 msid:RTCmS audio-365027C2-9219-44C0-8FC8-2D26600D5302\r\n" +
                "a=ssrc:452635800 mslabel:RTCmS\r\n" +
                "a=ssrc:452635800 label:audio-365027C2-9219-44C0-8FC8-2D26600D5302\r\n" +
                "m=video 9 RTP/SAVPF 96 97 39 40 98 99 106 107\r\n" +
                "c=IN IP4 0.0.0.0\r\n" +
                "a=rtcp:9 IN IP4 0.0.0.0\r\n" +
                "a=ice-ufrag:RG7W\r\n" +
                "a=ice-pwd:z4ffLGkcX4A49GgwBU0FFups\r\n" +
                "a=ice-options:trickle renomination\r\n" +
                "a=fingerprint:sha-256 57:04:17:D7:BC:38:21:3D:B0:F6:5C:9C:37:1C:FE:9A:A7:CE:65:21:AF:C6:C3:DF:76:C0:65:67:1F:F0:3A:B3\r\n" +
                "a=setup:passive\r\n" +
                "a=mid:1\r\n" +
                "a=extmap:14 urn:ietf:params:rtp-hdrext:toffset\r\n" +
                "a=extmap:2 http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time\r\n" +
                "a=extmap:13 urn:3gpp:video-orientation\r\n" +
                "a=extmap:3 http://www.ietf.org/id/draft-holmer-rmcat-transport-wide-cc-extensions-01\r\n" +
                "a=extmap:5 http://www.webrtc.org/experiments/rtp-hdrext/playout-delay\r\n" +
                "a=extmap:6 http://www.webrtc.org/experiments/rtp-hdrext/video-content-type\r\n" +
                "a=extmap:7 http://www.webrtc.org/experiments/rtp-hdrext/video-timing\r\n" +
                "a=extmap:8 http://www.webrtc.org/experiments/rtp-hdrext/color-space\r\n" +
                "a=extmap:4 urn:ietf:params:rtp-hdrext:sdes:mid\r\n" +
                "a=extmap:10 urn:ietf:params:rtp-hdrext:sdes:rtp-stream-id\r\n" +
                "a=extmap:11 urn:ietf:params:rtp-hdrext:sdes:repaired-rtp-stream-id\r\n" +
                "a=sendrecv\r\n" +
                "a=rtcp-mux\r\n" +
                "a=rtpmap:96 VP8/90000\r\n" +
                "a=rtcp-fb:96 goog-remb\r\n" +
                "a=rtcp-fb:96 transport-cc\r\n" +
                "a=rtcp-fb:96 ccm fir\r\n" +
                "a=rtcp-fb:96 nack\r\n" +
                "a=rtcp-fb:96 nack pli\r\n" +
                "a=rtpmap:97 rtx/90000\r\n" +
                "a=fmtp:97 apt=96\r\n" +
                "a=rtpmap:39 AV1/90000\r\n" +
                "a=rtcp-fb:39 goog-remb\r\n" +
                "a=rtcp-fb:39 transport-cc\r\n" +
                "a=rtcp-fb:39 ccm fir\r\n" +
                "a=rtcp-fb:39 nack\r\n" +
                "a=rtcp-fb:39 nack pli\r\n" +
                "a=rtpmap:40 rtx/90000\r\n" +
                "a=fmtp:40 apt=39\r\n" +
                "a=rtpmap:98 VP9/90000\r\n" +
                "a=rtcp-fb:98 goog-remb\r\n" +
                "a=rtcp-fb:98 transport-cc\r\n" +
                "a=rtcp-fb:98 ccm fir\r\n" +
                "a=rtcp-fb:98 nack\r\n" +
                "a=rtcp-fb:98 nack pli\r\n" +
                "a=rtpmap:99 rtx/90000\r\n" +
                "a=fmtp:99 apt=98\r\n" +
                "a=rtpmap:106 red/90000\r\n" +
                "a=rtpmap:107 rtx/90000\r\n" +
                "a=fmtp:107 apt=106\r\n" +
                "a=ssrc-group:FID 346999496 1499381982\r\n" +
                "a=ssrc:346999496 cname:RdbphLPYF760FCFi\r\n" +
                "a=ssrc:346999496 msid:RTCmS video-22BCC273-B3C3-4437-8CFE-BE42978DB6EC\r\n" +
                "a=ssrc:346999496 mslabel:RTCmS\r\n" +
                "a=ssrc:346999496 label:video-22BCC273-B3C3-4437-8CFE-BE42978DB6EC\r\n" +
                "a=ssrc:1499381982 cname:RdbphLPYF760FCFi\r\n" +
                "a=ssrc:1499381982 msid:RTCmS video-22BCC273-B3C3-4437-8CFE-BE42978DB6EC\r\n" +
                "a=ssrc:1499381982 mslabel:RTCmS\r\n" +
                "a=ssrc:1499381982 label:video-22BCC273-B3C3-4437-8CFE-BE42978DB6EC\r\n"

        val (sdp, id) = SDP.parse(source, { Content.Creator.initiator}, Content.Creator.initiator) ?: throw AssertionError();
        assertEquals(listOf("0","1"), sdp.bundle)
        val output = sdp.toString(id, Content.Creator.initiator, SDPDirection.incoming)
        assertEquals(source, output)
    }
}