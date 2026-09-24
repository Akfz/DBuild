package v.akfz.db.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Included only in dev jar, not in prod. Requires dbuild plugin. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface DevOnly {}