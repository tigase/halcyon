package tigase.halcyon.core.xmpp.modules.omemo

import tigase.halcyon.core.logger.LoggerFactory
import tigase.halcyon.core.xmpp.BareJID

private data class Devices(val jid: BareJID, val ids: List<Int>)

private val log = LoggerFactory.logger("tigase.halcyon.core.xmpp.modules.omemo")

private fun retrieveDevicesOfUsers(
    omemo: OMEMOModule,
    jids: List<BareJID>,
    handler: (List<Devices>) -> Unit
) {
    var c = jids.size
    val result = mutableListOf<Devices>()

    fun checkCompletion() {
        log.finest("checking if completed... " + c)
        if (c <= 0) {
            handler.invoke(result)
        }
    }

    jids.forEach { jid ->
        omemo.retrieveDevicesIds(jid).response {
            it.onSuccess {
                result += Devices(jid, it)
                --c
                checkCompletion()
            }
            it.onFailure {
                --c
                checkCompletion()
            }
        }.send()
    }
}

private fun retireveBundlesOfDevices(
    omemo: OMEMOModule,
    devices: List<Devices>,
    handler: (List<Bundle>) -> Unit
) {
    var c = devices.sumOf { it.ids.size }
    val result = mutableListOf<Bundle>()

    fun checkCompletion() {
        log.finest("checking if completed... " + c)
        if (c <= 0) {
            handler.invoke(result)
        }
    }
    devices.forEach { contact ->
        contact.ids.forEach { id ->
            omemo.retrieveBundle(contact.jid, id).response {
                it.onSuccess {
                    result += it
                    --c
                    checkCompletion()
                }
                it.onFailure {
                    --c
                    checkCompletion()
                }
            }.send()
        }
    }
}

/**
 * Retrieve all bundles of given JabberIDs.
 * @param jids list of JabberID to retrieve bundles.
 * @param handler result handler.
 */
fun OMEMOModule.retrieveBundles(jids: List<BareJID>, handler: (List<Bundle>) -> Unit) {
    retrieveDevicesOfUsers(this, jids) {
        log.finest("retrieved devices of users...")
        retireveBundlesOfDevices(this, it, handler)
    }
}
