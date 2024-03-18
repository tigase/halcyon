package tigase.halcyon.core.modules.filter

import tigase.halcyon.core.modules.StanzaFilter
import tigase.halcyon.core.modules.StanzaFilterChain
import tigase.halcyon.core.xml.Element

class StanzaFilterProcessor {

    private val filters = mutableListOf<StanzaFilter>()

    fun addToChain(filter: StanzaFilter) {
        filters.add(filter)
    }


    fun doFilters(element: Element, result: (Result<Element?>) -> Unit) {
        try {
            if (filters.isEmpty()) doQuiet { result(Result.success(element)) } else {
                val executor = StanzaFilterExecutor(element, filters, result)
                executor.doFilter(element)
            }
        } catch (e: Throwable) {
            doQuiet { result(Result.failure(e)) }
        }
    }

}

private class StanzaFilterExecutor(
    val element: Element,
    val filters: List<StanzaFilter>,
    val result: (Result<Element?>) -> Unit
) : StanzaFilterChain {

    private var index = 0

    private var active = true


    override fun doFilter(element: Element?) {
        if (index >= filters.size) doQuiet { result(Result.success(element)) } else {
            val currentIndex = index++
            val filter = filters[currentIndex]
            filter.doFilter(element, this)
        }
    }

}

private fun doQuiet(h: () -> Unit) {
    try {
        h()
    } catch (_: Throwable) {
    }
}


