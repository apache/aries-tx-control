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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;
import static org.ops4j.pax.exam.CoreOptions.when;

import java.util.concurrent.Callable;

import javax.inject.Inject;

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

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class LocalTransactionTest {

	@Inject
	@Filter("(osgi.local.enabled=true)")
	private TransactionControl txControl;

	@Test
	public void testLocalSupportAdvertised() {
		txControl.required(new Callable<Object>() {
			@Override
			public Object call() throws Exception {
				assertTrue(txControl.getCurrentContext().supportsLocal());
				return null;
			}
		});
	}

	@Test
	public void testRegisterLocalResource() {

		RecordingResource resource = new RecordingResource();
		
		txControl.required(new Callable<Object>() {
			@Override
			public Object call() throws Exception {
				TransactionContext context = txControl.getCurrentContext();
				context.registerLocalResource(resource);
				return null;
			}
		});
		
		Interaction i = resource.getList().get(0);
		
		assertEquals(Action.LOCAL_COMMIT, i.action);
		assertNull(i.xid);
		
		assertEquals(1, resource.getList().size());
	}

	@Configuration
	public Option[] localTxConfiguration() {
		String localRepo = System.getProperty("maven.repo.local");
		if (localRepo == null) {
			localRepo = System.getProperty("org.ops4j.pax.url.mvn.localRepository");
		}

		return options(junitBundles(), systemProperty("org.ops4j.pax.logging.DefaultServiceLog.level").value("INFO"),
				when(localRepo != null)
						.useOptions(CoreOptions.vmOption("-Dorg.ops4j.pax.url.mvn.localRepository=" + localRepo)),
				mavenBundle("org.apache.aries.tx-control", "tx-control-service-local").versionAsInProject(),
				mavenBundle("org.apache.felix", "org.apache.felix.configadmin").versionAsInProject(),
				mavenBundle("org.ops4j.pax.logging", "pax-logging-api").versionAsInProject(),
				mavenBundle("org.ops4j.pax.logging", "pax-logging-service").versionAsInProject()

		// ,CoreOptions.vmOption("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005")
		);
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
