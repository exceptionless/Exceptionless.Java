# Contributing

1. Use `google-java-format` for code formatting.
2. Use wrapper classes instead of primitives. For example, use `Integer` in place of `int`. It helps us in distinguishing, when a value is not set.
3. Use `@Builder` based object construction instead of `new`.
4. Using `lombok` annotations for any new classes.
5. All hardcoded constants appear as a `private static final` variables at the top of the class.
