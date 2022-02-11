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

import kotlinx.browser.window
import tigase.halcyon.core.connector.WebSocketConnector
import tigase.halcyon.core.connector.WebSocketConnectorConfig

actual class Halcyon actual constructor() : AbstractHalcyon() {

	private var intervalHandler: Int = -1

	override fun createConnector(): tigase.halcyon.core.connector.AbstractConnector {
		return WebSocketConnector(this)
	}

	init {
		this.config.connectorConfig = WebSocketConnectorConfig()
	}

	override fun reconnect(immediately: Boolean) {
		if (immediately) {
			state = State.Connecting
			startConnector()
		} else {
			window.setTimeout({
								  state = State.Connecting
								  startConnector()
							  }, 3000)
		}
	}

	override fun onConnecting() {
		console.info("Starting interval")
		super.onConnecting()
		intervalHandler = window.setInterval({ tick() }, 2000)
		console.info("Interval handler: $intervalHandler")
	}

	override fun onDisconnecting() {
		console.info("Clearing interval handler: $intervalHandler")
		window.clearInterval(intervalHandler)
		super.onDisconnecting()
	}
}
