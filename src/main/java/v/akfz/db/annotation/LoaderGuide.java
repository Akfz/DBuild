package v.akfz.db.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Method body is copied into the generated initializer for the listed loaders.
 * Processed only by Javac; ignored by ECJ.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface LoaderGuide {
    String[] value();
}