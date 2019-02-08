package org.apache.aries.tx.control.itests.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.osgi.service.transaction.control.LocalResource;
import org.osgi.service.transaction.control.TransactionException;

public class RecordingResource implements XAResource, LocalResource {
	
	public static enum Action {
		LOCAL_COMMIT, LOCAL_ROLLBACK, STARTED, PREPARED, COMMITTED_ONE_PHASE, COMMITTED, ROLLED_BACK, FORGOTTEN, END;
	}

	public static class Interaction {
		public final RecordingResource.Action action;
		public final Xid xid;
		
		public Interaction(RecordingResource.Action action, Xid xid) {
			this.action = action;
			this.xid = xid;
		}
	}

	private final List<RecordingResource.Interaction> list = new ArrayList<>();

	@Override
	public void commit(Xid arg0, boolean arg1) throws XAException {
		list.add(new Interaction(
				arg1 ? Action.COMMITTED_ONE_PHASE : Action.COMMITTED, arg0));
	}

	@Override
	public void end(Xid arg0, int arg1) throws XAException {
		list.add(new Interaction(Action.END, arg0));
	}

	@Override
	public void forget(Xid arg0) throws XAException {
		list.add(new Interaction(Action.FORGOTTEN, arg0));
	}

	@Override
	public int getTransactionTimeout() throws XAException {
		return 30;
	}

	@Override
	public boolean isSameRM(XAResource arg0) throws XAException {
		return this == arg0;
	}

	@Override
	public int prepare(Xid arg0) throws XAException {
		list.add(new Interaction(Action.PREPARED, arg0));
		return XA_OK;
	}

	@Override
	public Xid[] recover(int arg0) throws XAException {
		return new Xid[0];
	}

	@Override
	public void rollback(Xid arg0) throws XAException {
		list.add(new Interaction(Action.ROLLED_BACK, arg0));
	}

	@Override
	public boolean setTransactionTimeout(int arg0) throws XAException {
		return false;
	}

	@Override
	public void start(Xid arg0, int arg1) throws XAException {
		list.add(new Interaction(Action.STARTED, arg0));
	}

	public List<RecordingResource.Interaction> getList() {
		return Collections.unmodifiableList(list);
	}

	@Override
	public void commit() throws TransactionException {
		list.add(new Interaction(Action.LOCAL_COMMIT, null));
	}

	@Override
	public void rollback() throws TransactionException {
		list.add(new Interaction(Action.LOCAL_ROLLBACK, null));
	}
}