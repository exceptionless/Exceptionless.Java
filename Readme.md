# Exceptionless.Java

The definition of the word exceptionless is: to be without exception. [Exceptionless](https://exceptionless.io) provides real-time error reporting for your Java apps. It organizes the gathered information into simple actionable data that will help your app become exceptionless!

## Using Exceptionless

Refer to the Exceptionless documentation here: [Exceptionless Docs](http://docs.exceptionless.io).

### Warning! This package is still in Beta and not yet published to Maven Central. If you need it right now feel free to contact us on [Discord]((https://discord.gg/6HxgFCx)) or create an [Issue](https://github.com/exceptionless/Exceptionless.Java/issues/new).

## Show me the code!

```java
class ExampleApp{
    public static void main(String[] args) {
        private static final ExceptionlessClient client =
                ExceptionlessClient.from(
                        System.getenv("EXCEPTIONLESS_SAMPLE_APP_API_KEY"),
                        System.getenv("EXCEPTIONLESS_SAMPLE_APP_SERVER_URL"));

        // Submit different events using submitXXX methods
        client.submitLog("Test log");

        // Submit custom methods using our createXXX methods
        client.submitEvent(
                EventPluginContext.from(
                        client.createLog("test-log").referenceId("test-reference-id").build()));

        // Submit async events using submitXXXAsync methods
        client.submitLogAsync("Test log");
    }
}
```

## General Data Protection Regulation

By default the Exceptionless Client will report all available metadata including potential PII data.
You can fine tune the collection of information via Data Exclusions or turning off collection completely.

Please visit the [docs](https://exceptionless.com/docs/clients/javascript/client-configuration/#general-data-protection-regulation)
for detailed information on how to configure the client to meet your requirements.

## Support

If you need help, please contact us via in-app support, [open an issue](https://github.com/exceptionless/Exceptionless.Java/issues/new) or [join our chat on Discord](https://discord.gg/6HxgFCx). We’re always here to help if you have any questions!

## Thanks

Thanks to all the people who have contributed!

[![contributors](https://contributors-img.web.app/image?repo=exceptionless/Exceptionless.Java)](https://github.com/exceptionless/Exceptionless.JavaScript/graphs/contributors)
