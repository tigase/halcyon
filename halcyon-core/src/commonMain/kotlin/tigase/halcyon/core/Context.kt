package tigase.halcyon.core

interface Context {

	val eventBus: tigase.halcyon.core.eventbus.EventBus

	val sessionObject: tigase.halcyon.core.SessionObject

	val writer: tigase.halcyon.core.PacketWriter

	val modules: tigase.halcyon.core.modules.ModulesManager
}