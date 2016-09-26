package org.traccar.protocol;

import java.net.InetSocketAddress;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.traccar.BaseProtocolForwarder;
import org.traccar.GlobalChannelFactory;
import org.traccar.helper.Log;

public class Gt06ProtocolForwarder extends BaseProtocolForwarder {
  
  private volatile Channel outboundChannel;
  
  public Gt06ProtocolForwarder(Gt06Protocol protocol) {
    super(protocol);
  }
  
  @Override
  protected void channelConnected(Channel channel) {
    final String headerLog = "[" + getProtocolName() + "] ";
    try {
      
      // suspend incoming traffic until client bootstrap connected
      // or failed to attempt to the remote host
      final Channel inboundChannel = channel;
      inboundChannel.setReadable(false);
      
      // log it
      Log.debug(headerLog
          + "Gt06ProtocolForwarder setup client bootstrap , with "
          + ": handler = ForwarderOutboundHandler , remoteHost = "
          + getRemoteHost() + " , remotePort = " + getRemotePort());
      
      // setup client bootstrap
      ClientBootstrap clientBootstrap = new ClientBootstrap();
      clientBootstrap.setFactory(GlobalChannelFactory.getClientFactory());
      
      // add forwarder outbound handler
      clientBootstrap.getPipeline().addLast("gt06ForwarderOutboundHandler",
          new Gt06ProtocolForwarderOutboundHandler());
      
      // connect to remote host
      ChannelFuture channelFuture = clientBootstrap
          .connect(new InetSocketAddress(getRemoteHost(), getRemotePort()));
      
      // setup outbound channel
      outboundChannel = channelFuture.getChannel();
      
      // validate the future connection
      channelFuture.addListener(new ChannelFutureListener() {
        public void operationComplete(ChannelFuture channelFuture) {
          Log.debug(headerLog + "Gt06ProtocolForwarder client bootstrap "
              + "on operation complete : isSuccess = "
              + channelFuture.isSuccess());
          // resume incoming traffic because the client bootstrap
          // is connected and ready
          inboundChannel.setReadable(true);
        }
      });
      
    } catch (Exception e) {
      Log.warning(headerLog
          + "Gt06ProtocolForwarder failed channel connected , " + e);
    }
  }
  
  @Override
  protected void channelDisconnected(Channel channel) {
    final String headerLog = "[" + getProtocolName() + "] ";
    try {
      
      // log it
      Log.debug(headerLog + "Gt06ProtocolForwarder closing outbound channel");
      
      // close on flush for outbound channel
      closeOnFlush(outboundChannel);
      
    } catch (Exception e) {
      Log.warning(headerLog
          + "Gt06ProtocolForwarder failed channel disconnected , " + e);
    }
  }
  
  @Override
  protected void channelInterestChanged(Channel channel) {
    final String headerLog = "[" + getProtocolName() + "] ";
    try {
      
    } catch (Exception e) {
      Log.warning(headerLog
          + "Gt06ProtocolForwarder failed channel interest changed , " + e);
    }
  }
  
  @Override
  protected boolean forwardMessage(Channel channel, ChannelBuffer channelBuffer) {
    boolean result = false;
    String headerLog = "[" + getProtocolName() + "] ";
    try {
      
      // is outbound channel exist ?
      if (outboundChannel == null) {
        Log.warning(headerLog
            + "Gt06ProtocolForwarder failed to forward message "
            + ", found null outbound channel");
        return result;
      }
      
      // is outbound channel connected ?
      if (!outboundChannel.isConnected()) {
        Log.warning(headerLog
            + "Gt06ProtocolForwarder failed to forward message "
            + ", found disconnected outbound channel");
        return result;
      }
      
      // forward message
      write(outboundChannel, channelBuffer);
      
      // result as true
      result = true;
      
    } catch (Exception e) {
      Log.warning(headerLog + "Gt06ProtocolForwarder failed forward message , "
          + e);
    }
    return result;
  }
  
  @Override
  protected void exceptionCaught(ExceptionEvent exceptionEvent, Channel channel) {
    final String headerLog = "[" + getProtocolName() + "] ";
    try {
      
      Log.debug(headerLog + "Gt06ProtocolForwarder found exception "
          + exceptionEvent + " , close it");
      
      closeOnFlush(channel);
      
    } catch (Exception e) {
      Log.warning(headerLog
          + "Gt06ProtocolForwarder failed exception caught , " + e);
    }
  }
  
  private class Gt06ProtocolForwarderOutboundHandler extends
      SimpleChannelUpstreamHandler {
    
    public Gt06ProtocolForwarderOutboundHandler() {
      // ...
    }
    
    @Override
    public void channelConnected(ChannelHandlerContext channelHandlerContext,
        ChannelStateEvent channelStateEvent) throws Exception {
      Log.debug("[" + getProtocolName()
          + "] Gt06ProtocolForwarderOutboundHandler channel connected");
    }
    
    @Override
    public void channelDisconnected(
        ChannelHandlerContext channelHandlerContext,
        ChannelStateEvent channelStateEvent) throws Exception {
      Log.debug("[" + getProtocolName()
          + "] Gt06ProtocolForwarderOutboundHandler channel disconnected");
    }
    
    @Override
    public void messageReceived(ChannelHandlerContext channelHandlerContext,
        final MessageEvent messageEvent) {
      ChannelBuffer channelBuffer = (ChannelBuffer) messageEvent.getMessage();
      Log.debug("[" + getProtocolName()
          + "] Gt06ProtocolForwarderOutboundHandler message received : "
          + ChannelBuffers.hexDump(channelBuffer));
    }
    
    @Override
    public void channelInterestChanged(
        ChannelHandlerContext channelHandlerContext,
        ChannelStateEvent channelStateEvent) {
      Log.debug("[" + getProtocolName()
          + "] Gt06ProtocolForwarderOutboundHandler channel interest changed");
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext channelHandlerContext,
        ExceptionEvent exceptionEvent) {
      Log.debug("[" + getProtocolName()
          + "] Gt06ProtocolForwarderOutboundHandler exception caught , "
          + exceptionEvent);
      closeOnFlush(exceptionEvent.getChannel());
    }
    
  } // private class ForwarderOutboundHandler
  
  private static ChannelFuture write(Channel channel,
      ChannelBuffer channelBuffer) {
    ChannelFuture channelFuture = null;
    if (channel == null) {
      return channelFuture;
    }
    if (!channel.isConnected()) {
      return channelFuture;
    }
    if (channelBuffer == null) {
      return channelFuture;
    }
    channelFuture = channel.write(channelBuffer);
    return channelFuture;
  }
  
  private static boolean closeOnFlush(Channel channel) {
    boolean result = false;
    if (channel == null) {
      return result;
    }
    if (!channel.isConnected()) {
      return result;
    }
    ChannelFuture channelFuture = channel.write(ChannelBuffers.EMPTY_BUFFER);
    channelFuture.addListener(ChannelFutureListener.CLOSE);
    result = true;
    return result;
  }
  
}
