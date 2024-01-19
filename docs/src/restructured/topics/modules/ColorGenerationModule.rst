tigase.halcyon.core.xmpp.modules.color.ColorGenerationModule
---------------------

This module implements `XEP-0392: Consistent Color Generation <https://xmpp.org/extensions/xep-0392.html>`__.

Install and configure
^^^^^^^^^^^^^^^^^^^^^

To install or configure preinstalled Color Generation module, call function ``install`` inside Halcyon configuration
(see
:ref:`header-modules`):

.. code:: kotlin

    import tigase.halcyon.core.builder.createHalcyon
    import tigase.halcyon.core.xmpp.modules.color.*

    val halcyon = createHalcyon {
        install(ColorGenerationModule) {
            hueMin = 0f
            hueMax = 360f
            saturationMin = 0f
            saturationMax = 360f
            lightnessMin = 0f
            lightnessMax = 360f
            colorsCache = InMemoryColorsCache()
        }
    }

In configuration you can provide min and max expected values of each component of the color generated.
You can also provide cache to prevent the colour being calculated for the same JabberID each time.

Halcyon provides two caches implementations: ``InMemoryColorsCache`` (default) keeps color calculated for given JID in
memory, and ``NullColorsCache`` which is de facto no-cache mode.

Usage
^^^^^

The module provides function ``calculateColor(jid)`` what returns data class containing color in HSL representation.