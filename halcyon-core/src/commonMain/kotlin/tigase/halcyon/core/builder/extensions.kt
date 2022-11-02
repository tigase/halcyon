package tigase.halcyon.core.builder

import tigase.halcyon.core.xmpp.modules.BindModule
import tigase.halcyon.core.xmpp.modules.BindModuleConfig
import tigase.halcyon.core.xmpp.modules.auth.SASL2Module
import tigase.halcyon.core.xmpp.modules.auth.SASL2ModuleConfig
import tigase.halcyon.core.xmpp.modules.auth.SASLModule
import tigase.halcyon.core.xmpp.modules.auth.SASLModuleConfig
import tigase.halcyon.core.xmpp.modules.caps.EntityCapabilitiesModule
import tigase.halcyon.core.xmpp.modules.caps.EntityCapabilitiesModuleConfig
import tigase.halcyon.core.xmpp.modules.discovery.DiscoveryModule
import tigase.halcyon.core.xmpp.modules.discovery.DiscoveryModuleConfiguration
import tigase.halcyon.core.xmpp.modules.presence.PresenceModule
import tigase.halcyon.core.xmpp.modules.presence.PresenceModuleConfig
import tigase.halcyon.core.xmpp.modules.roster.RosterModule
import tigase.halcyon.core.xmpp.modules.roster.RosterModuleConfiguration

fun ConfigurationBuilder.bind(cfg: BindModuleConfig.() -> Unit) =
	this.install(BindModule, configuration = cfg)

fun ConfigurationBuilder.sasl(cfg: SASLModuleConfig.() -> Unit) =
	this.install(SASLModule, configuration = cfg)

fun ConfigurationBuilder.sasl2(cfg: SASL2ModuleConfig.() -> Unit) =
	this.install(SASL2Module, configuration = cfg)

fun ConfigurationBuilder.discovery(cfg: DiscoveryModuleConfiguration.() -> Unit) =
	this.install(DiscoveryModule, configuration = cfg)

fun ConfigurationBuilder.capabilities(cfg: EntityCapabilitiesModuleConfig.() -> Unit) =
	this.install(EntityCapabilitiesModule, configuration = cfg)

fun ConfigurationBuilder.presence(cfg: PresenceModuleConfig.() -> Unit) =
	this.install(PresenceModule, configuration = cfg)

fun ConfigurationBuilder.roster(cfg: RosterModuleConfiguration.() -> Unit) =
	this.install(RosterModule, configuration = cfg)

