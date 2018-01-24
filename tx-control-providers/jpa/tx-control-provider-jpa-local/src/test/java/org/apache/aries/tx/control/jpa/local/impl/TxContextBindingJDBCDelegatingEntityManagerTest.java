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


import static org.mockito.Mockito.times;
import static org.osgi.service.transaction.control.TransactionStatus.ACTIVE;
import static org.osgi.service.transaction.control.TransactionStatus.COMMITTED;
import static org.osgi.service.transaction.control.TransactionStatus.NO_TRANSACTION;
import static org.osgi.service.transaction.control.TransactionStatus.ROLLED_BACK;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;

import org.apache.aries.tx.control.jpa.common.impl.AbstractJPAEntityManagerProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.osgi.service.transaction.control.TransactionContext;
import org.osgi.service.transaction.control.TransactionControl;
import org.osgi.service.transaction.control.TransactionException;
import org.osgi.service.transaction.control.TransactionStatus;

@RunWith(MockitoJUnitRunner.class)
public class TxContextBindingJDBCDelegatingEntityManagerTest {

	@Mock
	TransactionControl control;
	
	@Mock
	TransactionContext context;
	
	@Mock
	EntityManagerFactory emf;
	
	@Mock
	EntityManager rawEm;

	@Mock
	EntityTransaction et;
	
	Map<Object, Object> variables = new HashMap<>();
	
	UUID id = UUID.randomUUID();
	
	AbstractJPAEntityManagerProvider provider;
	
	TxContextBindingJDBCDelegatingEntityManager em;
	
	@Before
	public void setUp() throws SQLException {
		Mockito.when(emf.createEntityManager()).thenReturn(rawEm).thenReturn(null);
		
		Mockito.when(rawEm.getTransaction()).thenReturn(et);
		
		Mockito.doAnswer(i -> variables.put(i.getArguments()[0], i.getArguments()[1]))
			.when(context).putScopedValue(Mockito.any(), Mockito.any());
		Mockito.when(context.getScopedValue(Mockito.any()))
			.thenAnswer(i -> variables.get(i.getArguments()[0]));
		
		provider = new JPAEntityManagerProviderImpl(emf, true, null);
		
		em = new TxContextBindingJDBCDelegatingEntityManager(control, provider, id);
	}
	
	private void setupNoTransaction() {
		Mockito.when(control.getCurrentContext()).thenReturn(context);
		Mockito.when(context.getTransactionStatus()).thenReturn(NO_TRANSACTION);
	}

	private void setupActiveTransaction() {
		Mockito.when(control.getCurrentContext()).thenReturn(context);
		Mockito.when(context.supportsLocal()).thenReturn(true);
		Mockito.when(context.getTransactionStatus()).thenReturn(ACTIVE);
	}
	
	
	@Test(expected=TransactionException.class)
	public void testUnscoped() throws SQLException {
		em.isOpen();
	}

	@Test
	public void testNoTransaction() throws SQLException {
		setupNoTransaction();
		
		em.isOpen();
		em.isOpen();
		
		Mockito.verify(rawEm, times(2)).isOpen();
		Mockito.verify(rawEm, times(0)).getTransaction();
		Mockito.verify(context, times(0)).registerLocalResource(Mockito.any());
		
		Mockito.verify(context).postCompletion(Mockito.any());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testActiveTransactionCommit() throws SQLException {
		setupActiveTransaction();
		
		em.isOpen();
		em.isOpen();
		
		ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
		@SuppressWarnings("rawtypes")
		ArgumentCaptor captor2 = ArgumentCaptor.forClass(Consumer.class);
		
		Mockito.verify(rawEm, times(2)).isOpen();
		Mockito.verify(et).begin();
		Mockito.verify(et, times(0)).commit();
		Mockito.verify(et, times(0)).rollback();
		Mockito.verify(context).preCompletion(captor.capture());
		
		Mockito.verify(context).postCompletion((Consumer<TransactionStatus>) captor2.capture());
		
		captor.getValue().run();
		Mockito.verify(rawEm).flush();
		
		((Consumer<TransactionStatus>)captor2.getValue()).accept(COMMITTED);
		
		Mockito.verify(et).commit();
		Mockito.verify(et, times(0)).rollback();
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testActiveTransactionRollback() throws SQLException {
		setupActiveTransaction();
		
		em.isOpen();
		em.isOpen();
		
		ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
		@SuppressWarnings("rawtypes")
		ArgumentCaptor captor2 = ArgumentCaptor.forClass(Consumer.class);
		
		Mockito.verify(rawEm, times(2)).isOpen();
		Mockito.verify(et).begin();
		Mockito.verify(et, times(0)).commit();
		Mockito.verify(et, times(0)).rollback();
		Mockito.verify(context).preCompletion(captor.capture());
		
		Mockito.verify(context).postCompletion((Consumer<TransactionStatus>) captor2.capture());
		
		captor.getValue().run();
		Mockito.verify(rawEm).flush();
		
		((Consumer<TransactionStatus>)captor2.getValue()).accept(ROLLED_BACK);
		
		Mockito.verify(et).rollback();
		Mockito.verify(et, times(0)).commit();
	}

	@Test(expected=TransactionException.class)
	public void testClosedProvider() throws SQLException {
		setupActiveTransaction();
		
		provider.close();
		
		em.isOpen();
	}

}
