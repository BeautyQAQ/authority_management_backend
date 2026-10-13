package com.liushao.vueadmin;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@org.springframework.test.context.ActiveProfiles("test")
@SpringBootTest(properties = "spring.security.user.password=test-password")
class VueadminApplicationTests {

	@Test
	void contextLoads() {
	}

}
