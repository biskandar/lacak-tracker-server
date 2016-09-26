/*
 * Copyright 2015 Vijay Kumar (vijaykumar@zilogic.com)
 * Copyright 2015 Anton Tananaev (anton.tananaev@gmail.com)
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

import java.nio.ByteOrder;
import java.util.List;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelPipeline;
import org.traccar.BaseProtocol;
import org.traccar.TrackerServer;

public class BlackKiteProtocol extends BaseProtocol {
  
  public BlackKiteProtocol() {
    super("blackkite");
  }
  
  @Override
  public void initTrackerServers(List<TrackerServer> serverList) {
    TrackerServer server = new TrackerServer(new ServerBootstrap(),
        this.getName()) {
      @Override
      protected void addSpecificHandlers(ChannelPipeline pipeline) {
        pipeline.addLast("frameDecoder", new GalileoFrameDecoder());
        pipeline.addLast("objectDecoder", new BlackKiteProtocolDecoder(
            BlackKiteProtocol.this));
      }
    };
    server.setEndianness(ByteOrder.LITTLE_ENDIAN);
    serverList.add(server);
  }
  
}