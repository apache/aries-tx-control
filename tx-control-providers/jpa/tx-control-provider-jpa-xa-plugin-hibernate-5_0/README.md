Hibernate (5.0.x & 5.1.x) plugin for Aries Tx Control JPA (XA) 
--------------------------------------------------------------

This maven module provides a plugin for Hibernate used to support XA transactions when using Transaction Control. This plugin supports Hibernate 5.0.x and 5.1.x. The plugin SPI was broken in Hibernate 5.2.x, so a different plugin supports those versions. This module must not be used in isolation, and is designed to be repackaged into the Aries Tx Control JPA (XA) provider.

The Transaction Control Service (RFC-221) is an in-progress RFC publicly available from the OSGi Alliance: https://github.com/osgi/design/blob/master/rfcs/rfc0221/rfc-0221-TransactionControl.pdf