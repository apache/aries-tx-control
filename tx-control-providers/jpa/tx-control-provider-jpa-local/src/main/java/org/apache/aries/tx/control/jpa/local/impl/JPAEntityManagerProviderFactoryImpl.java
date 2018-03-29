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

import static java.util.Optional.ofNullable;
import static javax.persistence.spi.PersistenceUnitTransactionType.RESOURCE_LOCAL;
import static org.osgi.service.transaction.control.jpa.JPAEntityManagerProviderFactory.LOCAL_ENLISTMENT_ENABLED;
import static org.osgi.service.transaction.control.jpa.JPAEntityManagerProviderFactory.TRANSACTIONAL_DB_CONNECTION;
import static org.osgi.service.transaction.control.jpa.JPAEntityManagerProviderFactory.XA_ENLISTMENT_ENABLED;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import javax.persistence.EntityManagerFactory;
import javax.persistence.spi.PersistenceUnitTransactionType;
import javax.sql.DataSource;

import org.apache.aries.tx.control.jpa.common.impl.AbstractJPAEntityManagerProvider;
import org.apache.aries.tx.control.jpa.common.impl.DelayedJPAEntityManagerProvider;
import org.apache.aries.tx.control.jpa.common.impl.InternalJPAEntityManagerProviderFactory;
import org.apache.aries.tx.control.jpa.common.impl.JPADataSourceHelper;
import org.apache.aries.tx.control.jpa.common.impl.ScopedConnectionDataSource;
import org.osgi.service.jpa.EntityManagerFactoryBuilder;
import org.osgi.service.transaction.control.TransactionControl;
import org.osgi.service.transaction.control.TransactionException;
import org.osgi.service.transaction.control.jdbc.JDBCConnectionProvider;

import com.zaxxer.hikari.HikariDataSource;

public class JPAEntityManagerProviderFactoryImpl implements InternalJPAEntityManagerProviderFactory {

	@Override
	public AbstractJPAEntityManagerProvider getProviderFor(EntityManagerFactoryBuilder emfb, Map<String, Object> jpaProperties,
			Map<String, Object> resourceProviderProperties) {
		Map<String, Object> jpaPropsToUse = jpaProperties == null ? new HashMap<>() : new HashMap<>(jpaProperties);
		Map<String, Object> resourceProviderPropertiesToUse = resourceProviderProperties == null ? new HashMap<>() : new HashMap<>(resourceProviderProperties);
		checkEnlistment(resourceProviderPropertiesToUse);
		
		if(resourceProviderPropertiesToUse.containsKey(TRANSACTIONAL_DB_CONNECTION)) {
			return delegateTransactionality(emfb, jpaPropsToUse, resourceProviderPropertiesToUse);
		}
		
		Object found = jpaPropsToUse.get("javax.persistence.dataSource");
		
		if(found == null) {
			found = jpaPropsToUse.get("javax.persistence.nonJtaDataSource");
		}
		
		if(found == null) {
			throw new IllegalArgumentException("No datasource was found when checking the javax.persistence.dataSource and javax.persistence.nonJtaDataSource.");
		}
		
		DataSource unpooled;
		if(found instanceof DataSource) {
			unpooled = (DataSource) found;
		} else {
			throw new IllegalArgumentException("The object found when checking the javax.persistence.dataSource and javax.persistence.nonJtaDataSource properties was not a DataSource.");
		}
		
		DataSource toUse = JPADataSourceHelper.poolIfNecessary(resourceProviderPropertiesToUse, unpooled);
		jpaPropsToUse.put("javax.persistence.dataSource", toUse);
		jpaPropsToUse.put("javax.persistence.nonJtaDataSource", toUse);
		
		EntityManagerFactory emf = emfb.createEntityManagerFactory(jpaPropsToUse);
		
		validateEMF(emf);
		
		return new JPAEntityManagerProviderImpl(emf, false, () -> {
				emf.close();
				if (toUse instanceof HikariDataSource) {
					((HikariDataSource)toUse).close();
				}
			});
	}

	private AbstractJPAEntityManagerProvider delegateTransactionality(EntityManagerFactoryBuilder emfb, Map<String, Object> jpaPropsToUse, Map<String, Object> resourceProviderProperties) {
		Function<ThreadLocal<TransactionControl>, AbstractJPAEntityManagerProvider> create;
		JDBCConnectionProvider provider = (JDBCConnectionProvider) resourceProviderProperties.get(TRANSACTIONAL_DB_CONNECTION);
		
		create = tx -> {
				DataSource ds = new ScopedConnectionDataSource(
						new ScopedConnectionIsolator(provider.getResource(tx.get())));
				jpaPropsToUse.put("javax.persistence.dataSource", ds);
				jpaPropsToUse.put("javax.persistence.nonJtaDataSource", ds);
				
				EntityManagerFactory emf = emfb.createEntityManagerFactory(jpaPropsToUse);
				
				validateEMF(emf);
				
				return new JPAEntityManagerProviderImpl(emf, true, () -> {
						emf.close();
					});
			};
		return new DelayedJPAEntityManagerProvider(create, () -> {});
	}

	public AbstractJPAEntityManagerProvider getProviderFor(EntityManagerFactoryBuilder emfb, 
			Map<String, Object> jpaProperties, Map<String, Object> resourceProviderProperties, 
			Runnable onClose) {
		checkEnlistment(resourceProviderProperties);
		
		EntityManagerFactory emf = emfb.createEntityManagerFactory(jpaProperties);
		
		validateEMF(emf);
		
		return new JPAEntityManagerProviderImpl(emf, false, () -> {
			try {
				emf.close();
			} catch (Exception e) {
			}
			if (onClose != null) {
				onClose.run();
			}
		});
	}

	private void validateEMF(EntityManagerFactory emf) {
		Object o = emf.getProperties().get("javax.persistence.transactionType");
		
		PersistenceUnitTransactionType tranType;
		if(o instanceof PersistenceUnitTransactionType) {
			tranType = (PersistenceUnitTransactionType) o;
		} else if (o instanceof String) {
			tranType = PersistenceUnitTransactionType.valueOf(o.toString());
		} else {
			//TODO log this?
			tranType = RESOURCE_LOCAL;
		}
		
		if(RESOURCE_LOCAL != tranType) {
			throw new IllegalArgumentException("The supplied EntityManagerFactory is not declared RESOURCE_LOCAL");
		}
	}

	@Override
	public AbstractJPAEntityManagerProvider getProviderFor(EntityManagerFactory emf,
			Map<String, Object> resourceProviderProperties) {
		checkEnlistment(resourceProviderProperties);
		validateEMF(emf);
		
		return new JPAEntityManagerProviderImpl(emf, false, null);
	}

	private void checkEnlistment(Map<String, Object> resourceProviderProperties) {
		if (toBoolean(resourceProviderProperties, XA_ENLISTMENT_ENABLED, false)) {
			throw new TransactionException("This Resource Provider does not support XA transactions");
		} else if (!toBoolean(resourceProviderProperties, LOCAL_ENLISTMENT_ENABLED, true)) {
			throw new TransactionException(
					"This Resource Provider always enlists in local transactions as it does not support XA");
		}
	}
	
	private boolean toBoolean(Map<String, Object> props, String key, boolean defaultValue) {
		Object o =  ofNullable(props)
			.map(m -> m.get(key))
			.orElse(defaultValue);
		
		if (o instanceof Boolean) {
			return ((Boolean) o).booleanValue();
		} else if(o instanceof String) {
			return Boolean.parseBoolean((String) o);
		} else {
			throw new IllegalArgumentException("The property " + key + " cannot be converted to a boolean");
		}
	}
}
