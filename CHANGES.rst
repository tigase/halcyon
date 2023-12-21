Release 1.0.0-SNAPSHOT (in development)
=======================================

* First public release

Dependencies
------------

* Moved bouncycastle dependency to ``halcyon-bouncycastle`` module.

Incompatible changes
--------------------

* Moved ``BouncyCastleTLSProcessor`` to separate module.

Deprecated
----------

Features added
--------------

Bugs fixed
----------

* #82: Add channel binding to SCRAM

Testing
-------


Release 1.0.0--a.7 (12-12-2023)
===============================

Bugs fixed
----------

* #82: Add channel binding to SCRAM


Release 1.0.0--a.6 (08-11-2023)
===============================

Dependencies
------------

* add dependency to BouncyCastle

Features added
--------------

* #83: Socket connector based on BouncyCastle
* #82: Add channel binding to SCRAM
* #84: XEP-0440 SASL Channel-Binding Type Capability

Bugs fixed
----------

* fixed errors in ``SocketConnector``


Release 1.0.0-a.5 (12-09-2023)
==============================

Bugs fixed
----------

* fixed problems in ``WebSocketConnector``


Release 1.0.0-a.4 (25-08-2023)
==============================

Features added
--------------

* simplified JID usage

Bugs fixed
----------

* fixed problems in ``WebSocketConnector``
* fixed SocketConnector error handling

Release 1.0.0-a.3 (17-08-2023)
==============================

Features added
--------------

* implementation of XEP-0156 (Discovering Alternative XMPP Connection Methods)


Release 1.0.0-a.2 (25-07-2023)
==============================


Dependencies
------------

* Upgrade Kotlin to 1.8.20
* Upgrade Reactive to 1.2.3
* Upgrade Kotlin Serialization to 1.5.0
* Upgrade Krypto to 4.0.0-beta3

Features added
--------------

* add ``ColorGenerationModule``
* add ``FileUploadModule``

Release 1.0.0-a.1 (29-03-2023)
==============================

* First alpha release

Incompatible changes
--------------------

* disable iOS target

Features added
--------------

* Improved events subsystem

Bugs fixed
----------

* fixed bug in CAPS verification string calculation
* fixed class cast error
