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
package tigase.halcyon.core.connector.socket

import kotlinx.cinterop.*
import platform.darwin.*
import tigase.halcyon.core.logger.Level
import tigase.halcyon.core.logger.LoggerFactory
import kotlin.native.concurrent.AtomicReference

class DnsResolver {

	private val log = LoggerFactory.logger("tigase.halcyon.core.connector.socket.DnsResolver")
	private var callback: ((Result<List<SrvRecord>>) -> Unit)? = null
	private var sdRef: DNSServiceRefVar = memScoped { alloc<DNSServiceRefVar>(); }
	private var results: AtomicReference<List<SrvRecord>> =
		AtomicReference<List<SrvRecord>>(emptyList<SrvRecord>())
	private val stableRef = StableRef.create(this)
	private var queue = dispatch_queue_create("dns_resolver_queue", null)
	private var domain: String = ""

	fun resolve(domain: String, completionHandler: (Result<List<SrvRecord>>) -> Unit) {
		this.domain = domain
		this.callback = completionHandler
		results.value = emptyList()
		if (log.isLoggable(Level.FINEST)) {
			log.finest("Resolving SRV records for domain ${domain}")
		}
		//this.freeze();
		val result = DNSServiceQueryRecord(
			this.sdRef.ptr,
			kDNSServiceFlagsReturnIntermediates,
			kDNSServiceInterfaceIndexAny.toUInt(),
			"_xmpp-client._tcp." + domain,
			kDNSServiceType_SRV.toUShort(),
			kDNSServiceClass_IN.toUShort(), staticCFunction(::QueryRecordCallback),
			stableRef.asCPointer()
		)
		if (result == kDNSServiceErr_NoError) {
			val dnsSocket = DNSServiceRefSockFD(this.sdRef.value)
			if (dnsSocket == -1) {
				fail()
				return
			}
			println("using dns socket: " + dnsSocket)

			val sdRef = this.sdRef
			val stableRef = this.stableRef
			val readSource = dispatch_source_create(
				DISPATCH_SOURCE_TYPE_READ,
				handle = dnsSocket.toULong(),
				mask = 0u,
				queue = queue
			)//dispatch_get_main_queue());
			val block: () -> Unit = {
				println("processing result..")
				val res = DNSServiceProcessResult(sdRef.value)
				println("result processed: " + res)
				if (res != kDNSServiceErr_NoError) {
					stableRef.get()
						.fail()
					//this.fail();
				}
			}
			//block.freeze();
			dispatch_source_set_event_handler(readSource, block)
			dispatch_source_set_cancel_handler(readSource) {
				DNSServiceRefDeallocate(sdRef.value)
			}
			//this.freeze();
			dispatch_resume(readSource)
		} else {
			fail()
		}
	}

	private fun fail() {
		failed()
	}

	fun failed() {
		results.value = emptyList()
		callback?.invoke(Result.failure(DnsException(message = "DNS resolution failed!")))
		stableRef.dispose()
	}

	fun succeeded() {
		val results = this.results.value.sortedBy { it.priority }
		callback?.invoke(Result.success(results))
		//callback = null;
		stableRef.dispose()
	}

	fun addRecord(record: SrvRecord) {
		var records = ArrayList<SrvRecord>()
		records += results.value
		records += record
		results.value = records
	}

	class SrvRecord(val port: UInt, val weight: UInt, val priority: UInt, val target: String) {

		companion object {

			private val log = LoggerFactory.logger("tigase.halcyon.core.connector.socket.SrvRecord")
			fun parse(rdata: UByteArray): SrvRecord? {
				if (rdata.size <= 7) {
					log.warning("rdata too short!")
					return null
				}
				var idx = 0
				val priority = rdata.get(idx)
					.toUShort() * 256.toUShort() + rdata.get(idx + 1)
					.toUShort()
				idx += 2
				val weight = rdata.get(idx)
					.toUShort() * 256.toUShort() + rdata.get(idx + 1)
					.toUShort()
				idx += 2
				val port = rdata.get(idx)
					.toUShort() * 256.toUShort() + rdata.get(idx + 1)
					.toUShort()
				idx += 2

				var target = ""
				while (idx < rdata.size) {
					val targetLen = rdata.get(idx)
						.toInt()
					if (targetLen == 0) {
						break
					}
					idx += 1
					val part = rdata.asByteArray()
						.toKString(startIndex = idx, endIndex = idx + targetLen)
					if (target.isEmpty()) {
						target = part
					} else {
						target = "${target}.${part}"
					}
					idx += targetLen
				}
				if (target.isEmpty()) {
					log.warning("rdata target is empty!")
					return null
				}

				return SrvRecord(port, weight, priority, target)
			}
		}

	}

}

class DnsException(message: String) : Exception(message = message)

fun QueryRecordCallback(
	@Suppress("UNUSED_PARAMETER")sdRef: DNSServiceRef?,
	flags: DNSServiceFlags,
	@Suppress("UNUSED_PARAMETER")interfaceIndex: UInt,
	errorCode: DNSServiceErrorType,
	@Suppress("UNUSED_PARAMETER")fullname: CPointer<ByteVarOf<kotlin.Byte>>?,
	rrtype: UShort,
	@Suppress("UNUSED_PARAMETER")rrclass: UShort,
	rdlen: UShort,
	rdata: COpaquePointer?,
	@Suppress("UNUSED_PARAMETER")ttl: UInt,
	context: COpaquePointer?,
) {
	val resolver: DnsResolver = context!!.asStableRef<DnsResolver>()
		.get()

	if ((flags and kDNSServiceFlagsAdd) == 0.toUInt()) {
		return
	}

	when (errorCode) {
		kDNSServiceErr_NoError -> {
			if (rrtype != kDNSServiceType_SRV.toUShort()) {
				resolver.failed()
				return
			}
			rdata?.reinterpret<UInt8Var>()
				?.readBytes(rdlen.toInt())
				?.toUByteArray()
				?.let { srvdata ->
					DnsResolver.SrvRecord.parse(srvdata)
						?.let { record ->
							resolver.addRecord(record)
						}
				}
			if ((flags and kDNSServiceFlagsMoreComing) != 0.toUInt()) {
				return
			}
			resolver.succeeded()
			return
		}

		else -> resolver.failed()
	}
}
