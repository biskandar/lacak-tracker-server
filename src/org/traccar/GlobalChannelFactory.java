/*
 * Copyright 2012 Anton Tananaev (anton.tananaev@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar;

import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.socket.DatagramChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioDatagramChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;

public final class GlobalChannelFactory {
  
  private static ChannelFactory serverChannelFactory = null;
  private static ChannelFactory clientChannelFactory = null;
  private static DatagramChannelFactory datagramChannelFactory = null;
  
  private GlobalChannelFactory() {
  }
  
  public static void release() {
    if (serverChannelFactory != null) {
      serverChannelFactory.releaseExternalResources();
    }
    if (clientChannelFactory != null) {
      clientChannelFactory.releaseExternalResources();
    }
    if (datagramChannelFactory != null) {
      datagramChannelFactory.releaseExternalResources();
    }
    serverChannelFactory = null;
    datagramChannelFactory = null;
  }
  
  public static ChannelFactory getServerFactory() {
    if (serverChannelFactory == null) {
      serverChannelFactory = new NioServerSocketChannelFactory();
    }
    return serverChannelFactory;
  }
  
  public static ChannelFactory getClientFactory() {
    if (clientChannelFactory == null) {
      clientChannelFactory = new NioClientSocketChannelFactory();
    }
    return clientChannelFactory;
  }
  
  public static DatagramChannelFactory getDatagramFactory() {
    if (datagramChannelFactory == null) {
      datagramChannelFactory = new NioDatagramChannelFactory();
    }
    return datagramChannelFactory;
  }
  
}
