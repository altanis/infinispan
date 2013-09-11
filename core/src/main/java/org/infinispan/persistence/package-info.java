/**
 * This package contains stores, which are used for overflow or persistence.
 * Need @XmlSchema annotation for CacheLoaderConfig.java and AbstractCacheStoreConfig.java
 */
@XmlSchema(namespace = ISPN_NS, elementFormDefault = XmlNsForm.QUALIFIED, attributeFormDefault = XmlNsForm.UNQUALIFIED, 
         xmlns = {
         @javax.xml.bind.annotation.XmlNs(prefix = "tns", namespaceURI = ISPN_NS),
         @javax.xml.bind.annotation.XmlNs(prefix = "xs", namespaceURI = "http://www.w3.org/2001/XMLSchema") })
package org.infinispan.persistence;

import javax.xml.bind.annotation.XmlNsForm;
import javax.xml.bind.annotation.*;
import static org.infinispan.config.parsing.NamespaceFilter.ISPN_NS;