.. _header-setting-up-client:

Setting up a client
===================

Supported platforms
-------------------

Halcyon library can be used on different platforms:

* JVM
* Android
* JavaScript

Adding client dependencies
--------------------------

To use Halcyon library in your project you have to configure repositories and add library dependency.
All versions of library are available in Tigase Maven repository:

Production
   .. code:: kotlin

      repositories {
          maven("https://maven-repo.tigase.org/repository/release/")
      }

Snapshot
   .. code:: kotlin

      repositories {
          maven("https://maven-repo.tigase.org/repository/snapshot/")
      }

At the end, you have to add dependency to ``tigase.halcyon:halcyon-core`` artifact:

.. code:: kotlin

    implementation("tigase.halcyon:halcyon-core:$halcyon_version")

Where ``$halcyon_version`` is required Halcyon version.
