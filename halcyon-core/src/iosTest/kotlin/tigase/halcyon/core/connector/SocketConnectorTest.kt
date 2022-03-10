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
package tigase.halcyon.core.connector;

import platform.posix.sleep
import tigase.halcyon.core.AbstractHalcyon
import tigase.halcyon.core.Halcyon
import tigase.halcyon.core.configuration.PasswordCallback
import tigase.halcyon.core.connector.socket.DnsResolver
import tigase.halcyon.core.connector.socket.SocketConnectorConfig
import tigase.halcyon.core.xmpp.BareJID
import kotlin.native.concurrent.AtomicReference
import kotlin.native.concurrent.freeze
import kotlin.test.Test
import kotlin.test.assertTrue

public class SocketConnectorTest {

    @Test
    fun testDnsResolver() {
//        val worker = Worker.start();
        val resultsStore = AtomicReference<List<DnsResolver.SrvRecord>>(emptyList());
//        worker.execute(TransferMode.SAFE, {}) {
            val dnsResolver = DnsResolver();
            println("testing SRV resolution for domain tigase.org...")
            dnsResolver.resolve("tigase.org") { result ->
                result.onSuccess { records ->
                    println("got results:")
                    records.forEach {
                        println("port: " + it.port + ", target: " + it.target);
                    }
                    println("done")
                    var results = emptyList<DnsResolver.SrvRecord>()
                    results += records;
                    resultsStore.value = results.freeze();
                }.onFailure { ex ->
                    println("got exception:")
                    ex.printStackTrace();
                }
            }
//        }

        sleep(1);
        assertTrue { !resultsStore.value.isEmpty() }
    }

    @Test
    fun testConnector() {
        val client = Halcyon();
        client.configure {
            userJID = BareJID("testuser", "tigase.org");
            passwordCallback = object : PasswordCallback {
                override fun getPassword(): String = "testpassword";
            }
        }
        client.config.connectorConfig = SocketConnectorConfig();
        client.connect();
        sleep(15);
        println("connection timeout reached, checking state..");
        assertTrue { client.state == AbstractHalcyon.State.Connected }
    }
}
