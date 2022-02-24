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
package tigase.halcyon.core.xmpp.modules.discovery

import tigase.halcyon.core.xmpp.BareJID

interface NodeDetailsProvider {

	fun getIdentities(sender: BareJID?, node: String?): List<DiscoveryModule.Identity>
	fun getFeatures(sender: BareJID?, node: String?): List<String>
	fun getItems(sender: BareJID?, node: String?): List<DiscoveryModule.Item>

}