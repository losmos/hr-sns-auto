package com.losmos.hrsnsauto;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"meta.instagram.access-token=",
		"meta.instagram.api-version=",
		"meta.instagram.ig-user-id="
})
class HrSnsAutoApplicationTests {

	@Test
	void contextLoads() {
	}

}
