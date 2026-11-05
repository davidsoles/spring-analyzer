package io.github.davidsoles.springanalyzer;

/**
 * A place where a Spring event is published.
 *
 * @param handler      Class#method that publishes the event
 * @param classFqn     fully qualified class name
 * @param methodName   method name
 * @param expression   source expression passed to publishEvent/multicastEvent
 * @param receiver     receiver expression, e.g. "publisher" or "this.publisher"
 * @param receiverType resolved receiver type when available
 * @param confidence   "high" when the receiver is a known Spring publisher type, otherwise "name-only"
 * @param file         source file
 * @param line         line of the publish call
 * @param methodLine   line where the enclosing method starts
 */
public record EventPublisherInfo(
        String handler,
        String classFqn,
        String methodName,
        String expression,
        String receiver,
        String receiverType,
        String confidence,
        String file,
        int line,
        int methodLine) {
}
