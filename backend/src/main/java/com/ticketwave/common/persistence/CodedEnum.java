package com.ticketwave.common.persistence;

/**
 * Implemented by enums whose persisted form is a short lowercase code
 * (matching a CHECK-constrained VARCHAR column) rather than the Java
 * constant name, e.g. {@code CUSTOMER -> "customer"}.
 */
public interface CodedEnum {

    String getCode();
}
