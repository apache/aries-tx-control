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
package org.apache.aries.tx.control.jpa.xa.impl;

import static java.util.Optional.ofNullable;
import static org.apache.aries.tx.control.jpa.xa.impl.XAJPADataSourceSetup.JTA_DATA_SOURCE;
import static org.osgi.service.transaction.control.jpa.JPAEntityManagerProviderFactory.OSGI_RECOVERY_IDENTIFIER;

import java.util.Map;
import java.util.UUID;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.apache.aries.tx.control.jpa.common.impl.AbstractJPAEntityManagerProvider;
import org.apache.aries.tx.control.jpa.xa.impl.JPAEntityManagerProviderFactoryImpl.EnlistingDataSource;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.transaction.control.TransactionControl;
import org.osgi.service.transaction.control.TransactionException;
import org.osgi.service.transaction.control.recovery.RecoverableXAResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JPAEntityManagerProviderImpl extends AbstractJPAEntityManagerProvider {

	private static final Logger LOG = LoggerFactory.getLogger(JPAEntityManagerProviderImpl.class);
	
	private final UUID					uuid	= UUID.randomUUID();

	private final ThreadLocal<TransactionControl> tx;
	
	private final String recoveryIdentifier;
	
	private final ServiceRegistration<RecoverableXAResource> reg;

	public JPAEntityManagerProviderImpl(EntityManagerFactory emf, ThreadLocal<TransactionControl> tx,
			Runnable onClose, BundleContext ctx, Map<String, Object> jpaProps, 
			Map<String, Object> providerProps) {
		super(emf, onClose);
		this.tx = tx;
		
		recoveryIdentifier = (String) ofNullable(providerProps)
								.map(m -> m.get(OSGI_RECOVERY_IDENTIFIER))
								.orElse(null);
		
		if(recoveryIdentifier != null) {
			EnlistingDataSource ds = (EnlistingDataSource) jpaProps.get(JTA_DATA_SOURCE);
			reg = ctx.registerService(RecoverableXAResource.class, 
					new RecoverableXAResourceImpl(recoveryIdentifier, ds, 
							(String) providerProps.get("recovery.user"),
							(String) providerProps.get(".recovery.password)")), 
					null);
		} else {
			reg = null;
		}
	}

	@Override
	public EntityManager getResource(TransactionControl txControl) throws TransactionException {
		return new XATxContextBindingEntityManager(txControl, this, uuid, tx);
	}

	public void unregister() {
		if(reg != null) {
			try {
				reg.unregister();
			} catch (IllegalStateException ise) {
				LOG.debug("An exception occurred when unregistering the recovery service for {}", recoveryIdentifier);
			}
		}
	}
}
