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
package org.infinispan.commands;

import org.infinispan.context.InvocationContext;

/**
 * The core of the command-based cache framework.  Commands correspond to specific areas of functionality in the cache,
 * and can be replicated using the {@link org.infinispan.remoting.rpc.RpcManager}
 *
 * @author Mircea.Markus@jboss.com
 * @author Manik Surtani
 * @since 4.0
 */
public interface ReplicableCommand {
   /**
    * Performs the primary function of the command.  Please see specific implementation classes for details on what is
    * performed as well as return types. <b>Important</b>: this method will be invoked at the end of interceptors chain.
    * It should never be called directly from a custom interceptor.
    *
    * @param ctx invocation context
    * @return arbitrary return value generated by performing this command
    * @throws Throwable in the event of problems.
    */
   Object perform(InvocationContext ctx) throws Throwable;

   /**
    * Used by marshallers to convert this command into an id for streaming.
    *
    * @return the method id of this command.  This is compatible with pre-2.2.0 MethodCall ids.
    */
   byte getCommandId();

   /**
    * Used by marshallers to stream this command across a network
    *
    * @return an object array of arguments, compatible with pre-2.2.0 MethodCall args.
    */
   Object[] getParameters();

   /**
    * Used by the {@link CommandsFactory} to create a command from raw data read off a stream.
    *
    * @param commandId  command id to set.  This is usually unused but *could* be used in the event of a command having
    *                   multiple IDs, such as {@link org.infinispan.commands.write.PutKeyValueCommand}.
    * @param parameters object array of args
    */
   void setParameters(int commandId, Object[] parameters);
}