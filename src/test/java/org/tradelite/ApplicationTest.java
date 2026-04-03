package org.tradelite;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

class ApplicationTest {

    @Test
    void mainMethodSignature() throws NoSuchMethodException {
        Method mainMethod = Application.class.getMethod("main", String[].class);

        assertThat(mainMethod.getReturnType(), is(void.class));
        assertThat(Modifier.isPublic(mainMethod.getModifiers()), is(true));
        assertThat(Modifier.isStatic(mainMethod.getModifiers()), is(true));
    }

    @Test
    void constructor() {
        // Test the constructor to improve coverage
        Application application = new Application();
        assertThat(application, is(notNullValue()));
    }
}
