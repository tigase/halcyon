package tigase.halcyon.core.builder

import kotlin.test.*
import tigase.halcyon.core.xmpp.modules.BindModule
import tigase.halcyon.core.xmpp.modules.PingModule
import tigase.halcyon.core.xmpp.modules.StreamFeaturesModule
import tigase.halcyon.core.xmpp.modules.auth.SASL2Module
import tigase.halcyon.core.xmpp.modules.auth.SASLModule
import tigase.halcyon.core.xmpp.modules.caps.EntityCapabilitiesModule
import tigase.halcyon.core.xmpp.modules.commands.CommandsModule
import tigase.halcyon.core.xmpp.modules.discovery.DiscoveryModule
import tigase.halcyon.core.xmpp.modules.mam.MAMModule
import tigase.halcyon.core.xmpp.modules.mix.MIXModule
import tigase.halcyon.core.xmpp.modules.pubsub.PubSubModule
import tigase.halcyon.core.xmpp.modules.roster.RosterModule

class TopoSortTest {

    @Test
    fun test_sort() {
        val result = listOf(
            Item(EntityCapabilitiesModule, {
            }),
            Item(CommandsModule, {}),
            Item(MIXModule, {})
        ).extendForDependencies()

        assertTrue(
            result.indexOfFirst { it.provider == DiscoveryModule } <
                result.indexOfFirst { it.provider == EntityCapabilitiesModule }
        )
        assertTrue(
            result.indexOfFirst { it.provider == StreamFeaturesModule } <
                result.indexOfFirst { it.provider == EntityCapabilitiesModule }
        )

        assertTrue(
            result.indexOfFirst { it.provider == DiscoveryModule } <
                result.indexOfFirst { it.provider == CommandsModule }
        )

        assertTrue(
            result.indexOfFirst { it.provider == RosterModule } <
                result.indexOfFirst { it.provider == MIXModule }
        )
        assertTrue(
            result.indexOfFirst { it.provider == PubSubModule } <
                result.indexOfFirst { it.provider == MIXModule }
        )
        assertTrue(
            result.indexOfFirst { it.provider == MAMModule } <
                result.indexOfFirst { it.provider == MIXModule }
        )
    }

    @Test
    fun test_sort2() {
        val result = listOf(
            Item(BindModule, {}),
            Item(PingModule, {}),
            Item(SASL2Module, {}),
            Item(SASLModule, {}),
            Item(MIXModule, {})
        ).shuffled()
            .extendForDependencies()

        assertTrue(
            result.indexOfFirst { it.provider == DiscoveryModule } <
                result.indexOfFirst { it.provider == SASL2Module }
        )

        assertTrue(
            result.indexOfFirst { it.provider == RosterModule } <
                result.indexOfFirst { it.provider == MIXModule }
        )
        assertTrue(
            result.indexOfFirst { it.provider == PubSubModule } <
                result.indexOfFirst { it.provider == MIXModule }
        )
        assertTrue(
            result.indexOfFirst { it.provider == MAMModule } <
                result.indexOfFirst { it.provider == MIXModule }
        )
    }

    @Test
    fun deep_extend_for_dependencies() {
        val result = listOf(Item(Module1, {})).extendForDependencies()

        assertEquals(4, result.size, "Not all depend modules are added.")

        assertTrue(
            result.indexOfFirst { it.provider == Module4 } <
                result.indexOfFirst { it.provider == Module3 }
        )
        assertTrue(
            result.indexOfFirst { it.provider == Module3 } <
                result.indexOfFirst { it.provider == Module2 }
        )
        assertTrue(
            result.indexOfFirst { it.provider == Module2 } <
                result.indexOfFirst { it.provider == Module1 }
        )

        assertNotNull(assertNotNull(result.find { it.provider == Module1 }).configuration)
        assertNull(assertNotNull(result.find { it.provider == Module2 }).configuration)
        assertNull(assertNotNull(result.find { it.provider == Module3 }).configuration)
        assertNull(assertNotNull(result.find { it.provider == Module4 }).configuration)
    }

    @Test
    fun deep_extend_for_dependencies_two_start_modules() {
        val result = listOf(Item(Module1, {}), Item(Module4, {})).extendForDependencies()

        assertEquals(4, result.size, "Not all depend modules are added.")

        assertTrue(
            result.indexOfFirst { it.provider == Module4 } <
                result.indexOfFirst { it.provider == Module3 }
        )
        assertTrue(
            result.indexOfFirst { it.provider == Module3 } <
                result.indexOfFirst { it.provider == Module2 }
        )
        assertTrue(
            result.indexOfFirst { it.provider == Module2 } <
                result.indexOfFirst { it.provider == Module1 }
        )

        assertNotNull(assertNotNull(result.find { it.provider == Module1 }).configuration)
        assertNull(assertNotNull(result.find { it.provider == Module2 }).configuration)
        assertNull(assertNotNull(result.find { it.provider == Module3 }).configuration)
        assertNotNull(assertNotNull(result.find { it.provider == Module4 }).configuration)
    }
}
