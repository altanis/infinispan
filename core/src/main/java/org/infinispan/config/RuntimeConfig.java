/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2000 - 2008, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.infinispan.config;

import org.infinispan.remoting.rpc.RpcManager;
import org.infinispan.util.Util;

import javax.transaction.TransactionManager;
import java.util.concurrent.ExecutorService;

public class RuntimeConfig extends AbstractNamedCacheConfigurationBean {
   /**
    * The serialVersionUID
    */
   private static final long serialVersionUID = 5626847485703341794L;

   private transient TransactionManager transactionManager;
   private RpcManager rpcManager;
   private transient ExecutorService asyncSerializationExecutor;
   private transient ExecutorService asyncCacheListenerExecutor;

   /**
    * Resets the runtime to default values.
    */
   public void reset() {
      rpcManager = null;
   }


   public TransactionManager getTransactionManager() {
      return transactionManager;
   }

   public void setTransactionManager(TransactionManager transactionManager) {
      testImmutability("transactionManager");
      this.transactionManager = transactionManager;
   }

   /**
    * This is only relevant if the async cache replication executor has been set using {@link
    * #setAsyncSerializationExecutor(ExecutorService)}. If the executor is created internally, this method will return
    * null.
    * <p/>
    *
    * @return the executor used for async replication.
    */
   public ExecutorService getAsyncSerializationExecutor() {
      return asyncSerializationExecutor;
   }

   /**
    * This is used to set the executor to use for asynchronous cache operations.
    *
    * @param asyncSerializationExecutor executor to set
    */
   public void setAsyncSerializationExecutor(ExecutorService asyncSerializationExecutor) {
      this.asyncSerializationExecutor = asyncSerializationExecutor;
   }

   /**
    * This is only relevant if the async cache listener executor has been set using {@link
    * #setAsyncCacheListenerExecutor(java.util.concurrent.ExecutorService)}. If the executor is created internally, this
    * method will return null.
    * <p/>
    *
    * @return the executor to use for async cache listeners
    */
   public ExecutorService getAsyncCacheListenerExecutor() {
      return asyncCacheListenerExecutor;
   }

   /**
    * This is used to set the executor to use for asynchronous cache listeners.
    * <p/>
    *
    * @param asyncCacheListenerExecutor the executor to use for async cache listeners
    */
   public void setAsyncCacheListenerExecutor(ExecutorService asyncCacheListenerExecutor) {
      this.asyncCacheListenerExecutor = asyncCacheListenerExecutor;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }

      if (obj instanceof RuntimeConfig) {
         RuntimeConfig other = (RuntimeConfig) obj;
         return Util.safeEquals(transactionManager, other.transactionManager)
               && Util.safeEquals(rpcManager, other.rpcManager)
               && Util.safeEquals(asyncCacheListenerExecutor, other.asyncCacheListenerExecutor)
               && Util.safeEquals(asyncSerializationExecutor, other.asyncSerializationExecutor);
      }

      return false;
   }

   @Override
   public int hashCode() {
      int result = 17;
      result = result * 29 + (transactionManager == null ? 0 : transactionManager.hashCode());
      result = result * 29 + (rpcManager == null ? 0 : rpcManager.hashCode());
      result = result * 29 + (asyncCacheListenerExecutor == null ? 0 : asyncCacheListenerExecutor.hashCode());
      result = result * 29 + (asyncSerializationExecutor == null ? 0 : asyncSerializationExecutor.hashCode());
      return result;
   }

   public void setRPCManager(RpcManager rpcManager) {
      this.rpcManager = rpcManager;
   }

   public RpcManager getRPCManager() {
      return rpcManager;
   }

   @Override
   public RuntimeConfig clone() throws CloneNotSupportedException {
      return (RuntimeConfig) super.clone();
   }
}
