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
import tigase.halcyon.core.connector.socket.DnsResolver.SrvRecord
import tigase.halcyon.core.logger.LoggerFactory
import tigase.halcyon.core.utils.Lock
import kotlin.concurrent.AtomicReference

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
class DnsResolver {

    private val log = LoggerFactory.logger("tigase.halcyon.core.connector.socket.DnsResolver")
    private val lock = Lock();
    private val resolvers = listOf(DnsResolverInternal(true), DnsResolverInternal(false))

    fun resolve(domain: String, completionHandler: (Result<List<SrvRecord>>) -> Unit) {
        var counter = resolvers.count();
        val results = mutableListOf<SrvRecord>();
        val callback: (Result<List<SrvRecord>>) -> Unit = { result ->
            val finished = this.lock.withLock {
                result.getOrNull()?.let { results += it }
                counter -= 1;
                counter == 0
            }
            log.fine { "received results ${result.getOrNull()} for domain $domain, finished: $finished" }
            if (finished) {
                if (results.isEmpty() && result.isFailure) {
                    completionHandler.invoke(result);
                } else {
                    completionHandler.invoke(Result.success(results.shuffled().sortedBy {
                        if (it.directTls) {
                            0
                        } else {
                            1
                        }
                    }));
                }
            }
        };
        resolvers.forEach { resolver ->
            resolver.resolve(domain, callback)
        }
    }

    class SrvRecord(val port: UInt, val weight: UInt, val priority: UInt, val target: String, val directTls: Boolean) {

        companion object {

            private val log = LoggerFactory.logger("tigase.halcyon.core.connector.socket.SrvRecord")
            fun parse(rdata: UByteArray, directTls: Boolean): SrvRecord? {
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

                return SrvRecord(port, weight, priority, target, directTls)
            }
        }

    }
}

@OptIn(ExperimentalForeignApi::class)
class DnsResolverInternal(val directTls: Boolean) {

	private val log = LoggerFactory.logger("tigase.halcyon.core.connector.socket.DnsResolver")
	private val lock = Lock();
	private var callback: ((Result<List<SrvRecord>>) -> Unit)? = null
	//private var sdRef: DNSServiceRefVar = memScoped { alloc<DNSServiceRefVar>(); }
	private var results: AtomicReference<List<SrvRecord>> =
		AtomicReference<List<SrvRecord>>(emptyList<SrvRecord>())
	private var stableRef: StableRef<DnsResolverInternal>? = null;
	private var queue = dispatch_queue_create("dns_resolver_queue", null)
	private var domain: String = ""
    private var serviceName: String = if (directTls) { "_xmpps-client._tcp." } else { "_xmpp-client._tcp." }
    private var sdRef: DNSServiceRef? = null
    private var readSource: NSObject? = null;

	fun resolve(domain: String, completionHandler: (Result<List<SrvRecord>>) -> Unit) {
		stableRef = StableRef.create(this)
		this.domain = domain
		this.callback = completionHandler
		results.value = emptyList()
        log.finest("Resolving SRV records for domain ${domain}, service: $serviceName")

        //this.freeze();
		val result = memScoped {
			val sdRef = alloc<DNSServiceRefVar>()
			val result = DNSServiceQueryRecord(
				sdRef.ptr,
				kDNSServiceFlagsReturnIntermediates,
				kDNSServiceInterfaceIndexAny.toUInt(),
				serviceName + domain,
				kDNSServiceType_SRV.toUShort(),
				kDNSServiceClass_IN.toUShort(), staticCFunction(::QueryRecordCallback),
				stableRef?.asCPointer()
			)
            this@DnsResolverInternal.sdRef = sdRef.value;
			return@memScoped result;
		}
		if (result == kDNSServiceErr_NoError) {
			val dnsSocket = DNSServiceRefSockFD(sdRef)
			if (dnsSocket == -1) {
				failed("could not allocate DNS socket!")
				return
			}
			log.finest("using dns socket: " + dnsSocket)

			//val stableRef = this.stableRef
			readSource = dispatch_source_create(
				DISPATCH_SOURCE_TYPE_READ,
				handle = dnsSocket.toULong(),
				mask = 0u,
				queue = queue
			)//dispatch_get_main_queue());
			val block: () -> Unit = {
				log.finest("processing result for resolver: $this..")
				val res = DNSServiceProcessResult(sdRef)
				log.finest("result processed: " + res)
				if (res != kDNSServiceErr_NoError) {
					this.failed(res);
					//stableRef?.get()?.failed(res);
					//this.fail();
				}
			}
			//block.freeze();
			dispatch_source_set_event_handler(readSource, block)
			dispatch_source_set_cancel_handler(readSource) {
				DNSServiceRefDeallocate(sdRef)
			}
			//this.freeze();
			dispatch_resume(readSource)
		} else {
			DNSServiceRefDeallocate(sdRef)
			failed("DNSServiceQueryRecord returned error: " + result);
		}
	}

	fun failed(errorCode: Int) {
		this.failed("DNS resolution error: $errorCode");
	}
	
	private fun failed(reason: String) {
        log.finest(reason);
		lock.withLock {
			results.value = emptyList()
			callback?.invoke(Result.failure(DnsException(message = "DNS resolution failed! Reason: " + reason)))
            completed()
		}
	}

	fun succeeded() {
		lock.withLock {
			val results = this.results.value.sortedBy { it.priority }
			callback?.invoke(Result.success(results))
			//callback = null;
			completed()
		}
	}

    // call only from the lock!!
    private fun completed() {
        log.finest { "releasing DNSResolver for $serviceName$domain" }
        readSource?.let { dispatch_source_cancel(it) }
        readSource = null
        sdRef?.let { DNSServiceRefDeallocate(it) }
        sdRef = null;
        stableRef?.dispose()
        stableRef = null;
    }

	fun addRecord(record: SrvRecord) {
		lock.withLock {
			val records = ArrayList<SrvRecord>()
			records += results.value
			records += record
			results.value = records
		}
	}
    
}

class DnsException(message: String) : Exception(message = message)

@OptIn(ExperimentalForeignApi::class)
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
    val log = LoggerFactory.logger("tigase.halcyon.core.connector.socket.DnsResolver")
	val resolver: DnsResolverInternal = context!!.asStableRef<DnsResolverInternal>()
		.get()

	if ((flags and kDNSServiceFlagsAdd) == 0.toUInt()) {
		return
	}

    log.finest { "QueryRecordCallback errorCode: ${errorCode}, resolver: $resolver" }

	when (errorCode) {
		kDNSServiceErr_NoError -> {
			if (rrtype != kDNSServiceType_SRV.toUShort()) {
				resolver.failed(errorCode)
				return
			}
			rdata?.reinterpret<UInt8Var>()
				?.readBytes(rdlen.toInt())
				?.toUByteArray()
				?.let { srvdata ->
					DnsResolver.SrvRecord.parse(srvdata, resolver.directTls)
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

		else -> resolver.failed(errorCode)
	}
}
