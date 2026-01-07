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
package tigase.halcyon.core

import platform.posix.usleep
import tigase.halcyon.core.builder.ConfigurationBuilder
import tigase.halcyon.core.connector.AbstractConnector
import tigase.halcyon.core.connector.socket.SocketConnector
import tigase.halcyon.core.logger.LoggerFactory

actual class Halcyon actual constructor(configuration: ConfigurationBuilder) : AbstractHalcyon(configuration) {

	private val log = LoggerFactory.logger("tigase.halcyon.core.Halcyon")

	init {
		// this.config.connectorConfig = SocketConnectorConfig()
	}

	override actual fun reconnect(immediately: Boolean) {
		log.finer { "Called reconnect. immediately=$immediately" }
		if (!immediately) usleep(3000u)
		state = State.Connecting
		startConnector()
	}

	override actual fun createConnector(): AbstractConnector {
		return SocketConnector(this)
	}

}