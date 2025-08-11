package org.tradelite;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;

import static org.mockito.Mockito.mockStatic;

class ApplicationTest {

    @Test
    void main() {
        try (var mock = mockStatic(SpringApplication.class)) {
            mock.when(() -> SpringApplication.run(Application.class, new String[]{}))
                    .thenReturn(null);

            Application.main(new String[]{});

            mock.verify(() -> SpringApplication.run(Application.class, new String[]{}));
        }
    }
}
