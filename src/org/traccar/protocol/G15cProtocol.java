package org.traccar.protocol;

import java.util.List;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelPipeline;
import org.traccar.BaseProtocol;
import org.traccar.TrackerServer;

public class G15cProtocol extends BaseProtocol {
  
  public G15cProtocol() {
    super("g15c");
  }
  
  @Override
  public void initTrackerServers(List<TrackerServer> serverList) {
    
    serverList.add(new TrackerServer(new ServerBootstrap(), this.getName()) {
      @Override
      protected void addSpecificHandlers(ChannelPipeline pipeline) {
        pipeline.addLast("objectForwarder", new G15cProtocolForwarder(
            G15cProtocol.this));
        pipeline.addLast("frameDecoder", new G15cFrameDecoder());
        pipeline.addLast("objectEncoder", new G15cProtocolEncoder());
        pipeline.addLast("objectDecoder", new G15cProtocolDecoder(
            G15cProtocol.this));
      }
    });
    
  }
  
}
