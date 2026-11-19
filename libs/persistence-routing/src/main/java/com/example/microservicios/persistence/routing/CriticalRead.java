package com.example.microservicios.persistence.routing;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a read operation as critical, forcing read-your-writes consistency by routing
 * execution directly to PRIMARY instead of a potentially lagging replica.
 * Restricted strictly by architectural whitelist.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CriticalRead {
}
