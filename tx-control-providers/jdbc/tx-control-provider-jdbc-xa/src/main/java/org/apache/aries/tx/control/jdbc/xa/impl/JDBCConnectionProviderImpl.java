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
package org.apache.aries.tx.control.jdbc.xa.impl;

import static org.apache.aries.tx.control.jdbc.common.impl.AbstractInternalJDBCConnectionProviderFactory.toBoolean;
import static org.osgi.service.transaction.control.jdbc.JDBCConnectionProviderFactory.XA_ENLISTMENT_ENABLED;

import java.sql.Connection;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.apache.aries.tx.control.jdbc.common.impl.AbstractJDBCConnectionProvider;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.transaction.control.TransactionControl;
import org.osgi.service.transaction.control.TransactionException;
import org.osgi.service.transaction.control.recovery.RecoverableXAResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JDBCConnectionProviderImpl extends AbstractJDBCConnectionProvider {

	private static final Logger LOG = LoggerFactory.getLogger(JDBCConnectionProviderImpl.class);
	
	private final UUID			uuid	= UUID.randomUUID();

	private final boolean xaEnabled;
	
	private final boolean localEnabled;
	
	private final String recoveryIdentifier;
	
	private final ServiceRegistration<RecoverableXAResource> reg;
	
	public JDBCConnectionProviderImpl(DataSource dataSource, boolean xaEnabled,
			boolean localEnabled, String recoveryIdentifier, BundleContext ctx,
			Map<String, Object> providerProperties) {
		super(dataSource);
		this.xaEnabled = xaEnabled;
		this.localEnabled = localEnabled;
		this.recoveryIdentifier = recoveryIdentifier;
		
		if(recoveryIdentifier != null) {
			if(!toBoolean(providerProperties, XA_ENLISTMENT_ENABLED, true)) {
				LOG.warn("A JDBCResourceProvider has been configured with a recovery identifier {} but it has also been configured not to use XA transactions. No recovery will be available.", recoveryIdentifier);
				reg = null;
			} else {
				reg = ctx.registerService(RecoverableXAResource.class, 
						new RecoverableXAResourceImpl(recoveryIdentifier, this, 
								(String) providerProperties.get("recovery.user"),
								(String) providerProperties.get(".recovery.password)")), 
						null);
			}
		} else {
			reg = null;
		}
	}
	
	@Override
	public Connection getResource(TransactionControl txControl)
			throws TransactionException {
		return new XAEnabledTxContextBindingConnection(txControl, this, uuid,
				xaEnabled, localEnabled, recoveryIdentifier);
	}

	@Override
	public void close() {
		if(reg != null) {
			try {
				reg.unregister();
			} catch (IllegalStateException ise) {
				LOG.debug("An exception occurred when unregistering the recovery service for {}", recoveryIdentifier);
			}
		}
		super.close();
	}
	
}
