package tigase.halcyon.core.builder

import tigase.halcyon.core.modules.HalcyonModule

object TopoSort {

	fun <T> sort(allNodes: Collection<Node<T>>): List<Node<T>> {
		val result = mutableListOf<Node<T>>()
		val nodesEmptyIncom = mutableSetOf<Node<T>>()
		for (n in allNodes) {
			if (n.inEdges.size == 0) {
				nodesEmptyIncom.add(n)
			}
		}
		while (!nodesEmptyIncom.isEmpty()) {
			val n: Node<T> = nodesEmptyIncom.iterator().next()
			nodesEmptyIncom.remove(n)
			result.add(n)
			val it: MutableIterator<Edge<T>> = n.outEdges.iterator()
			while (it.hasNext()) {
				val e = it.next()
				val m = e.to
				it.remove()
				m!!.inEdges.remove(e)
				if (m.inEdges.isEmpty()) {
					nodesEmptyIncom.add(m)
				}
			}
		}
		//Check to see if all edges are removed
		for (n in allNodes) {
			if (!n.inEdges.isEmpty()) {
				throw ConfigurationException("Cycle detected. Cannot initiate modules.")
			}
		}
		return result
	}

	data class Edge<T>(val from: Node<T>?, val to: Node<T>?)
	class Node<T>(val data: T) {

		val inEdges: MutableSet<Edge<T>> = mutableSetOf()
		val outEdges: MutableSet<Edge<T>> = mutableSetOf()

		fun after(node: Node<T>): Node<T> {
			val e = Edge(node, this)
			node.outEdges.add(e)
			inEdges.add(e)
			return this
		}

		fun before(node: Node<T>): Node<T> {
			val e = Edge(this, node)
			outEdges.add(e)
			node.inEdges.add(e)
			node.inEdges.add(e)
			return this
		}

		override fun toString(): String {
			return data.toString()
		}
	}
}

inline fun Iterable<Item<out HalcyonModule, out Any>>.extendForDependencies(): List<Item<out HalcyonModule, out Any>> {

	val result = mutableSetOf<Item<out HalcyonModule, out Any>>()
	result.addAll(this)

	do {
		val toAdd2 = result.flatMap {
			it.provider.requiredModules().filter { c -> !result.any { it.provider.TYPE == c.TYPE } }.map { Item(it) }
		}.distinctBy { it.provider.TYPE }
		result.addAll(toAdd2)
	} while (toAdd2.isNotEmpty())

	val x = result.distinctBy { it.provider.TYPE }.map { TopoSort.Node(it) }

	x.forEach { node ->
		node.data.provider.requiredModules().flatMap { dep -> x.filter { it.data.provider.TYPE == dep.TYPE } }.forEach {
			node.after(it)
		}
	}
	return TopoSort.sort(x).map { it.data }
}
