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

public class TeltonikaProtocolForwarder extends BaseProtocolForwarder {
  
  private final boolean connectionLess;
  private volatile Channel outboundChannel;
  
  public TeltonikaProtocolForwarder(TeltonikaProtocol protocol,
      boolean connectionLess) {
    super(protocol);
    this.connectionLess = connectionLess;
  }
  
  @Override
  protected void channelConnected(Channel channel) {
    final String headerLog = headerLog(channel);
    try {
      
      // suspend incoming traffic until client bootstrap connected
      // or failed to attempt to the remote host
      final Channel inboundChannel = channel;
      inboundChannel.setReadable(false);
      
      // log it
      Log.debug(headerLog
          + "TeltonikaProtocolForwarder setup client bootstrap , with "
          + ": handler = ForwarderOutboundHandler , remoteHost = "
          + getRemoteHost() + " , remotePort = " + getRemotePort()
          + " , connectionLess = " + connectionLess);
      
      // setup client bootstrap
      ClientBootstrap clientBootstrap = new ClientBootstrap();
      if (connectionLess) {
        clientBootstrap.setFactory(GlobalChannelFactory.getDatagramFactory());
      } else {
        clientBootstrap.setFactory(GlobalChannelFactory.getClientFactory());
      }
      
      // add forwarder outbound handler
      clientBootstrap.getPipeline().addLast(
          "teltonikaForwarderOutboundHandler",
          new TeltonikaProtocolForwarderOutboundHandler(headerLog));
      
      // connect to remote host
      ChannelFuture channelFuture = clientBootstrap
          .connect(new InetSocketAddress(getRemoteHost(), getRemotePort()));
      
      // setup outbound channel
      outboundChannel = channelFuture.getChannel();
      
      // validate the future connection
      channelFuture.addListener(new ChannelFutureListener() {
        public void operationComplete(ChannelFuture channelFuture) {
          Log.debug(headerLog + "TeltonikaProtocolForwarder client bootstrap "
              + "on operation complete : isSuccess = "
              + channelFuture.isSuccess());
          // resume incoming traffic because the client bootstrap
          // is connected and ready
          inboundChannel.setReadable(true);
        }
      });
      
    } catch (Exception e) {
      Log.warning(headerLog
          + "TeltonikaProtocolForwarder failed channel connected , " + e);
    }
  }
  
  @Override
  protected void channelDisconnected(Channel channel) {
    final String headerLog = headerLog(channel);
    try {
      
      // log it
      Log.debug(headerLog
          + "TeltonikaProtocolForwarder closing outbound channel");
      
      // close on flush for outbound channel
      closeOnFlush(outboundChannel);
      
    } catch (Exception e) {
      Log.warning(headerLog
          + "TeltonikaProtocolForwarder failed channel disconnected , " + e);
    }
  }
  
  @Override
  protected void channelInterestChanged(Channel channel) {
    final String headerLog = headerLog(channel);
    try {
      
    } catch (Exception e) {
      Log.warning(headerLog
          + "TeltonikaProtocolForwarder failed channel interest changed , " + e);
    }
  }
  
  @Override
  protected boolean forwardMessage(Channel channel, ChannelBuffer channelBuffer) {
    boolean result = false;
    final String headerLog = headerLog(channel);
    try {
      
      // is outbound channel exist ?
      if (outboundChannel == null) {
        Log.warning(headerLog
            + "TeltonikaProtocolForwarder failed to forward message "
            + ", found null outbound channel");
        return result;
      }
      
      // is outbound channel connected ?
      if (!outboundChannel.isConnected()) {
        Log.warning(headerLog
            + "TeltonikaProtocolForwarder failed to forward message "
            + ", found disconnected outbound channel");
        return result;
      }
      
      // forward message
      write(outboundChannel, channelBuffer);
      
      // result as true
      result = true;
      
    } catch (Exception e) {
      Log.warning(headerLog
          + "TeltonikaProtocolForwarder failed forward message , " + e);
    }
    return result;
  }
  
  @Override
  protected void exceptionCaught(ExceptionEvent exceptionEvent, Channel channel) {
    final String headerLog = headerLog(channel);
    try {
      
      Log.debug(headerLog + "TeltonikaProtocolForwarder found exception "
          + exceptionEvent + " , close it");
      
      closeOnFlush(channel);
      
    } catch (Exception e) {
      Log.warning(headerLog
          + "TeltonikaProtocolForwarder failed exception caught , " + e);
    }
  }
  
  private class TeltonikaProtocolForwarderOutboundHandler extends
      SimpleChannelUpstreamHandler {
    
    private final String headerLog;
    
    public TeltonikaProtocolForwarderOutboundHandler(String headerLog) {
      this.headerLog = headerLog;
    }
    
    @Override
    public void channelConnected(ChannelHandlerContext channelHandlerContext,
        ChannelStateEvent channelStateEvent) throws Exception {
      Log.debug(headerLog
          + "TeltonikaProtocolForwarderOutboundHandler channel connected");
    }
    
    @Override
    public void channelDisconnected(
        ChannelHandlerContext channelHandlerContext,
        ChannelStateEvent channelStateEvent) throws Exception {
      Log.debug(headerLog
          + "TeltonikaProtocolForwarderOutboundHandler channel disconnected");
    }
    
    @Override
    public void messageReceived(ChannelHandlerContext channelHandlerContext,
        final MessageEvent messageEvent) {
      ChannelBuffer channelBuffer = (ChannelBuffer) messageEvent.getMessage();
      Log.debug(headerLog
          + "TeltonikaProtocolForwarderOutboundHandler message received : "
          + ChannelBuffers.hexDump(channelBuffer));
    }
    
    @Override
    public void channelInterestChanged(
        ChannelHandlerContext channelHandlerContext,
        ChannelStateEvent channelStateEvent) {
      Log.debug(headerLog
          + "TeltonikaProtocolForwarderOutboundHandler channel interest changed");
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext channelHandlerContext,
        ExceptionEvent exceptionEvent) {
      Log.debug(headerLog
          + "TeltonikaProtocolForwarderOutboundHandler exception caught , "
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
