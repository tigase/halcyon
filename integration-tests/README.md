Integration tests
=================

To run integration tests, you need to create `local.properties` file in `integration-tests` project folder. 
File must contain user credentials (account must exists). For example:

```properties
userJID=account@xmppserver.com
password=******
```
To run tests execute `:integration-tests:check` task from root project directory:

```shell
./gradlew :integration-tests:clean :integration-tests:check
```