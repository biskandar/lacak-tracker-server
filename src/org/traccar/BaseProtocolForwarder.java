package org.traccar;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.traccar.helper.Log;

public abstract class BaseProtocolForwarder implements ChannelUpstreamHandler {
  
  private final Protocol protocol;
  
  private String remoteHost;
  private int remotePort;
  
  public String getProtocolName() {
    return protocol.getName();
  }
  
  public String getRemoteHost() {
    return remoteHost;
  }
  
  public int getRemotePort() {
    return remotePort;
  }
  
  public BaseProtocolForwarder(Protocol protocol) {
    this.protocol = protocol;
    
    String headerLog = headerLog(null);
    
    String keyPrefix = getProtocolName().concat(".forward");
    
    remoteHost = null;
    remotePort = 0;
    if (Context.getConfig().getBoolean(keyPrefix + ".enable")) {
      remoteHost = Context.getConfig().getString(keyPrefix + ".remote-host");
      remotePort = Context.getConfig().getInteger(keyPrefix + ".remote-port");
    }
    
    if ((remoteHost != null) && (remotePort > 0)) {
      Log.debug(headerLog + "BaseProtocolForwarder initialized : remoteHost = "
          + remoteHost + " , remotePort = " + remotePort);
    }
    
  }
  
  @Override
  public void handleUpstream(ChannelHandlerContext channelHandlerContext,
      ChannelEvent channelEvent) throws Exception {
    
    // header log
    String headerLog = headerLog(channelEvent.getChannel());
    
    // make sure forwarder only works if remote properties are assigned
    if (remoteHost == null) {
      channelHandlerContext.sendUpstream(channelEvent);
      return;
    }
    if (remotePort < 1) {
      channelHandlerContext.sendUpstream(channelEvent);
      return;
    }
    
    // found as channel state
    if (channelEvent instanceof ChannelStateEvent) {
      stateMessage(channelHandlerContext, (ChannelStateEvent) channelEvent);
    }
    
    // found as message
    if (channelEvent instanceof MessageEvent) {
      // forward message
      forwardMessage(channelHandlerContext, (MessageEvent) channelEvent);
    }
    
    // found as exception
    if (channelEvent instanceof ExceptionEvent) {
      ExceptionEvent exceptionEvent = (ExceptionEvent) channelEvent;
      Log.debug(headerLog + "BaseProtocolForwarder caught " + exceptionEvent);
      exceptionCaught(exceptionEvent, channelEvent.getChannel());
    }
    
    // store and forward , keep the original data as it is
    channelHandlerContext.sendUpstream(channelEvent);
    
  }
  
  public ChannelFuture write(Channel channel, ChannelBuffer channelBuffer) {
    ChannelFuture channelFuture = null;
    if (channelBuffer == null) {
      return channelFuture;
    }
    if (channel == null) {
      return channelFuture;
    }
    if (!channel.isConnected()) {
      return channelFuture;
    }
    channelFuture = channel.write(channelBuffer);
    return channelFuture;
  }
  
  public boolean closeOnFlush(Channel channel) {
    boolean result = false;
    // write empty buffer
    ChannelFuture channelFuture = write(channel, ChannelBuffers.EMPTY_BUFFER);
    if (channelFuture != null) {
      // make sure the channel is cleaned than close it
      channelFuture.addListener(ChannelFutureListener.CLOSE);
    }
    result = true;
    return result;
  }
  
  private boolean stateMessage(ChannelHandlerContext channelHandlerContext,
      ChannelStateEvent channelStateEvent) {
    boolean result = false;
    String headerLog = headerLog(channelStateEvent.getChannel());
    switch (channelStateEvent.getState()) {
    case OPEN:
      if (Boolean.TRUE.equals(channelStateEvent.getValue())) {
        Log.debug(headerLog + "BaseProtocolForwarder do channel open");
      } else {
        Log.debug(headerLog + "BaseProtocolForwarder do channel closed");
      }
      break;
    case BOUND:
      if (channelStateEvent.getValue() != null) {
        Log.debug(headerLog + "BaseProtocolForwarder do channel bound");
      } else {
        Log.debug(headerLog + "BaseProtocolForwarder do channel unbound");
      }
      break;
    case CONNECTED:
      if (channelStateEvent.getValue() != null) {
        Log.debug(headerLog + "BaseProtocolForwarder do channel connected");
        channelConnected(channelStateEvent.getChannel());
      } else {
        Log.debug(headerLog + "BaseProtocolForwarder do channel disconnected");
        channelDisconnected(channelStateEvent.getChannel());
      }
      break;
    case INTEREST_OPS:
      Log.debug(headerLog + "BaseProtocolForwarder do channel interest changed");
      channelInterestChanged(channelStateEvent.getChannel());
      break;
    } // switch (channelStateEvent.getState())
    result = true;
    return result;
  }
  
  private boolean forwardMessage(ChannelHandlerContext channelHandlerContext,
      MessageEvent messageEvent) {
    boolean result = false;
    
    if (messageEvent == null) {
      // no log required
      return result;
    }
    
    // there shall be forwarder
    
    String headerLog = headerLog(messageEvent.getChannel());
    
    Object objectMessage = messageEvent.getMessage();
    if (objectMessage == null) {
      Log.debug(headerLog + "BaseProtocolForwarder failed to handle upstream "
          + ", found null object message");
      return result;
    }
    
    ChannelBuffer channelBuffer = null;
    if (objectMessage instanceof ChannelBuffer) {
      channelBuffer = (ChannelBuffer) objectMessage;
    }
    
    if (channelBuffer == null) {
      Log.debug(headerLog + "BaseProtocolForwarder failed to handle upstream "
          + ", found null channel buffer");
      return result;
    }
    
    // copied as a new channel buffer , not sure why ?
    // but this is the workaround otherwise it will not recognized
    ChannelBuffer channelBufferNew = ChannelBuffers.copiedBuffer(channelBuffer);
    
    Log.debug(headerLog + "BaseProtocolForwarder send message to " + remoteHost
        + ":" + remotePort + " , hex = "
        + ChannelBuffers.hexDump(channelBuffer));
    
    result = forwardMessage(messageEvent.getChannel(), channelBufferNew);
    
    return result;
  }
  
  protected String headerLog(Channel channel) {
    String headerLog = "";
    if (channel != null) {
      headerLog = headerLog.concat(Log.header(channel));
    }
    headerLog = headerLog.concat("[").concat(getProtocolName()).concat("] ");
    return headerLog;
  }
  
  protected abstract void channelConnected(Channel channel);
  
  protected abstract void channelDisconnected(Channel channel);
  
  protected abstract void channelInterestChanged(Channel channel);
  
  protected abstract boolean forwardMessage(Channel channel,
      ChannelBuffer channelBuffer);
  
  protected abstract void exceptionCaught(ExceptionEvent exceptionEvent,
      Channel channel);
  
}
