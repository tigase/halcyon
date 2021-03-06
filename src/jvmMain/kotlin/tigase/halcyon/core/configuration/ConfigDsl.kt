/*
 * Tigase Halcyon XMPP Library
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
package tigase.halcyon.core.configuration

import tigase.halcyon.core.connector.socket.SocketConnectorConfig
import javax.net.ssl.TrustManager

class SocketConnectorConfigDsl(private val socketConfig: SocketConnectorConfig) {

	var port: Int by Alias(socketConfig::port)

	var trustManager: TrustManager by Alias(socketConfig::trustManager)

}

actual class ConfigDsl actual constructor(configuration: Configuration) : AbstractConfigDsl(configuration) {

	fun socketConnector(block: SocketConnectorConfigDsl.() -> Unit) {
		val cf = SocketConnectorConfig()
		configuration.connectorConfig = cf
		val cfg = SocketConnectorConfigDsl(cf)
		cfg.block()
	}

}