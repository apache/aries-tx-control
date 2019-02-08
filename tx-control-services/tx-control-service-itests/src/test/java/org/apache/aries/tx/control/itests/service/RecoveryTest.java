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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;
import static org.ops4j.pax.exam.CoreOptions.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
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
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.transaction.control.TransactionContext;
import org.osgi.service.transaction.control.TransactionControl;
import org.osgi.service.transaction.control.recovery.RecoverableXAResource;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class RecoveryTest {

	@Inject
	@Filter("(osgi.xa.enabled=true)")
	private TransactionControl txControl;
	
	@Inject
	private BundleContext context;
	
	@Test
	public void testResourceRevcovery() throws InterruptedException {
		RecordingResource resource = new RecordingResource() {

			@Override
			public void commit(Xid arg0, boolean arg1) throws XAException {
				super.commit(arg0, arg1);
				throw new XAException(XAException.XAER_RMFAIL);
			}
			
		};
		
		txControl.required(new Callable<Object>() {
			@Override
			public Object call() throws Exception {
				TransactionContext context = txControl.getCurrentContext();
				context.registerXAResource(new RecordingResource(), null);
				context.registerXAResource(resource, "test");
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

		assertEquals(Action.COMMITTED, i.action);
		assertEquals(id, i.xid);
		
		assertEquals(4, resource.getList().size());
		
		CountDownLatch latch = new CountDownLatch(1);
		
		RecordingResource recovery = new RecordingResource() {
			List<Xid> list = new ArrayList<>(Collections.singleton(id));

			@Override
			public void commit(Xid arg0, boolean arg1) throws XAException {
				super.commit(arg0, arg1);
				list.remove(arg0);
				latch.countDown();
			}

			@Override
			public void forget(Xid arg0) throws XAException {
				super.forget(arg0);
				list.remove(arg0);
				latch.countDown();
			}

			@Override
			public Xid[] recover(int arg0) throws XAException {
				return list.toArray(new Xid[1]);
			}

			@Override
			public void rollback(Xid arg0) throws XAException {
				super.rollback(arg0);
				list.remove(arg0);
				latch.countDown();
			}
		};
		
		
		
		RecoverableXAResource r = new RecoverableXAResource() {
			
			@Override
			public void releaseXAResource(XAResource xaRes) {
				
			}
			
			@Override
			public XAResource getXAResource() throws Exception {
				return recovery;
			}
			
			@Override
			public String getId() {
				return "test";
			}
		};
		
		ServiceRegistration<?> reg = context.registerService(RecoverableXAResource.class, r, null);
		
		try {
			
			assertTrue(latch.await(30, TimeUnit.SECONDS));
			
			i = recovery.getList().get(0);
			
			assertEquals(Action.COMMITTED, i.action);
			assertEquals(id, i.xid);
			
			assertEquals(1, recovery.getList().size());
			
		} finally {
			reg.unregister();
		}
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
				mavenBundle("org.ops4j.pax.logging", "pax-logging-service").versionAsInProject(),
				CoreOptions.frameworkProperty("org.apache.aries.tx.control.service.xa.recovery.log.enabled").value("true")

//		 ,CoreOptions.vmOption("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005")
		);
	}
}
