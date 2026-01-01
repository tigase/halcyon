@file:Suppress("UnusedVariable", "UNUSED_VARIABLE", "UnusedParameter", "UNUSED_PARAMETER", "unused")

package tigase.halcyon.core.modules.filter

import tigase.halcyon.core.modules.StanzaFilter
import tigase.halcyon.core.modules.StanzaFilterChain
import tigase.halcyon.core.xml.Element
import tigase.halcyon.core.xml.element
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

class StanzaFilterProcessorTest {

    @Test
    fun settingUpTest() {
        val proc = StanzaFilterProcessor()
        proc.addToChain(Filter1)
        proc.addToChain(Filter2)
        proc.addToChain(Filter3)

        proc.doFilters(element("test") {}) {
            it.onSuccess { element ->
                assertNotNull(element?.getFirstChild("filter1"))
                assertNotNull(element?.getFirstChild("filter2"))
                assertNotNull(element?.getFirstChild("filter3"))
            }
            it.onFailure {
                fail()
            }
        }
    }

    @Test
    fun failedTest() {
        val proc = StanzaFilterProcessor()
        proc.addToChain(Filter1)
        proc.addToChain(FilterFail)
        proc.addToChain(Filter2)
        proc.addToChain(Filter3)

        proc.doFilters(element("test") {}) {
            it.onSuccess {
                fail()
            }
            it.onFailure {
                assertEquals("Fail", it.message)
            }
        }
    }

    @Test
    fun failInHandlerTest() {
        val proc = StanzaFilterProcessor()
        proc.addToChain(Filter1)
        proc.addToChain(Filter2)
        proc.addToChain(Filter3)

        proc.doFilters(element("test") {}) {
            it.onSuccess {
                throw RuntimeException("A")
            }
            it.onFailure {
                fail()
            }
        }
    }

}

object Filter1 : StanzaFilter {
    override fun doFilter(element: Element?, chain: StanzaFilterChain) {
        element?.add(element("filter1") {})
        chain.doFilter(element)
    }
}

object Filter2 : StanzaFilter {
    override fun doFilter(element: Element?, chain: StanzaFilterChain) {
        element?.add(element("filter2") {})
        chain.doFilter(element)
    }
}

object Filter3 : StanzaFilter {
    override fun doFilter(element: Element?, chain: StanzaFilterChain) {
        element?.add(element("filter3") {})
        chain.doFilter(element)
    }
}

object FilterFail : StanzaFilter {
    override fun doFilter(element: Element?, chain: StanzaFilterChain) {
        throw RuntimeException("Fail")
    }
}
