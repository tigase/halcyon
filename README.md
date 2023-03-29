Halcyon
=======

![](docs/src/asciidoc/images/logo_320.png)

> :warning: **THIS SOFTWARE IS NOT FINISHED YET**:
> This library is under active development. We cannot ensure API stability.
>
> You can use it at your own risk.

![TC build](https://tc.tigase.net/app/rest/builds/buildType:(id:Halcyon_BuildArtur)/statusIcon)
![CI Status](https://github.com/tigase/halcyon/actions/workflows/TEST.yml/badge.svg?branch=master)

# What it is

Halcyon is an [XMPP](https://xmpp.org) client library written in a [Kotlin](https://kotlinlang.org/) programming
language. It provides implementation of core of the XMPP standard and processing XML. Additionally it provides support
for many popular extensions (XEP's).

Library using [Kotlin Multiplatform](https://kotlinlang.org/docs/reference/multiplatform.html) feature to provide XMPP
library for as many platforms as possible. Currently we are focused on

* JVM
* JavaScript
* Android

In the future we want to provide native binary version. 

This repository contains source files of the library.

# Features
Halcyon implements support for [RFC 6120: Extensible Messaging and Presence Protocol (XMPP): Core](https://xmpp.org/rfcs/rfc6120.html) and [RFC 6121: Extensible Messaging and Presence Protocol (XMPP): Instant Messaging and Presence](https://xmpp.org/rfcs/rfc6121.html).

Halcyon is under active development, so list of features is changing very often.

# Quickstart

## Simplest client

Here is example of simplest client sending single message.

```kotlin
val halcyon = createHalcyon {
    auth {
        userJID = "client@tigase.net".toBareJID()
        password { "secret" }
    }
}
halcyon.connectAndWait()

halcyon.request.message {
    to = "romeo@example.net".toJID()
    "body" {
        +"Art thou not Romeo, and a Montague?"
    }
}.send()

halcyon.disconnect()
``` 
## Code snippets

There is a set of small examples of Halcyon library usage. You can find them in [codeSnippets project](./docs/codeSnippets/).

# Support

When looking for support, please first search for answers to your question in the available online channels:

* Our online documentation: [Tigase Docs](https://docs.tigase.net)
* Our online forums: [Tigase Forums](https://help.tigase.net/portal/community)
* Our online Knowledge Base [Tigase KB](https://help.tigase.net/portal/kb)

If you didn't find an answer in the resources above, feel free to submit your question to either our 
[community portal](https://help.tigase.net/portal/community) or open a [support ticket](https://help.tigase.net/portal/newticket).
 
# Compilation 

[Gradle](https://gradle.org/) Build Tool is required tool to compile library:

    ./gradlew assemble

Jar file will be stored in `./build/libs/`, JavaScript files - in `./build/js/`.

# License

<img alt="Tigase Tigase Logo" src="https://github.com/tigase/website-assets/blob/master/tigase/images/tigase-logo.png?raw=true" width="25"/> Official <a href="https://tigase.net/">Tigase</a> repository is available at: https://github.com/tigase/halcyon/.

Copyright (c) 2004 Tigase, Inc.

Licensed under AGPL License Version 3. Other licensing options available upon request.
