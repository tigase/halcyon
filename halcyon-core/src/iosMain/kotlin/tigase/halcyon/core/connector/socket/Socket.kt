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

import kotlin.properties.Delegates
import kotlin.reflect.KProperty
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.refTo
import kotlinx.cinterop.sizeOf
import platform.darwin.ByteVar
import platform.darwin.EVFILT_READ
import platform.darwin.EV_ADD
import platform.darwin.EV_EOF
import platform.darwin.dispatch_async
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_sync
import platform.darwin.inet_pton
import platform.darwin.kevent
import platform.darwin.kqueue
import platform.posix.AF_INET
import platform.posix.F_GETFL
import platform.posix.F_SETFL
import platform.posix.IPPROTO_TCP
import platform.posix.O_NONBLOCK
import platform.posix.PF_INET
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_KEEPALIVE
import platform.posix.TCP_KEEPALIVE
import platform.posix.close
import platform.posix.connect
import platform.posix.fcntl
import platform.posix.gethostbyname
import platform.posix.read
import platform.posix.send
import platform.posix.setsockopt
import platform.posix.sockaddr
import platform.posix.sockaddr_in
import platform.posix.socket
import platform.posix.ssize_t
import tigase.halcyon.core.logger.LoggerFactory

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
class Socket {

    private val log = LoggerFactory.logger("tigase.halcyon.core.connector.socket.Socket")

    enum class State {

        disconnected,
        connecting,
        connected
    }

    private var sockfd: Int = -1
    var state: State by Delegates.observable(State.disconnected) {
            kProperty: KProperty<*>,
            oldState: State,
            newState: State
        ->
        stateCallback?.invoke(newState)
    }
    var readCallback: ((ByteArray) -> Unit)? = null
    var stateCallback: ((State) -> Unit)? = null
    private val queue = dispatch_queue_create("SocketReadQueue", null)

    fun connect(name: String, port: Int) {
        dispatch_sync(queue) {
            state = State.connecting
            memScoped {
                val hname = gethostbyname(name)
                val inetaddr = hname!!.pointed.h_addr_list!![0]!!
                val ip = "${
                    inetaddr[0].toUInt()
                        .mod(256u)
                }.${
                    inetaddr[1].toUInt()
                        .mod(256u)
                }.${
                    inetaddr[2].toUInt()
                        .mod(256u)
                }.${
                    inetaddr[3].toUInt()
                        .mod(256u)
                }"
                var addr = alloc<sockaddr_in>()
                addr.sin_family = AF_INET.convert()
                inet_pton(AF_INET, ip, addr.sin_addr.ptr)
                addr.sin_port = swapBytes(port.toUShort())

                sockfd = socket(PF_INET.toInt(), SOCK_STREAM, 0)
                if (sockfd < 0) {
                    log.finest("socket creation failed!")
                    state = State.disconnected
                } else {
                    log.finest("connecting $sockfd to $ip:${swapBytes(port.toUShort())}..")
                    if (connect(
                            sockfd,
                            addr.ptr as CValuesRef<sockaddr>?,
                            sockaddr_in.size.convert()
                        ) <
                        0
                    ) {
                        log.finest("connection failed!")
                        state = State.disconnected
                    } else {
                        setBlockingEnabled(false)
                        state = State.connected
                    }
                }
            }
        }
    }

    fun disconnect() {
        memScoped {
            close(sockfd)
            sockfd = -1
        }
    }

    fun startProcessing() {
        dispatch_async(queue) {
            log.finest("preparing kqueue...")
            memScoped {
                val kq = kqueue()
                val evSet = alloc<kevent>()
                evSet.ident = sockfd.toULong()
                evSet.filter = EVFILT_READ.toShort()
                evSet.flags = EV_ADD.toUShort()
                evSet.fflags = 0.toUInt()
                evSet.data = 0
                evSet.udata = null

                if (kevent(kq, evSet.ptr, 1, null, 0, null) < 0) {
                    log.finest("kqueue init failed!")
                } else {
                    val evList = allocArray<kevent>(32)
                    log.finest("starting processing of events...")
                    while (sockfd != -1) {
                        val nev = kevent(kq, null, 0, evList, 32, null)

                        log.finest("received $nev events from " + kq)
                        if (nev > 0) {
                            for (i in 0..(nev - 1)) {
                                val fd = evList[i].ident.toInt()
                                if (fd == sockfd) {
                                    log.finest(
                                        "event ${evList[i].filter} for $fd, isRead: ${evList[i].filter == EVFILT_READ.toShort()}"
                                    )
                                    if ((evList[i].fflags and EV_EOF.toUInt()) != 0.toUInt()) {
                                        state = State.disconnected
                                        close(fd)
                                        sockfd = -1
                                        break
                                    } else if (evList[i].filter == EVFILT_READ.toShort()) {
                                        var read: ssize_t = 0
                                        memScoped {
                                            do {
                                                val data = allocArray<ByteVar>(2048)
                                                read = read(fd, data, 2048.toULong())
                                                log.finest("read $read bytes from socket " + fd)
                                                if (read >= 0) {
                                                    readCallback?.invoke(
                                                        data.readBytes(read.toInt())
                                                    )
                                                }
                                                val socketStatus = fcntl(fd, F_GETFL)
                                                log.finest(
                                                    "socket status reported as: " + socketStatus
                                                )
                                            } while (read > 0)
                                        }
//                            if (read < 0) {
//                                close(fd);
//                                break;
//                            }
                                    }
                                }
                            }
                        }
                        log.finest("ended events processing loop")
                    }
                    log.finest("processing of events finished..")
                }
            }
        }
    }

    fun setBlockingEnabled(blocking: Boolean): Boolean {
        if (sockfd < 0) return false

        var flags = fcntl(sockfd, F_GETFL, 0)
        if (flags == -1) return false
        flags = if (blocking) (flags and O_NONBLOCK.inv()) else (flags or O_NONBLOCK)
        return (fcntl(sockfd, F_SETFL, flags) == 0)
    }

    fun setKeepAlive(timeout: Int, interval: Int) {
        memScoped {
            var optval = 0
            if ((timeout and interval) != 0) {
                optval = 1
            }
            var ret =
                setsockopt(
                    sockfd,
                    SOL_SOCKET,
                    SO_KEEPALIVE,
                    optval as CValuesRef<*>,
                    sizeOf<IntVar>().toUInt()
                )
            if (ret < 0) return

            if (optval != 0) {
                ret =
                    setsockopt(
                        sockfd,
                        IPPROTO_TCP,
                        TCP_KEEPALIVE,
                        timeout as CValuesRef<*>,
                        sizeOf<IntVar>().toUInt()
                    )
                if (ret < 0) {
                    return
                }
            }
        }
    }

    fun send(data: ByteArray) {
        if (data.size <= 0) {
            log.finest("skipping sending data, empty buffer..")
            return
        }

        memScoped {
            val result = send(sockfd, data.refTo(0), data.size.convert(), 0)
            log.finest("sent $result bytes")
            if (result < data.size) {
                log.warning(
                    "disconnecting, we failed to send ${data.size} bytes, sent only $result"
                )
                // FIXME: we got disconnected!!
                state = State.disconnected
            }
        }
    }

    private fun swapBytes(v: UShort): UShort {
        val p1 = (v.toInt() and 0xFF) shl 8
        val p2 = (v.toInt() ushr 8) and 0xFF
        return (p1 or p2).toUShort()
    }
}
