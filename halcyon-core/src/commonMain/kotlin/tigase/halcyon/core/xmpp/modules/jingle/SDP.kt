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

import kotlinx.datetime.Clock
import tigase.halcyon.core.logger.LoggerFactory
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import tigase.halcyon.core.xmpp.nextUIDLongs
import kotlin.jvm.JvmStatic

enum class SDPDirection {
	incoming,
	outgoing
}

object Bundle {

	fun parse(el: Element): List<String>? =
		if (el.name == "group" && el.xmlns == "urn:xmpp:jingle:apps:grouping:0") {
			el.getChildren("content").mapNotNull { it.attributes["name"] }.takeIf { it.isNotEmpty() }
		} else {
			null
		}

	fun toElement(bundle: List<String>?, onElement: (Element) -> Unit) {
		toElementOrNull(bundle)?.let(onElement);
	}

	fun toElementOrNull(bundle: List<String>?): Element? {
		 return bundle?.let {
			 if (bundle.isEmpty()) {
				 null
			 } else {
				 toElement(bundle);
			 }
		 }
	}

	fun toElement(bundle: List<String>): Element {
		return element("group") {
			xmlns = "urn:xmpp:jingle:apps:grouping:0"
			attribute("semantics", "BUNDLE")
			bundle.forEach { name ->
				element("content") {
					attribute("name", name)
				}
			}
		}
	}
}


class SDP(val id: String, val contents: List<Content>, val bundle: List<String>) {

	enum class StreamType {
		inactive,
		sendonly,
		recvonly,
		sendrecv;

		companion object {
			val values = enumValues<StreamType>();
			val SDP_LINES = run {
				val result = HashMap<String, StreamType>()
				for (v in values) {
					result.put("a=${v.name}", v);
				}
				result
			}
			fun from(lines: List<String>): StreamType? {
				return lines.firstOrNull { SDP_LINES.containsKey(it) }?.let { return SDP_LINES[it] }
			}
		}

		fun senders(localRole: Content.Creator): Content.Senders = when (this) {
			inactive -> Content.Senders.none
			sendrecv -> Content.Senders.both
			sendonly -> when (localRole) {
				Content.Creator.initiator -> Content.Senders.initiator
				Content.Creator.responder -> Content.Senders.responder
			}
			recvonly -> when (localRole) {
				Content.Creator.responder -> Content.Senders.initiator
				Content.Creator.initiator -> Content.Senders.responder
			}
		}
	}

	constructor(contents: List<Content>, bundle: List<String>) : this("${Clock.System.now().epochSeconds}", contents, bundle) {
	}

	fun applyDiff(action: Jingle.ContentAction, diff: SDP): SDP {
		return when (action) {
			Jingle.ContentAction.Add, Jingle.ContentAction.Accept -> SDP(id, contents + diff.contents, diff.bundle);
			Jingle.ContentAction.Modify -> {
				var contents = contents.toMutableList();
				for (diffed in diff.contents) {
					val idx = contents.indexOfFirst { it.name == diffed.name };
					if (idx >= 0) {
						val newContent = Content(contents[idx].creator, diffed.senders, diffed.name, diffed.description, contents[idx].transports);
						contents[idx] = newContent;
					}
				}
				SDP(id, contents.toList(), diff.bundle)
			}
			Jingle.ContentAction.Remove -> {
				val removed = diff.contents.map { it.name }.toSet();
				return SDP(id, contents.filterNot { removed.contains(it.name) }, diff.bundle)
			}
		}
	}

	fun diff(oldSdp: SDP): Map<Jingle.ContentAction,SDP> {
		val results = mutableMapOf<Jingle.ContentAction,SDP>()

		val oldContentNames = oldSdp.contents.map { it.name }
		val newContentNames = this.contents.map { it.name }

		oldSdp.contents.filterNot { newContentNames.contains(it.name) }.takeUnless { it.isEmpty() }?.let {
			results[Jingle.ContentAction.Remove] = SDP(id, it, bundle);
		}

		contents.filterNot { oldContentNames.contains(it.name) }.takeUnless { it.isEmpty() }?.let {
			results[Jingle.ContentAction.Add] = SDP(id, it, bundle);
		}

		fun oldContentByName(name: String): Content? {
			return oldSdp.contents.filter { it.name == name }.firstOrNull();
		}

		contents.mapNotNull { newContent ->
			val oldContent = oldContentByName(newContent.name)?: return@mapNotNull null;
			if ((oldContent.senders ?: Content.Senders.both) == (newContent.senders
					?: Content.Senders.both)) {
				return@mapNotNull null
			}
			Content(newContent.creator, newContent.senders, newContent.name, newContent.description?.cloneForModify(), emptyList());
		}.takeUnless { it.isEmpty() }?.let {
			results[Jingle.ContentAction.Modify] = SDP(id, it, bundle);
		}

		return results;
	}

	fun toString(sid: String, localRole: Content.Creator, direction: SDPDirection): String {
		val lines: MutableList<String> = mutableListOf("v=0", "o=- $sid $id IN IP4 0.0.0.0", "s=-", "t=0 0")
		if (bundle.isNotEmpty()) {
			val t = listOf("a=group:BUNDLE")
			lines += ((t + bundle).joinToString(" "))
		}

		lines += contents.map { it.toSDP(localRole, direction) }

		return lines.joinToString("\r\n") + "\r\n"
	}

	companion object {

		val log = LoggerFactory.logger("tigase.halcyon.core.xmpp.modules.jingle.SDP")

		@JvmStatic
		fun parse(sdp: String, creatorProvider: (String) -> Content.Creator, localRole: Content.Creator): Pair<SDP, String>? {
			val parts = sdp.dropLast(2)
				.split("\r\nm=")
			val media = parts.drop(1)
				.map { "m=$it" }
			val sessionLines = parts.get(0)
				.split("\r\n")
			val sessionLine = sessionLines.first { it.startsWith("o=") }
				.split(" ")
			if (sessionLine.size > 3) {
				val sid = sessionLine[1]
				val id = sessionLine[2]

				val groupParts = sessionLines.firstOrNull { it.startsWith("a=group:BUNDLE ") }
					?.split(" ") ?: listOf("")
				val bundle = if (groupParts.get(0) == "a=group:BUNDLE") {
					groupParts.drop(1)
				} else {
					emptyList()
				}

				log.finest("got session with id=$id and sid=$sid and bundle=$bundle")

				val contents = media.map { Content.parse(it, creatorProvider, localRole) }
				log.finest("contents: $contents")

				return Pair(SDP(id, contents, bundle), sid)
			}
			return null
		}
	}

}

fun Content.Companion.parse(sdp: String, creatorProvider: (String) -> Content.Creator, localRole: Content.Creator): Content {
	val log = LoggerFactory.logger("tigase.halcyon.core.xmpp.modules.jingle.Content")
	log.finest("parsing sdp: $sdp")

	val lines = sdp.split("\r\n")
	val line = lines.get(0)
		.split(" ")
	val mediaName = line.get(0)
		.drop(2)
	var name = mediaName

	lines.firstOrNull { it.startsWith("a=mid:") }
		?.drop(6)
		?.let {
			name = it
		}

	val pwd = lines.firstOrNull { it.startsWith("a=ice-pwd:") }
		?.drop("a=ice-pwd:".length)
	val ufrag = lines.firstOrNull { it.startsWith("a=ice-ufrag:") }
		?.drop("a=ice-ufrag:".length)

	val creator = creatorProvider(name)

	val payloads = line.subList(3, line.size)
		.map { id ->
			Payload
			var prefix = "a=rtpmap:$id "
			val l = lines.firstOrNull { it.startsWith(prefix) }
				?.drop(prefix.length)
				?.split("/")
			prefix = "a=fmtp:$id "
			val params = lines.firstOrNull { it.startsWith(prefix) }
				?.drop(prefix.length)
				?.split(";")
				?.map { it.split("=") }
				?.map {
					Payload.Parameter(
						it.get(0), if (it.size > 1) {
							it.get(1)
						} else {
							""
						}
					)
				} ?: emptyList()
			prefix = "a=rtcp-fb:$id "
			val rtcpFb = lines.filter { it.startsWith(prefix) }
				.map {
					it.drop(prefix.length)
						.split(" ")
				}
				.map {
					Payload.RtcpFeedback(
						it[0], if (it.size > 1) {
							it[1]
						} else {
							null
						}
					)
				}
			val clockrate = l?.get(1)
				?.toInt()
			val channels = (if ((l?.size ?: 0) > 2) {
				l?.get(2)
					?.toInt()
			} else {
				1
			}) ?: 1
			Payload(id.toInt(), channels, clockrate, name = l?.get(0), parameters = params, rtcpFeedbacks = rtcpFb)
		}

	val encryptions = lines.filter { it.startsWith("a=crypto:") }
		.map { it.split(" ") }
		.filter { it.size > 3 }
		.map {
			Encryption(
				it[0], it[1], if (it.size > 3) {
					it[3]
				} else {
					null
				}, it[2]
			)
		}
	val hdrExts = HdrExt.parse(lines)
	val ssrcs = SSRC.parse(lines)
	val ssrcGroups = SSRCGroup.parse(lines)
	val rtcpMux = lines.indexOf("a=rtcp-mux") >= 0
	val description = Description(mediaName, null, payloads, null, encryptions, rtcpMux, ssrcs, ssrcGroups, hdrExts)

	val senders: Content.Senders? = SDP.StreamType.from(lines)?.senders(localRole);

	val candidates = lines.filter { it.startsWith("a=candidate:") }
		.map { Candidate.parse(it) }
		.filterNotNull()
	val setupStr = lines.firstOrNull { it.startsWith("a=setup:") }
		?.drop("a=setup:".length)
		?.let { Fingerprint.Setup.valueOf(it) }
	val fingerprint = setupStr?.let { setup ->
		lines.filter { it.startsWith("a=fingerprint:") }
			.map {
				it.drop("a=fingerprint:".length)
					.split(" ")
			}
			.filter { it.size >= 2 }
			.map { Fingerprint(it[0], it[1], setup) }
	}
		?.firstOrNull()
	val transport = Transport(ufrag, pwd, candidates, fingerprint)

	return Content(creator, senders, name, description, listOf(transport))
}

fun Content.toSDP(localRole: Content.Creator, direction: SDPDirection): String {
	val lines = mutableListOf<String>()
	description?.let {
		lines += "m=${it.media} 1 ${
			if (it.encryption.isEmpty() && transports.map { it.fingerprint == null }.filterNotNull().isEmpty()) {
				"RTP/AVPF"
			} else {
				"RTP/SAVPF"
			}
		} ${
			it.payloads.map { it.id.toString() }
				.joinToString(" ")
		}"
	}
	lines += "c=IN IP4 0.0.0.0"

	if (description?.media == "audio" || description?.media == "video") {
		lines += "a=rtcp:1 IN IP4 0.0.0.0"
	}

	transports.firstOrNull()
		?.let {
			it.ufrag?.let { lines += "a=ice-ufrag:$it" }
			it.pwd?.let { lines += "a=ice-pwd:$it" }
			it.fingerprint?.let {
				lines += listOf("a=fingerprint:${it.hash} ${it.value}", "a=setup:${it.setup.name}")
			}
		}

	lines += "a=${(senders ?: Content.Senders.both).streamType(localRole, direction).name}"
	lines += "a=mid:$name"
	lines += "a=ice-options:trickle"

	description?.let {
		if (it.media == "audio" || it.media == "video") {
			if (it.rtcpMux) {
				lines += "a=rtcp-mux"
			}
			lines += it.encryption.map { it.toSDP() }
		}
		lines += it.payloads.flatMap { it.toSDP() }

		lines += it.hdrExts.map { it.toSDP() }
		lines += it.ssrcGroups.map { it.toSDP() }
		lines += it.ssrcs.flatMap { it.toSDP() }
	}

	transports.firstOrNull()
		?.let {
			lines += it.candidates.map { it.toSDP() }
		}

	return lines.joinToString("\r\n")
}

fun Candidate.toSDP(): String {
	val expType = type ?: Candidate.CandidateType.host
	var sdp =
		"a=candidate:$foundation $component ${protocolType.name.uppercase()} $priority $ip $port typ ${expType.name}"
	if (expType != Candidate.CandidateType.host) {
		relAddr?.let { addr ->
			relPort?.let { port ->
				sdp += " raddr $addr rport $port"
			}
		}
	}

	if (protocolType == Candidate.ProtocolType.tcp) {
		tcpType?.let { sdp += " tcptype $it" }
	}

	sdp += " generation $generation"
	return sdp
}

fun Candidate.Companion.parse(line: String): Candidate? {
	val parts = line.drop("a=candidate:".length)
		.split(" ")
	if (parts.size >= 10) {
		val foundation = parts[0]
		val component = parts[1]
		val protocolType = Candidate.ProtocolType.valueOf(parts[2].lowercase())
		val priority = parts[3].toInt()
		val port = parts[5].toInt()
		val type = Candidate.CandidateType.valueOf(parts[7])
		val ip = parts[4]

		var relAddr: String? = null
		var relPort: Int? = null
		var generation: Int? = null
		var tcptype: String? = null

		var i = 8
		while (parts.size >= i + 2) {
			val key = parts[i]
			val value = parts[i + 1]
			val oldI = i
			when (key) {
				"tcptype" -> tcptype = value
				"generation" -> generation = value.toInt()
				"raddr" -> relAddr = value
				"rport" -> relPort = value.toInt()
				else -> {
					i += 1
				}
			}
			if (oldI == i) {
				i += 2
			}
		}
		return generation?.let {
			Candidate(
				component,
				foundation,
				it,
				nextUIDLongs(),
				ip,
				0,
				port,
				priority,
				protocolType,
				relAddr,
				relPort,
				type,
				tcptype
			)
		}
	}
	return null
}

fun Encryption.toSDP() =
	sessionParams?.let { "a=crypto:$tag $cryptoSuite $keyParams $it" } ?: "a=crypto:$tag $cryptoSuite $keyParams"

fun Payload.toSDP(): List<String> {
	var line = "a=rtpmap:$id $name/$clockrate"
	if (channels > 1) {
		line += "/$channels"
	}
	val lines = mutableListOf(line)
	if (parameters.isNotEmpty()) {
		lines += "a=fmtp:$id ${
			parameters.map { "${it.name}=${it.value}" }
				.joinToString(";")
		}"
	}
	lines += rtcpFeedbacks.map {
		val type = it.type
		it.subtype?.let { subtype -> "a=rtcp-fb:$id $type $subtype" } ?: "a=rtcp-fb:$id $type"
	}
	return lines
}

fun HdrExt.toSDP(): String = "a=extmap:$id $uri"

fun HdrExt.Companion.parse(lines: List<String>): List<HdrExt> {
	val hdrExtLines = lines.filter { it.startsWith("a=extmap:") }
		.map { it.drop("a=extmap:".length) }
	return hdrExtLines.map { it.split(" ") }
		.filter { it.size > 1 && !it[0].contains("/") }
		.map { HdrExt(it[0], it[1], Description.Senders.Both) }
}

fun SSRCGroup.toSDP(): String = "a=ssrc-group:$semantics ${sources.joinToString(" ")}"

fun SSRCGroup.Companion.parse(lines: List<String>): List<SSRCGroup> {
	val ssrcGroupLines = lines.filter { it.startsWith("a=ssrc-group:") }
		.map { it.drop("a=ssrc-group:".length) }
	return ssrcGroupLines.map { it.split(" ") }
		.filter { it.size >= 2 }
		.map { SSRCGroup(it[0], it.drop(1)) }
}

fun SSRC.toSDP(): List<String> = parameters.map { "a=ssrc:$ssrc ${it.toSDP()}" }

fun SSRC.Companion.parse(lines: List<String>): List<SSRC> {
	val ssrcLines = lines.filter { it.startsWith("a=ssrc:") }
	val ssrcs = ssrcLines.map { it.drop("a=ssrc:".length) }
		.map {
			it.split(" ")
				.first()
		}
		.distinct()
	return ssrcs.map {
		val prefix = "a=ssrc:$it "
		val params = ssrcLines.filter { it.startsWith(prefix) }
			.map {
				it.drop(prefix.length)
					.split(":")
			}
			.filter { it[0].isNotEmpty() }
			.map {
				SSRC.Parameter(
					it[0], if (it.size == 1) {
						null
					} else {
						it.drop(1)
							.joinToString(":")
					}
				)
			}
		SSRC(it, params)
	}
}

fun SSRC.Parameter.toSDP(): String = value?.let { "$name:$value" } ?: name