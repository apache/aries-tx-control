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
package org.apache.aries.tx.control.service.common.impl;

import static org.osgi.service.transaction.control.TransactionStatus.NO_TRANSACTION;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import javax.transaction.xa.XAResource;

import org.osgi.service.transaction.control.LocalResource;
import org.osgi.service.transaction.control.TransactionContext;
import org.osgi.service.transaction.control.TransactionStatus;

public class NoTransactionContextImpl extends AbstractTransactionContextImpl
		implements TransactionContext {

	private enum Status {
		WORKING, PRE, POST;
	}
	
	private final AtomicReference<Status> status = new AtomicReference<>(Status.WORKING);

	public NoTransactionContextImpl() {
		super();
	}

	@Override
	public Object getTransactionKey() {
		return null;
	}

	@Override
	public boolean getRollbackOnly() throws IllegalStateException {
		throw new IllegalStateException("No transaction is active");
	}

	@Override
	public void setRollbackOnly() throws IllegalStateException {
		throw new IllegalStateException("No transaction is active");
	}

	@Override
	public TransactionStatus getTransactionStatus() {
		return NO_TRANSACTION;
	}

	@Override
	public void preCompletion(Runnable job) throws IllegalStateException {
		if (status.get() != Status.WORKING) {
			throw new IllegalStateException(
					"The scoped work has returned. No more pre-completion callbacks can be registered");
		}
		
		preCompletion.add(job);
	}

	@Override
	public void postCompletion(Consumer<TransactionStatus> job)
			throws IllegalStateException {
		if (status.get() == Status.POST) {
			throw new IllegalStateException(
					"Post completion callbacks have begun. No more post-completion callbacks can be registered");
		}

		postCompletion.add(job);
	}

	@Override
	public void registerXAResource(XAResource resource, String recoveryName) {
		throw new IllegalStateException("No transaction is active");
	}

	@Override
	public void registerLocalResource(LocalResource resource) {
		throw new IllegalStateException("No transaction is active");
	}

	@Override
	public boolean supportsXA() {
		return false;
	}

	@Override
	public boolean supportsLocal() {
		return false;
	}

	@Override
	public boolean isReadOnly() {
		return false;
	}

	@Override
	protected boolean isAlive() {
		return status.get() == Status.WORKING;
	}
	
	@Override
	public void finish() {
		if(status.compareAndSet(Status.WORKING, Status.PRE)) {
			beforeCompletion(() -> {});
			status.set(Status.POST);
			afterCompletion(NO_TRANSACTION);
		}
	}

	@Override
	protected void safeSetRollbackOnly() {
	}
}
