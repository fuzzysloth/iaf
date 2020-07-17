package nl.nn.adapterframework.integration;

import java.sql.Connection;

import org.apache.logging.log4j.Logger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import nl.nn.adapterframework.jdbc.JdbcFacade;
import nl.nn.adapterframework.jdbc.dbms.IDbmsSupport;
import nl.nn.adapterframework.jdbc.dbms.IDbmsSupportFactory;
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
		final Connection connection = jdbcFacade.getConnection();
		final IDbmsSupportFactory dbmsSupportFactory = jdbcFacade.getDbmsSupportFactory();
		final IDbmsSupport dbmsSupport = dbmsSupportFactory.getDbmsSupport(connection);
		LOG.info("jdbcFacade=", jdbcFacade);
	}
}
