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
	this.modulesConfigBuilder.install(BindModule, configuration = cfg)

fun ConfigurationBuilder.sasl(cfg: SASLModuleConfig.() -> Unit) =
	this.modulesConfigBuilder.install(SASLModule, configuration = cfg)

fun ConfigurationBuilder.sasl2(cfg: SASL2ModuleConfig.() -> Unit) =
	this.modulesConfigBuilder.install(SASL2Module, configuration = cfg)

fun ConfigurationBuilder.discovery(cfg: DiscoveryModuleConfiguration.() -> Unit) =
	this.modulesConfigBuilder.install(DiscoveryModule, configuration = cfg)

fun ConfigurationBuilder.capabilities(cfg: EntityCapabilitiesModuleConfig.() -> Unit) =
	this.modulesConfigBuilder.install(EntityCapabilitiesModule, configuration = cfg)

fun ConfigurationBuilder.presence(cfg: PresenceModuleConfig.() -> Unit) =
	this.modulesConfigBuilder.install(PresenceModule, configuration = cfg)

fun ConfigurationBuilder.roster(cfg: RosterModuleConfiguration.() -> Unit) =
	this.modulesConfigBuilder.install(RosterModule, configuration = cfg)

