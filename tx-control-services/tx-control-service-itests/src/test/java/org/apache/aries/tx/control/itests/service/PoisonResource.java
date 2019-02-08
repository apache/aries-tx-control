package org.apache.aries.tx.control.itests.service;

import javax.transaction.xa.XAException;
import javax.transaction.xa.Xid;

import org.osgi.service.transaction.control.TransactionException;

public class PoisonResource extends RecordingResource {

	@Override
	public void commit(Xid arg0, boolean arg1) throws XAException {
		super.commit(arg0, arg1);
		throw new XAException(XAException.XA_RBOTHER);
	}

	@Override
	public int prepare(Xid arg0) throws XAException {
		super.prepare(arg0);
		throw new XAException(XAException.XA_RBOTHER);
	}

	@Override
	public void commit() throws TransactionException {
		super.commit();
		throw new TransactionException("Poison");
	}
}