Code Snippets
=============

This project contain set of small examples of runnable code to how to use specific modules or solve common issues.

Because those code pieces tries to connect to server, you have store existing user credentials in file `local.properties` in `codeSnippets` project directory.
For example:

```properties
userJID=account@xmppserver.com
password=******
```

To run an example you can use Gradle `run` task in interesting project. For example, is you want to run `simple-client`, just execute:

```shell
./gradlew :simple-client:run
```