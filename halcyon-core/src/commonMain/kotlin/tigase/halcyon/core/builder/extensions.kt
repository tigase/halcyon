package tigase.halcyon.core.builder

import tigase.halcyon.core.xmpp.modules.BindModule
import tigase.halcyon.core.xmpp.modules.BindModuleConfig
import tigase.halcyon.core.xmpp.modules.auth.SASL2Module
import tigase.halcyon.core.xmpp.modules.auth.SASL2ModuleConfig
import tigase.halcyon.core.xmpp.modules.auth.SASLModule
import tigase.halcyon.core.xmpp.modules.auth.SASLModuleConfig

fun ConfigurationBuilder.bind(cfg: BindModuleConfig.() -> Unit) =
	this.modulesConfigBuilder.install(BindModule, configuration = cfg)

fun ConfigurationBuilder.sasl(cfg: SASLModuleConfig.() -> Unit) =
	this.modulesConfigBuilder.install(SASLModule, configuration = cfg)

fun ConfigurationBuilder.sasl2(cfg: SASL2ModuleConfig.() -> Unit) =
	this.modulesConfigBuilder.install(SASL2Module, configuration = cfg)

