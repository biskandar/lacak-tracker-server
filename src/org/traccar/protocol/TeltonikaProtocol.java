/*
 * Copyright 2015 - 2016 Anton Tananaev (anton.tananaev@gmail.com)
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
package org.traccar.protocol;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.jboss.netty.bootstrap.ConnectionlessBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelPipeline;
import org.traccar.BaseProtocol;
import org.traccar.TrackerServer;

public class TeltonikaProtocol extends BaseProtocol {
  
  public TeltonikaProtocol() {
    super("teltonika");
  }
  
  @Override
  public void initTrackerServers(List<TrackerServer> serverList) {
    
    serverList.add(new TrackerServer(new ServerBootstrap(), this.getName()) {
      @Override
      protected void addSpecificHandlers(ChannelPipeline pipeline) {
        pipeline.addLast("objectForwarder", new TeltonikaProtocolForwarder(
            TeltonikaProtocol.this, false));
        pipeline.addLast("frameDecoder", new TeltonikaFrameDecoder());
        pipeline.addLast("objectDecoder", new TeltonikaProtocolDecoder(
            TeltonikaProtocol.this));
      }
    });
    
    serverList.add(new TrackerServer(new ConnectionlessBootstrap(), this
        .getName()) {
      @Override
      protected void addSpecificHandlers(ChannelPipeline pipeline) {
        pipeline.addLast("objectForwarder", new TeltonikaProtocolForwarder(
            TeltonikaProtocol.this, true));
        pipeline.addLast("objectDecoder", new TeltonikaProtocolDecoder(
            TeltonikaProtocol.this));
      }
    });
    
  }
  
  public static String readCommandText(ChannelBuffer channelBuffer) {
    String commandText = null;
    try {
      
      if (channelBuffer == null) {
        return commandText;
      }
      
      if (channelBuffer.getUnsignedShort(0) > 0) {
        return commandText;
      }
      
      channelBuffer.skipBytes(4); // marker
      long dataLength = channelBuffer.readUnsignedInt();
      int codec = channelBuffer.readUnsignedByte();
      int count = channelBuffer.readUnsignedByte();
      
      if (count < 1) {
        return commandText;
      }
      
      if (codec != 0x0C) {
        return commandText;
      }
      
      int keyType = channelBuffer.readUnsignedByte();
      
      commandText = channelBuffer.readBytes(channelBuffer.readInt()).toString(
          StandardCharsets.US_ASCII);
      
    } catch (Exception e) {
    }
    return commandText;
  }
  
}
