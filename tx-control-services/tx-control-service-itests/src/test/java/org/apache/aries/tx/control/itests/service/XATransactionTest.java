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
package org.apache.aries.tx.control.itests.service;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;
import static org.ops4j.pax.exam.CoreOptions.when;

import java.util.concurrent.Callable;

import javax.inject.Inject;
import javax.transaction.xa.Xid;

import org.apache.aries.tx.control.itests.service.RecordingResource.Action;
import org.apache.aries.tx.control.itests.service.RecordingResource.Interaction;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.ops4j.pax.exam.util.Filter;
import org.osgi.service.transaction.control.TransactionContext;
import org.osgi.service.transaction.control.TransactionControl;
import org.osgi.service.transaction.control.TransactionRolledBackException;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class XATransactionTest {

	@Inject
	@Filter("(osgi.xa.enabled=true)")
	private TransactionControl txControl;

	@Test
	public void testXASupportAdvertised() {
		txControl.required(new Callable<Object>() {
			@Override
			public Object call() throws Exception {
				assertTrue(txControl.getCurrentContext().supportsXA());
				return null;
			}
		});
	}

	@Test
	public void testRegisterXAResource() {

		RecordingResource resource = new RecordingResource();
		
		txControl.required(new Callable<Object>() {
			@Override
			public Object call() throws Exception {
				TransactionContext context = txControl.getCurrentContext();
				context.registerXAResource(resource, null);
				return null;
			}
		});
		
		Interaction i = resource.getList().get(0);
		
		assertEquals(Action.STARTED, i.action);
		
		Xid id = i.xid;
		
		i = resource.getList().get(1);
		
		assertEquals(Action.END, i.action);
		assertEquals(id, i.xid);

		i = resource.getList().get(2);
		
		assertEquals(Action.COMMITTED_ONE_PHASE, i.action);
		assertEquals(id, i.xid);
		
		assertEquals(3, resource.getList().size());
	}

	@Test
	public void testRegisterXAResourceRollback() {
		
		RecordingResource resource = new RecordingResource();
		
		txControl.required(new Callable<Object>() {
			@Override
			public Object call() throws Exception {
				TransactionContext context = txControl.getCurrentContext();
				context.registerXAResource(resource, null);
				context.setRollbackOnly();
				return null;
			}
		});
		
		Interaction i = resource.getList().get(0);
		
		assertEquals(Action.STARTED, i.action);
		
		Xid id = i.xid;
		
		i = resource.getList().get(1);
		
		assertEquals(Action.END, i.action);
		assertEquals(id, i.xid);
		
		i = resource.getList().get(2);
		
		assertEquals(Action.ROLLED_BACK, i.action);
		assertEquals(id, i.xid);
		
		assertEquals(3, resource.getList().size());
	}

	@Test
	public void testRegisterXAResourcesTwoPhase() {
		
		RecordingResource resourceA = new RecordingResource();
		RecordingResource resourceB = new RecordingResource();
		
		txControl.required(new Callable<Object>() {
			@Override
			public Object call() throws Exception {
				TransactionContext context = txControl.getCurrentContext();
				context.registerXAResource(resourceA, null);
				context.registerXAResource(resourceB, null);
				return null;
			}
		});
		
		Interaction i = resourceA.getList().get(0);
		
		assertEquals(Action.STARTED, i.action);
		
		Xid idA = i.xid;
		
		i = resourceB.getList().get(0);
		
		assertEquals(Action.STARTED, i.action);

		Xid idB = i.xid;
		
		assertArrayEquals(idA.getGlobalTransactionId(), idB.getGlobalTransactionId());
		
		i = resourceA.getList().get(1);
		
		assertEquals(Action.END, i.action);
		assertEquals(idA, i.xid);
		
		i = resourceB.getList().get(1);
		
		assertEquals(Action.END, i.action);
		assertEquals(idB, i.xid);
		
		i = resourceA.getList().get(2);
		
		assertEquals(Action.PREPARED, i.action);
		assertEquals(idA, i.xid);

		i = resourceB.getList().get(2);
		
		assertEquals(Action.PREPARED, i.action);
		assertEquals(idB, i.xid);

		i = resourceA.getList().get(3);
		
		assertEquals(Action.COMMITTED, i.action);
		assertEquals(idA, i.xid);
		
		i = resourceB.getList().get(3);
		
		assertEquals(Action.COMMITTED, i.action);
		assertEquals(idB, i.xid);
		
		
		assertEquals(4, resourceA.getList().size());
		assertEquals(4, resourceB.getList().size());
	}

	@Test
	public void testRegisterXAResourcesTwoPhaseWithFirstFailure() {
		
		RecordingResource resourceA = new PoisonResource();
		RecordingResource resourceB = new RecordingResource();
		
		try {
			txControl.required(new Callable<Object>() {
				@Override
				public Object call() throws Exception {
					TransactionContext context = txControl.getCurrentContext();
					context.registerXAResource(resourceA, null);
					context.registerXAResource(resourceB, null);
					return null;
				}
			});
			fail("Should roll back");
		} catch (TransactionRolledBackException te) {
			// Expected
		}
		
		Interaction i = resourceA.getList().get(0);
		
		assertEquals(Action.STARTED, i.action);
		
		Xid idA = i.xid;
		
		i = resourceB.getList().get(0);
		
		assertEquals(Action.STARTED, i.action);
		
		Xid idB = i.xid;
		
		assertArrayEquals(idA.getGlobalTransactionId(), idB.getGlobalTransactionId());
		
		i = resourceA.getList().get(1);
		
		assertEquals(Action.END, i.action);
		assertEquals(idA, i.xid);
		
		i = resourceB.getList().get(1);
		
		assertEquals(Action.END, i.action);
		assertEquals(idB, i.xid);
		
		i = resourceA.getList().get(2);
		
		assertEquals(Action.PREPARED, i.action);
		assertEquals(idA, i.xid);
		
		i = resourceB.getList().get(2);
		
		assertEquals(Action.ROLLED_BACK, i.action);
		assertEquals(idB, i.xid);
		
		i = resourceA.getList().get(3);
		
		assertEquals(Action.ROLLED_BACK, i.action);
		assertEquals(idA, i.xid);
		
		
		assertEquals(4, resourceA.getList().size());
		assertEquals(3, resourceB.getList().size());
	}
	
	@Test
	public void testRegisterXAResourcesTwoPhaseWithSecondFailure() {
		
		RecordingResource resourceA = new RecordingResource();
		RecordingResource resourceB = new PoisonResource();
		
		try {
			txControl.required(new Callable<Object>() {
				@Override
				public Object call() throws Exception {
					TransactionContext context = txControl.getCurrentContext();
					context.registerXAResource(resourceA, null);
					context.registerXAResource(resourceB, null);
					return null;
				}
			});
		} catch (TransactionRolledBackException te) {
			// Expected
		}
		
		Interaction i = resourceA.getList().get(0);
		
		assertEquals(Action.STARTED, i.action);
		
		Xid idA = i.xid;
		
		i = resourceB.getList().get(0);
		
		assertEquals(Action.STARTED, i.action);
		
		Xid idB = i.xid;
		
		assertArrayEquals(idA.getGlobalTransactionId(), idB.getGlobalTransactionId());
		
		i = resourceA.getList().get(1);
		
		assertEquals(Action.END, i.action);
		assertEquals(idA, i.xid);
		
		i = resourceB.getList().get(1);
		
		assertEquals(Action.END, i.action);
		assertEquals(idB, i.xid);
		
		i = resourceA.getList().get(2);
		
		assertEquals(Action.PREPARED, i.action);
		assertEquals(idA, i.xid);
		
		i = resourceB.getList().get(2);
		
		assertEquals(Action.PREPARED, i.action);
		assertEquals(idB, i.xid);
		
		i = resourceA.getList().get(3);
		
		assertEquals(Action.ROLLED_BACK, i.action);
		assertEquals(idA, i.xid);
		
		i = resourceB.getList().get(3);
		
		assertEquals(Action.ROLLED_BACK, i.action);
		assertEquals(idB, i.xid);
		
		
		assertEquals(4, resourceA.getList().size());
		assertEquals(4, resourceB.getList().size());
	}

	@Test
	public void testLastParticipantGambit() {
		
		RecordingResource resource = new RecordingResource();
		
		txControl.required(new Callable<Object>() {
			@Override
			public Object call() throws Exception {
				TransactionContext context = txControl.getCurrentContext();
				context.registerXAResource(resource, null);
				context.registerLocalResource(resource);
				return null;
			}
		});
		
		Interaction i = resource.getList().get(0);
		
		assertEquals(Action.STARTED, i.action);
		
		Xid id = i.xid;
		
		i = resource.getList().get(1);
		
		assertEquals(Action.END, i.action);
		assertEquals(id, i.xid);
		
		i = resource.getList().get(2);
		
		assertEquals(Action.PREPARED, i.action);
		assertEquals(id, i.xid);
		
		i = resource.getList().get(3);
		
		assertEquals(Action.LOCAL_COMMIT, i.action);
		assertNull(i.xid);
		
		i = resource.getList().get(4);
		
		assertEquals(Action.COMMITTED, i.action);
		assertEquals(id, i.xid);
		
		assertEquals(5, resource.getList().size());
	}
	
	@Test
	public void testLastParticipantGambitLocalFailure() {
		
		RecordingResource resource = new RecordingResource();
		
		try {
			txControl.required(new Callable<Object>() {
				@Override
				public Object call() throws Exception {
					TransactionContext context = txControl.getCurrentContext();
					context.registerXAResource(resource, null);
					context.registerLocalResource(new PoisonResource());
					return null;
				}
			});
			fail("Should roll back");
		} catch (TransactionRolledBackException te) {
			// Expected
		}
		
		Interaction i = resource.getList().get(0);
		
		assertEquals(Action.STARTED, i.action);
		
		Xid id = i.xid;
		
		i = resource.getList().get(1);
		
		assertEquals(Action.END, i.action);
		assertEquals(id, i.xid);
		
		i = resource.getList().get(2);
		
		assertEquals(Action.PREPARED, i.action);
		assertEquals(id, i.xid);
		
		i = resource.getList().get(3);
		
		assertEquals(Action.ROLLED_BACK, i.action);
		assertEquals(id, i.xid);
		
		assertEquals(4, resource.getList().size());
	}

	@Test
	public void testLastParticipantGambitXAFailure() {
		
		RecordingResource xaResource = new PoisonResource();
		RecordingResource localResource = new RecordingResource();
		
		try {
			txControl.required(new Callable<Object>() {
				@Override
				public Object call() throws Exception {
					TransactionContext context = txControl.getCurrentContext();
					context.registerXAResource(xaResource, null);
					context.registerLocalResource(localResource);
					return null;
				}
			});
			fail("Should roll back");
		} catch (TransactionRolledBackException te) {
			// Expected
		}
		
		Interaction i = localResource.getList().get(0);
		
		assertEquals(Action.LOCAL_ROLLBACK, i.action);
		assertNull(i.xid);
		
		i = xaResource.getList().get(0);
		
		assertEquals(Action.STARTED, i.action);
		
		Xid id = i.xid;
		
		i = xaResource.getList().get(1);
		
		assertEquals(Action.END, i.action);
		assertEquals(id, i.xid);
		
		i = xaResource.getList().get(2);
		
		assertEquals(Action.PREPARED, i.action);
		assertEquals(id, i.xid);
		
		i = xaResource.getList().get(3);
		
		assertEquals(Action.ROLLED_BACK, i.action);
		assertEquals(id, i.xid);
		
		assertEquals(4, xaResource.getList().size());
	}

	@Configuration
	public Option[] xaTxConfiguration() {
		String localRepo = System.getProperty("maven.repo.local");
		if (localRepo == null) {
			localRepo = System.getProperty("org.ops4j.pax.url.mvn.localRepository");
		}

		return options(junitBundles(), systemProperty("org.ops4j.pax.logging.DefaultServiceLog.level").value("INFO"),
				when(localRepo != null)
						.useOptions(CoreOptions.vmOption("-Dorg.ops4j.pax.url.mvn.localRepository=" + localRepo)),
				mavenBundle("org.apache.aries.tx-control", "tx-control-service-xa").versionAsInProject(),
				mavenBundle("org.apache.felix", "org.apache.felix.configadmin").versionAsInProject(),
				mavenBundle("org.ops4j.pax.logging", "pax-logging-api").versionAsInProject(),
				mavenBundle("org.ops4j.pax.logging", "pax-logging-service").versionAsInProject()

		// ,CoreOptions.vmOption("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005")
		);
	}
}
