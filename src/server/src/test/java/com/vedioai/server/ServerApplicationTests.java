package com.vedioai.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;


@SpringBootTest(properties = {
        "vedioai.auth.mail.delivery-mode=local-catcher",
        "vedioai.auth.mail.dispatcher-enabled=false"
})
class ServerApplicationTests {

	@Test
	void contextLoads() {
	}

}
