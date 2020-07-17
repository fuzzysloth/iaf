package nl.nn.adapterframework.integration;

import org.apache.logging.log4j.Logger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import nl.nn.adapterframework.util.LogUtil;

@RunWith(SpringJUnit4ClassRunner.class)
@ActiveProfiles("integrationtest")
@ContextConfiguration ({"classpath:/integration-test-context.xml"})
public class IntegrationTest {
	private static final Logger LOG = LogUtil.getLogger(IntegrationTest.class);

	@Test
	public void testOne() throws Exception {
		LOG.info("Hallo!");
	}
}
