package v.akfz.db.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Prevents the annotated class from being compiled into the final release JAR.
 */
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.CLASS)
public @interface DontCompile {

    /**
     * If {@code true}, the class will still be compiled during test runs (e.g. JUnit or IDE test tasks).
     * If {@code false}, the class will be stripped in all compilation environments.
     */
    boolean value() default true;
}