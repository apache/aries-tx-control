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

import static org.osgi.service.transaction.control.TransactionStatus.NO_TRANSACTION;
import static org.osgi.service.transaction.control.TransactionStatus.ROLLED_BACK;

import java.util.UUID;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceException;

import org.apache.aries.tx.control.jpa.common.impl.AbstractJPAEntityManagerProvider;
import org.apache.aries.tx.control.jpa.common.impl.EntityManagerWrapper;
import org.apache.aries.tx.control.jpa.common.impl.ScopedEntityManagerWrapper;
import org.apache.aries.tx.control.jpa.common.impl.TxEntityManagerWrapper;
import org.osgi.service.transaction.control.TransactionContext;
import org.osgi.service.transaction.control.TransactionControl;
import org.osgi.service.transaction.control.TransactionException;

public class TxContextBindingJDBCDelegatingEntityManager extends EntityManagerWrapper {

	private final TransactionControl				txControl;
	private final UUID								resourceId;
	private final AbstractJPAEntityManagerProvider	provider;

	public TxContextBindingJDBCDelegatingEntityManager(TransactionControl txControl,
			AbstractJPAEntityManagerProvider provider, UUID resourceId) {
		this.txControl = txControl;
		this.provider = provider;
		this.resourceId = resourceId;
	}

	@Override
	protected final EntityManager getRealEntityManager() {

		TransactionContext txContext = txControl.getCurrentContext();

		if (txContext == null) {
			throw new TransactionException("The resource " + provider
					+ " cannot be accessed outside of an active Transaction Context");
		}

		EntityManager existing = (EntityManager) txContext.getScopedValue(resourceId);

		if (existing != null) {
			return existing;
		}

		EntityManager toReturn;
		EntityManager toClose;

		try {
			if (txContext.getTransactionStatus() == NO_TRANSACTION) {
				toClose = provider.createEntityManager();
				toReturn = new ScopedEntityManagerWrapper(toClose);
			} else {
				toClose = provider.createEntityManager();
				toReturn = new TxEntityManagerWrapper(toClose);
				
				txContext.preCompletion(toClose::flush);
				toClose.getTransaction().begin();
			}
		} catch (Exception sqle) {
			throw new TransactionException(
					"There was a problem getting hold of a database connection",
					sqle);
		}
		
		txContext.postCompletion(s -> {
				try {
					// Make sure that the transaction ends,
					// and that the EntityManager gets the
					// right cache invalidation based on
					// commit/rollback
					if(s == ROLLED_BACK) {
						toClose.getTransaction().rollback();
					} else {
						toClose.getTransaction().commit();
					}
				} catch (PersistenceException sqle) {
					// TODO log this
				}
				try {
					toClose.close();
				} catch (PersistenceException sqle) {
					// TODO log this
				}
			});
		
		txContext.putScopedValue(resourceId, toReturn);
		
		return toReturn;
	}
}
