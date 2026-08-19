package com.losmos.hrsnsauto;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"meta.instagram.access-token=",
		"meta.instagram.api-version=",
		"meta.instagram.ig-user-id=",
		"instagram.browser.automation-enabled=false"
})
class HrSnsAutoApplicationTests {

	@Test
	void contextLoads() {
	}

}
