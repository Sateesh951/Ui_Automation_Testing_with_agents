package com.automation.testdata;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CaseMetadata {
    String id();
    String module();
    String description();
    String expectedResult();
    String type() default "UI";
}
