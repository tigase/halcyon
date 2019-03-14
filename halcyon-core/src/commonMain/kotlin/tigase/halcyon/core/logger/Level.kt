package tigase.halcyon.core.logger

enum class Level(val value: Int) {
	OFF(Int.MAX_VALUE),
	SEVERE(1000),
	WARNING(900),
	INFO(800),
	CONFIG(700),
	FINE(500),
	FINER(400),
	FINEST(300),
	ALL(Int.MIN_VALUE),
}