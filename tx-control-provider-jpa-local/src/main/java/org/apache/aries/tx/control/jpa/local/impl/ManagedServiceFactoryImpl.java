/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIESOR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.aries.tx.control.jpa.local.impl;

import static java.lang.Integer.MAX_VALUE;
import static java.util.Arrays.asList;
import static java.util.Optional.ofNullable;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static javax.persistence.spi.PersistenceUnitTransactionType.RESOURCE_LOCAL;
import static org.osgi.service.jdbc.DataSourceFactory.JDBC_DATABASE_NAME;
import static org.osgi.service.jdbc.DataSourceFactory.JDBC_DATASOURCE_NAME;
import static org.osgi.service.jdbc.DataSourceFactory.JDBC_DESCRIPTION;
import static org.osgi.service.jdbc.DataSourceFactory.JDBC_NETWORK_PROTOCOL;
import static org.osgi.service.jdbc.DataSourceFactory.JDBC_PASSWORD;
import static org.osgi.service.jdbc.DataSourceFactory.JDBC_PORT_NUMBER;
import static org.osgi.service.jdbc.DataSourceFactory.JDBC_ROLE_NAME;
import static org.osgi.service.jdbc.DataSourceFactory.JDBC_SERVER_NAME;
import static org.osgi.service.jdbc.DataSourceFactory.JDBC_URL;
import static org.osgi.service.jdbc.DataSourceFactory.JDBC_USER;
import static org.osgi.service.jdbc.DataSourceFactory.OSGI_JDBC_DRIVER_CLASS;

import java.util.Arrays;
import java.util.Collection;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ManagedServiceFactoryImpl implements ManagedServiceFactory {

	static final Logger LOG = LoggerFactory.getLogger(ManagedServiceFactoryImpl.class);
	
	static final String DSF_TARGET_FILTER = "aries.dsf.target.filter";
	static final String EMF_BUILDER_TARGET_FILTER = "aries.emf.builder.target.filter";
	static final String JDBC_PROP_NAMES = "aries.jdbc.property.names";
	static final List<String> JDBC_PROPERTIES = asList(JDBC_DATABASE_NAME, JDBC_DATASOURCE_NAME,
			JDBC_DESCRIPTION, JDBC_NETWORK_PROTOCOL, JDBC_PASSWORD, JDBC_PORT_NUMBER, JDBC_ROLE_NAME, JDBC_SERVER_NAME,
			JDBC_URL, JDBC_USER);
	static final String JPA_PROP_NAMES = "aries.jpa.property.names";

	private final Map<String, LifecycleAware> managedInstances = new ConcurrentHashMap<>();

	private final BundleContext context;

	public ManagedServiceFactoryImpl(BundleContext context) {
		this.context = context;
	}

	@Override
	public String getName() {
		return "Aries JPAEntityManagerProvider (Local only) service";
	}

	@Override
	public void updated(String pid, Dictionary<String, ?> properties) throws ConfigurationException {

		Map<String, Object> propsMap = new HashMap<>();

		Enumeration<String> keys = properties.keys();
		while (keys.hasMoreElements()) {
			String key = keys.nextElement();
			propsMap.put(key, properties.get(key));
		}

		Properties jdbcProps = getJdbcProps(pid, propsMap);
		Map<String, Object> jpaProps = getJPAProps(pid, propsMap);

		try {
			LifecycleAware worker;
			if(propsMap.containsKey(OSGI_JDBC_DRIVER_CLASS) ||
					propsMap.containsKey(DSF_TARGET_FILTER)) {
				worker = new ManagedJPADataSourceSetup(context, pid, jdbcProps, jpaProps, propsMap);
			} else {
				if(!jdbcProps.isEmpty()) {
					LOG.warn("The configuration {} contains raw JDBC configuration, but no osgi.jdbc.driver.class or aries.dsf.target.filter properties. No DataSourceFactory will be used byt this bundle, so the JPA provider must be able to directly create the datasource, and these configuration properties will likely be ignored. {}",
								pid, jdbcProps.stringPropertyNames());
				}
				worker = new ManagedJPAEMFLocator(context, pid, jpaProps, propsMap, null);
			}
			ofNullable(managedInstances.put(pid, worker)).ifPresent(LifecycleAware::stop);
			worker.start();
		} catch (InvalidSyntaxException e) {
			LOG.error("The configuration {} contained an invalid target filter {}", pid, e.getFilter());
			throw new ConfigurationException(DSF_TARGET_FILTER, "The target filter was invalid", e);
		}
	}

	public void stop() {
		managedInstances.values().forEach(LifecycleAware::stop);
	}

	@SuppressWarnings("unchecked")
	private Properties getJdbcProps(String pid, Map<String, Object> properties) throws ConfigurationException {

		Object object = properties.getOrDefault(JDBC_PROP_NAMES, JDBC_PROPERTIES);
		Collection<String> propnames;
		if (object instanceof String) {
			propnames = Arrays.asList(((String) object).split(","));
		} else if (object instanceof String[]) {
			propnames = Arrays.asList((String[]) object);
		} else if (object instanceof Collection) {
			propnames = (Collection<String>) object;
		} else {
			LOG.error("The configuration {} contained an invalid list of JDBC property names", pid, object);
			throw new ConfigurationException(JDBC_PROP_NAMES,
					"The jdbc property names must be a String+ or comma-separated String");
		}

		Properties p = new Properties();

		propnames.stream().filter(properties::containsKey)
				.forEach(s -> p.setProperty(s, String.valueOf(properties.get(s))));

		return p;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> getJPAProps(String pid, Map<String, Object> properties) throws ConfigurationException {
		
		Object object = properties.getOrDefault(JPA_PROP_NAMES, new AllCollection());
		Collection<String> propnames;
		if (object instanceof String) {
			propnames = Arrays.asList(((String) object).split(","));
		} else if (object instanceof String[]) {
			propnames = Arrays.asList((String[]) object);
		} else if (object instanceof Collection) {
			propnames = (Collection<String>) object;
		} else {
			LOG.error("The configuration {} contained an invalid list of JPA property names", pid, object);
			throw new ConfigurationException(JDBC_PROP_NAMES,
					"The jpa property names must be empty, a String+, or a comma-separated String list");
		}
		
		Map<String, Object> result = properties.keySet().stream()
			.filter(propnames::contains)
			.collect(toMap(identity(), properties::get));
		
		result.putIfAbsent("javax.persistence.transactionType", RESOURCE_LOCAL.name());
		
		return result;
	}

	@Override
	public void deleted(String pid) {
		ofNullable(managedInstances.remove(pid))
			.ifPresent(LifecycleAware::stop);
	}
	
	private static class AllCollection implements Collection<String> {

		@Override
		public int size() {
			return MAX_VALUE;
		}

		@Override
		public boolean isEmpty() {
			return false;
		}

		@Override
		public boolean contains(Object o) {
			return true;
		}

		@Override
		public Iterator<String> iterator() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Object[] toArray() {
			throw new UnsupportedOperationException();
		}

		@Override
		public <T> T[] toArray(T[] a) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean add(String e) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean remove(Object o) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			return true;
		}

		@Override
		public boolean addAll(Collection<? extends String> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void clear() {
			throw new UnsupportedOperationException();
		}
		
	}
}
