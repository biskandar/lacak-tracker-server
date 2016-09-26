/*
 * Copyright 2012 - 2016 Anton Tananaev (anton.tananaev@gmail.com)
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

import java.net.InetSocketAddress;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.DownstreamMessageEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.handler.logging.LoggingHandler;
import org.jboss.netty.handler.timeout.IdleStateHandler;
import org.traccar.events.CommandResultEventHandler;
import org.traccar.events.GeofenceEventHandler;
import org.traccar.events.MotionEventHandler;
import org.traccar.events.OverspeedEventHandler;
import org.traccar.helper.Log;

public abstract class BasePipelineFactory implements ChannelPipelineFactory {
  
  private final TrackerServer server;
  private int timeout;
  
  private FilterHandler filterHandler;
  private DistanceHandler distanceHandler;
  private ReverseGeocoderHandler reverseGeocoderHandler;
  private LocationProviderHandler locationProviderHandler;
  private HemisphereHandler hemisphereHandler;
  
  private CommandResultEventHandler commandResultEventHandler;
  private OverspeedEventHandler overspeedEventHandler;
  private MotionEventHandler motionEventHandler;
  private GeofenceEventHandler geofenceEventHandler;
  
  private static final class OpenChannelHandler extends SimpleChannelHandler {
    
    private final TrackerServer server;
    
    private OpenChannelHandler(TrackerServer server) {
      this.server = server;
    }
    
    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) {
      server.getChannelGroup().add(e.getChannel());
    }
  }
  
  private static class StandardLoggingHandler extends LoggingHandler {
    
    @Override
    public void log(ChannelEvent e) {
      if (e instanceof MessageEvent) {
        MessageEvent event = (MessageEvent) e;
        StringBuilder msg = new StringBuilder();
        
        msg.append("[").append(String.format("%08X", e.getChannel().getId()))
            .append(": ");
        msg.append(((InetSocketAddress) e.getChannel().getLocalAddress())
            .getPort());
        if (e instanceof DownstreamMessageEvent) {
          msg.append(" > ");
        } else {
          msg.append(" < ");
        }
        
        if (event.getRemoteAddress() != null) {
          msg.append(((InetSocketAddress) event.getRemoteAddress())
              .getHostString());
        } else {
          msg.append("null");
        }
        msg.append("]");
        
        if (event.getMessage() instanceof ChannelBuffer) {
          msg.append(" HEX: ");
          msg.append(ChannelBuffers.hexDump((ChannelBuffer) event.getMessage()));
        }
        
        Log.debug(msg.toString());
      }
    }
    
  }
  
  public BasePipelineFactory(TrackerServer server, String protocol) {
    this.server = server;
    
    timeout = Context.getConfig().getInteger(protocol + ".timeout", 0);
    if (timeout == 0) {
      timeout = Context.getConfig().getInteger(protocol + ".resetDelay", 0); // temporary
    }
    
    if (Context.getConfig().getBoolean("filter.enable")) {
      filterHandler = new FilterHandler();
    }
    
    if (Context.getReverseGeocoder() != null) {
      reverseGeocoderHandler = new ReverseGeocoderHandler(
          Context.getReverseGeocoder(), Context.getConfig().getBoolean(
              "geocoder.processInvalidPositions"));
    }
    
    if (Context.getLocationProvider() != null) {
      locationProviderHandler = new LocationProviderHandler(
          Context.getLocationProvider(), Context.getConfig().getBoolean(
              "location.processInvalidPositions"));
    }
    
    if (Context.getConfig().getBoolean("distance.enable")) {
      distanceHandler = new DistanceHandler();
    }
    
    if (Context.getConfig().hasKey("location.latitudeHemisphere")
        || Context.getConfig().hasKey("location.longitudeHemisphere")) {
      hemisphereHandler = new HemisphereHandler();
    }
    
    if (Context.getConfig().getBoolean("event.enable")) {
      commandResultEventHandler = new CommandResultEventHandler();
      
      if (Context.getConfig().getBoolean("event.overspeedHandler")) {
        overspeedEventHandler = new OverspeedEventHandler();
      }
      
      if (Context.getConfig().getBoolean("event.motionHandler")) {
        motionEventHandler = new MotionEventHandler();
      }
    }
    if (Context.getConfig().getBoolean("event.geofenceHandler")) {
      geofenceEventHandler = new GeofenceEventHandler();
    }
  }
  
  protected abstract void addSpecificHandlers(ChannelPipeline pipeline);
  
  @Override
  public ChannelPipeline getPipeline() {
    ChannelPipeline pipeline = Channels.pipeline();
    
    if (timeout > 0 && !server.isConnectionless()) {
      pipeline.addLast("idleHandler",
          new IdleStateHandler(GlobalTimer.getTimer(), timeout, 0, 0));
    }
    
    pipeline.addLast("openHandler", new OpenChannelHandler(server));
    
    if (Context.isLoggerEnabled()) {
      pipeline.addLast("logger", new StandardLoggingHandler());
    }
    
    addSpecificHandlers(pipeline);
    
    if (hemisphereHandler != null) {
      pipeline.addLast("hemisphere", hemisphereHandler);
    }
    if (distanceHandler != null) {
      pipeline.addLast("distance", distanceHandler);
    }
    if (reverseGeocoderHandler != null) {
      pipeline.addLast("geocoder", reverseGeocoderHandler);
    }
    if (locationProviderHandler != null) {
      pipeline.addLast("location", locationProviderHandler);
    }
    pipeline.addLast("remoteAddress", new RemoteAddressHandler());
    
    addDynamicHandlers(pipeline);
    
    if (filterHandler != null) {
      pipeline.addLast("filter", filterHandler);
    }
    
    if (Context.getDataManager() != null) {
      pipeline.addLast("dataHandler", new DefaultDataHandler());
    }
    
    if (Context.getConfig().getBoolean("forward.enable")) {
      String forwardUrl = Context.getConfig().getString("forward.url");
      pipeline.addLast("webHandler", new WebDataHandler(forwardUrl));
    }
    
    if (Context.getConfig().getBoolean("forward.splunk.enable")) {
      String forwardSplunkUrl = Context.getConfig().getString(
          "forward.splunk.url");
      String forwardSplunkToken = Context.getConfig().getString(
          "forward.splunk.token");
      String host = Context.getConfig().getString("forward.splunk.host");
      String source = Context.getConfig().getString("forward.splunk.source");
      String sourceType = Context.getConfig().getString(
          "forward.splunk.sourcetype");
      String index = Context.getConfig().getString("forward.splunk.index");
      pipeline.addLast("splunkHandler", new SplunkDataHandler(forwardSplunkUrl,
          forwardSplunkToken, host, source, sourceType, index));
    }
    
    if (commandResultEventHandler != null) {
      pipeline.addLast("CommandResultEventHandler", commandResultEventHandler);
    }
    
    if (overspeedEventHandler != null) {
      pipeline.addLast("OverspeedEventHandler", overspeedEventHandler);
    }
    
    if (motionEventHandler != null) {
      pipeline.addLast("MotionEventHandler", motionEventHandler);
    }
    
    if (geofenceEventHandler != null) {
      pipeline.addLast("GeofenceEventHandler", geofenceEventHandler);
    }
    
    pipeline.addLast("mainHandler", new MainEventHandler());
    return pipeline;
  }
  
  private void addDynamicHandlers(ChannelPipeline pipeline) {
    if (Context.getConfig().hasKey("extra.handlers")) {
      String[] handlers = Context.getConfig().getString("extra.handlers")
          .split(",");
      for (int i = 0; i < handlers.length; i++) {
        try {
          pipeline.addLast("extraHandler." + i,
              (ChannelHandler) Class.forName(handlers[i]).newInstance());
        } catch (ClassNotFoundException | InstantiationException
            | IllegalAccessException error) {
          Log.warning(error);
        }
      }
    }
  }
  
}
