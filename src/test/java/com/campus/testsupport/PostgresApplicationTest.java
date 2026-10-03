package com.campus.testsupport;

import java.lang.annotation.*;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

/** Apply alongside SpringBootTest: one database per class, closed with its dependent context. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Import(PostgresTestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public @interface PostgresApplicationTest { }
