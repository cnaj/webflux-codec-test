# Custom webflux codec stress test

This project contains a decoder class, `ItemsDecoder`, that parses an incoming asynchronous stream of (JSON-formatted) `ItemsEntity` and extracts an asynchronous stream of decoded entity objects, `Item`.

This project was created to examine the behavior of `Jackson2Tokenizer` from the Spring framework (https://github.com/spring-projects/spring-framework).

Build and run the tests with this command:

```
mvn clean test -DitemCount=100000
```
