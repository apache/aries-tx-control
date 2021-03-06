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

import static java.util.Collections.newSetFromMap;
import static java.util.Collections.singletonMap;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyMapOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;
import static org.osgi.framework.FrameworkUtil.createFilter;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.persistence.EntityManagerFactory;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.jpa.EntityManagerFactoryBuilder;

@RunWith(MockitoJUnitRunner.class)
public class XAJPAEMFLocatorTest {
	
	private static final String PID = "some.pid";
	private static final String UNIT_NAME = "foo";
	
	private final Set<Map<String, Object>> maps = newSetFromMap(new IdentityHashMap<>());
	
	private final Semaphore getJDBCSem = new Semaphore(0);
	
	private final Semaphore closeSem = new Semaphore(0);
	
	@Mock
	BundleContext ctx;
	
	@Mock
	ServiceReference<EntityManagerFactoryBuilder> ref;

	@Mock
	EntityManagerFactoryBuilder emfb;

	@Mock
	EntityManagerFactory emf;
	
	Supplier<Map<String, Object>> propertiesProvider;

	Consumer<Map<String, Object>> closeHandler;

	private XAJPAEMFLocator locator;
	
	@Before
	public void setUp() throws InvalidSyntaxException, ConfigurationException {

		propertiesProvider = () -> {
				Map<String, Object> m = new HashMap<>();
				maps.add(m);
				getJDBCSem.release();
				return m;
			};
		
		closeHandler	 = m -> {
				if(maps.remove(m)) {
					closeSem.release();
				}
			};
			
		when(emfb.createEntityManagerFactory(anyMapOf(String.class, Object.class)))
			.thenReturn(emf);
		
		when(ctx.getService(ref)).thenReturn(emfb);
		
		when(ctx.createFilter(anyString()))
			.then(i -> createFilter(i.getArguments()[0].toString()));
			
		locator = new XAJPAEMFLocator(ctx, PID, propertiesProvider, 
				singletonMap("osgi.unit.name", UNIT_NAME), closeHandler);
	}
	
	@Test
	public void testEMFAddedRemovedThenAddedAgain() {
		
		locator.addingService(ref);
		
		assertEquals(1, getJDBCSem.availablePermits());
		assertEquals(0, closeSem.availablePermits());
		
		locator.removedService(ref, emfb);
		
		assertEquals(1, getJDBCSem.availablePermits());
		assertEquals(1, closeSem.availablePermits());
		
		locator.addingService(ref);
		
		assertEquals(2, getJDBCSem.availablePermits());
		assertEquals(1, closeSem.availablePermits());
		
		locator.removedService(ref, emfb);
		
		assertEquals(2, getJDBCSem.availablePermits());
		assertEquals(2, closeSem.availablePermits());
	}

}
