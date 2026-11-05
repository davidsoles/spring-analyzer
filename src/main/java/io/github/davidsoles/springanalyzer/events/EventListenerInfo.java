package io.github.davidsoles.springanalyzer.events;

/**
 * A method that listens for a Spring event.
 *
 * @param handler      Class#method that listens for the event
 * @param classFqn     fully qualified class name of the registered listener class
 * @param methodName   method name
 * @param declaredIn   type where the listener annotation is declared
 * @param annotation   EventListener or TransactionalEventListener
 * @param condition    SpEL condition, or an empty string
 * @param file         source file containing the annotation
 * @param line         line of the listener annotation
 * @param methodLine   line where the method starts
 */
public record EventListenerInfo(
        String handler,
        String classFqn,
        String methodName,
        String declaredIn,
        String annotation,
        String condition,
        String file,
        int line,
        int methodLine) {
}
