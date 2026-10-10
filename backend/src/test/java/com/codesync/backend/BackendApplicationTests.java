package com.codesync.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "codesync.jwt.secret=test-only-secret-that-is-long-enough-for-hs256")
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
