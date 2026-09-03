package com.team.careerfit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CareerfitApplicationTest {

    @Test
    void contextLoads() {
        // DataSource, JPA repository, configuration properties 배선을 함께 검증한다.
    }
}
