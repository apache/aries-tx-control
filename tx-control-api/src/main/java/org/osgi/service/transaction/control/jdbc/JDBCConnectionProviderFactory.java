/*
 * Copyright (c) OSGi Alliance (2016). All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.osgi.service.transaction.control.jdbc;

import java.sql.Driver;
import java.util.Map;
import java.util.Properties;

import javax.sql.DataSource;
import javax.sql.XADataSource;

import org.osgi.service.jdbc.DataSourceFactory;

/**
 * A factory for creating JDBCConnectionProvider instances
 * <p>
 * This factory can be used if the {@link JDBCConnectionProvider} should not be
 * a public service, for example to protect a username/password.
 */
public interface JDBCConnectionProviderFactory {

	/**
	 * The property used to determine whether XA enlistment is enabled for this
	 * resource provider
	 */
	public static final String	XA_ENLISTMENT_ENABLED		= "osgi.xa.enabled";

	/**
	 * The property used to determine whether local enlistment is enabled for
	 * this resource provider
	 */
	public static final String	LOCAL_ENLISTMENT_ENABLED	= "osgi.local.enabled";

	/**
	 * The property used to determine whether connection pooling is enabled for
	 * this resource provider
	 */
	public static final String	CONNECTION_POOLING_ENABLED	= "osgi.connection.pooling.enabled";

	/**
	 * The property used to set the maximum amount of time that the pool should
	 * wait for a connection
	 */
	public static final String	CONNECTION_TIMEOUT			= "osgi.connection.timeout";

	/**
	 * The property used to set the maximum amount of time that connections in
	 * the pool should remain idle before being closed
	 */
	public static final String	IDLE_TIMEOUT				= "osgi.idle.timeout";

	/**
	 * The property used to set the maximum amount of time that connections in
	 * the pool should remain open
	 */
	public static final String	CONNECTION_LIFETIME			= "osgi.connection.lifetime";

	/**
	 * The property used to set the minimum number of connections that should be
	 * held in the pool
	 */
	public static final String	MIN_CONNECTIONS				= "osgi.connection.min";

	/**
	 * The property used to set the maximum number of connections that should be
	 * held in the pool
	 */
	public static final String	MAX_CONNECTIONS				= "osgi.connection.max";

	/**
	 * The property used to set the maximum number of connections that should be
	 * held in the pool
	 */
	public static final String	USE_DRIVER					= "osgi.use.driver";

	/**
	 * Create a private {@link JDBCConnectionProvider} using a
	 * DataSourceFactory.
	 * 
	 * @param dsf
	 * @param jdbcProperties The properties to pass to the
	 *            {@link DataSourceFactory} in order to create the underlying
	 *            {@link DataSource}
	 * @param resourceProviderProperties Configuration properties to pass to the
	 *            JDBC Resource Provider runtime
	 * @return A {@link JDBCConnectionProvider} that can be used in transactions
	 */
	JDBCConnectionProvider getProviderFor(DataSourceFactory dsf,
			Properties jdbcProperties,
			Map<String,Object> resourceProviderProperties);

	/**
	 * Create a private {@link JDBCConnectionProvider} using an existing
	 * {@link DataSource}.
	 * 
	 * @param ds
	 * @param resourceProviderProperties Configuration properties to pass to the
	 *            JDBC Resource Provider runtime
	 * @return A {@link JDBCConnectionProvider} that can be used in transactions
	 */
	JDBCConnectionProvider getProviderFor(DataSource ds,
			Map<String,Object> resourceProviderProperties);

	/**
	 * Create a private {@link JDBCConnectionProvider} using an existing
	 * {@link Driver}.
	 * 
	 * @param driver
	 * @param resourceProviderProperties Configuration properties to pass to the
	 *            JDBC Resource Provider runtime
	 * @return A {@link JDBCConnectionProvider} that can be used in transactions
	 */
	JDBCConnectionProvider getProviderFor(Driver driver,
			Map<String,Object> resourceProviderProperties);

	/**
	 * Create a private {@link JDBCConnectionProvider} using an existing
	 * {@link XADataSource}.
	 * 
	 * @param ds
	 * @param resourceProviderProperties Configuration properties to pass to the
	 *            JDBC Resource Provider runtime
	 * @return A {@link JDBCConnectionProvider} that can be used in transactions
	 */
	JDBCConnectionProvider getProviderFor(XADataSource ds,
			Map<String,Object> resourceProviderProperties);

}
