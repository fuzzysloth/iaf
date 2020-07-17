package nl.nn.adapterframework.integration;

import org.apache.logging.log4j.Logger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import nl.nn.adapterframework.jdbc.JdbcFacade;
import nl.nn.adapterframework.util.LogUtil;

@RunWith(SpringJUnit4ClassRunner.class)
@ActiveProfiles("integrationtest")
@ContextConfiguration ({"classpath:/integration-test-mssql-context.xml"})
public class MSSQLIntegrationTest {
	private static final Logger LOG = LogUtil.getLogger(MSSQLIntegrationTest.class);

	@Autowired
	private JdbcFacade jdbcFacade;
	
	@Test
	public void testOne() throws Exception {
		LOG.info("jdbcFacade=", jdbcFacade);
	}
}
