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
import platform.posix.*
import tigase.halcyon.core.logger.Level
import tigase.halcyon.core.logger.LoggerFactory
import kotlin.native.concurrent.AtomicReference
import kotlin.native.concurrent.freeze

class DnsResolver {
    private val log = LoggerFactory.logger("tigase.halcyon.core.connector.socket.DnsResolver")
    private var callback: ((Result<List<DnsResolver.SrvRecord>>) -> Unit)? = null;
    private var sdRef: DNSServiceRefVar = memScoped { alloc<DNSServiceRefVar>(); }
    private var results: AtomicReference<List<SrvRecord>> = AtomicReference<List<SrvRecord>>(emptyList<SrvRecord>().freeze());
    private val stableRef = StableRef.create(this);
    private var domain: String = "";

    fun resolve(domain: String, completionHandler: (Result<List<SrvRecord>>)->Unit) {
        this.domain = domain;
        this.callback = completionHandler;
        results.value = emptyList();
        if (log.isLoggable(Level.FINEST)) {
            log.finest("Resolving SRV records for domain ${domain}");
        }
        //this.freeze();
        val result = DNSServiceQueryRecord(this.sdRef.ptr, kDNSServiceFlagsReturnIntermediates, kDNSServiceInterfaceIndexAny, "_xmpp-client._tcp." + domain, kDNSServiceType_SRV.toUShort(), kDNSServiceClass_IN.toUShort(), staticCFunction(::QueryRecordCallback)!!, stableRef.asCPointer());
        if (result == kDNSServiceErr_NoError) {
            val dnsSocket = DNSServiceRefSockFD(this.sdRef.value);
            if (dnsSocket == -1) {
                fail();
                return;
            }

            log.finest("opened DNS socket: " + dnsSocket);

            var timeout = 30L;
            var remainingTime = timeout;
            var result = 0;
            var err = 0;
            var start = time(null);

            while (remainingTime > 0L) {
                var x: fd_set = memScoped { alloc<fd_set>() };
                bzero(x.ptr, sizeOf<fd_set>().toULong());
                __darwin_fd_set(dnsSocket, x.ptr);

                var tv = cValue<timeval>();
                tv.useContents {
                    tv_sec = remainingTime;
                    tv_usec = ((remainingTime - tv_usec) * 1000000).toInt();
                }

                assert(__darwin_fd_isset(dnsSocket, x.ptr) != 0);

                val sdRef = this.sdRef;
                result = select(dnsSocket+1, x.ptr, null, null, tv);
                if (result > 0) {
                    if (__darwin_fd_isset(dnsSocket, x.ptr) != 0) {
                        log.finest("processing DNS result..")
                        err = DNSServiceProcessResult(sdRef.value);
                        if (err != kDNSServiceErr_NoError) {
                            log.finest("DNS processing returned an error: " + err);
                            break;
                        }
                    }
                } else if (result == 0) {
                    log.finest("ending processing DNS results..");
                    break;
                } else {
                    err = -2;
                    break;
                }

                remainingTime = timeout - (time(null) - start);
                log.finest("remaining time: " + remainingTime)
            }
            DNSServiceRefDeallocate(sdRef.value);
            if (err == 0) {
                val results = this.results.value.sortedBy { it.priority };
                results.freeze();
                log.finest("got results for " + domain + ": " + results);
                completionHandler(Result.success(results));
            } else {
                log.finest("resolution error..");
                completionHandler(Result.failure(DnsException(message = "DNS resolution failed!")));
            }

//            println("using dns socket: " + dnsSocket);
//
//            val sdRef = this.sdRef;
//            val stableRef = this.stableRef;
//            val readSource = dispatch_source_create(DISPATCH_SOURCE_TYPE_READ, handle = dnsSocket.toULong(), mask = 0, queue = queue);//dispatch_get_main_queue());
//            val block: () -> Unit = {
//                println("processing result..");
//                val res = DNSServiceProcessResult(sdRef.value);
//                println("result processed: " + res);
//                if (res != kDNSServiceErr_NoError) {
//                    stableRef.get().fail();
//                    //this.fail();
//                }
//            }
//            block.freeze();
//            dispatch_source_set_event_handler(readSource, block);
//            dispatch_source_set_cancel_handler(readSource) {
//                DNSServiceRefDeallocate(sdRef!!.value);
//            }
//            //this.freeze();
//            dispatch_resume(readSource);
        } else {
            completionHandler(Result.failure(DnsException(message = "DNS resolution failed!" )));
        }
    }

    private fun fail() {
        failed();
    }

    fun failed() {
        results.value = emptyList();
        callback?.invoke(Result.failure(DnsException(message = "DNS resolution failed!")));
        stableRef.dispose();
    }

    fun succeeded() {
        val results = this.results.value.sortedBy { it.priority };
        callback?.invoke(Result.success(results));
        //callback = null;
        stableRef.dispose();
    }

    fun addRecord(record: SrvRecord) {
        var records = ArrayList<SrvRecord>();
        records += results.value;
        records += record;
        results.value = records.freeze();
    }
    
    class SrvRecord(val port: UInt, val weight: UInt, val priority: UInt, val target: String) {

        companion object {
            private val log = LoggerFactory.logger("tigase.halcyon.core.connector.socket.SrvRecord")
            fun parse(rdata: UByteArray): SrvRecord? {
                if (rdata.size <= 7) {
                    log.warning("rdata too short!");
                    return null;
                }
                var idx = 0;
                val priority = rdata.get(idx).toUShort() * 256.toUShort() + rdata.get(idx + 1).toUShort();
                idx += 2;
                val weight = rdata.get(idx).toUShort() * 256.toUShort() + rdata.get(idx + 1).toUShort();
                idx += 2;
                val port = rdata.get(idx).toUShort() * 256.toUShort() + rdata.get(idx + 1).toUShort();
                idx += 2;
                
                var target = "";
                while (idx < rdata.size) {
                    val targetLen = rdata.get(idx).toInt();
                    if (targetLen == 0) {
                        break;
                    }
                    idx += 1;
                    val part = rdata.asByteArray().toKString(startIndex = idx, endIndex = idx + targetLen);
                    if (target.isEmpty()) {
                        target = part;
                    } else {
                        target = "${target}.${part}";
                    }
                    idx += targetLen;
                }
                if (target.isEmpty()) {
                    log.warning("rdata target is empty!");
                    return null;
                }

                return SrvRecord(port, weight, priority, target);
            }
        }

    }

}

class DnsException(message: String): Exception(message = message) {

}

private val log = LoggerFactory.logger("tigase.halcyon.core.connector.socket.QueryRecordCallback")

fun QueryRecordCallback(sdRef: DNSServiceRef?, flags: DNSServiceFlags, interfaceIndex: UInt, errorCode: DNSServiceErrorType, fullname: CPointer<ByteVarOf<kotlin.Byte>>?, rrtype: UShort, rrclass: UShort, rdlen: UShort, rdata: COpaquePointer?, ttl: UInt, context: COpaquePointer?) {
    val resolver: DnsResolver = context!!.asStableRef<DnsResolver>().get()

    if ((flags and kDNSServiceFlagsAdd) == 0.toUInt()) {
        log.finest("no kDNSServiceFlagsAdd")
        return;
    }

    log.finest("processing with error code: " + errorCode)

    if (errorCode == kDNSServiceErr_NoError) {
        if (rrtype != kDNSServiceType_SRV.toUShort()) {
            return;
        }
        rdata?.reinterpret<UInt8Var>()?.readBytes(rdlen.toInt())?.toUByteArray()?.let { srvdata ->
            DnsResolver.SrvRecord.parse(srvdata)?.let { record ->
                resolver.addRecord(record);
            }
        }
    }
}
