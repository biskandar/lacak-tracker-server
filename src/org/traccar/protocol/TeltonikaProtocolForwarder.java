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
  
  private final Object trafficLock = new Object();
  
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
          new TeltonikaProtocolForwarderOutboundHandler(headerLog,
              inboundChannel));
      
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
      
      // ...
      
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
      
      Log.debug(headerLog + "TeltonikaProtocolForwarder caught exception "
          + exceptionEvent);
      
      // ...
      
    } catch (Exception e) {
      Log.warning(headerLog
          + "TeltonikaProtocolForwarder failed exception caught , " + e);
    }
  }
  
  private class TeltonikaProtocolForwarderOutboundHandler extends
      SimpleChannelUpstreamHandler {
    
    private final String headerLog;
    private final Channel inboundChannel;
    
    public TeltonikaProtocolForwarderOutboundHandler(String headerLog,
        Channel inboundChannel) {
      this.headerLog = headerLog;
      this.inboundChannel = inboundChannel;
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
      try {
        Log.debug(headerLog
            + "TeltonikaProtocolForwarderOutboundHandler channel disconnected");
        
        Log.debug(headerLog
            + "TeltonikaProtocolForwarderOutboundHandler closing inbound channel");
        
        // close on flush for inbound channel
        closeOnFlush(inboundChannel);
        
      } catch (Exception e) {
        Log.warning(headerLog
            + "TeltonikaProtocolForwarderOutboundHandler failed channel disconnected , "
            + e);
      }
    }
    
    @Override
    public void messageReceived(ChannelHandlerContext channelHandlerContext,
        final MessageEvent messageEvent) {
      ChannelBuffer channelBuffer = (ChannelBuffer) messageEvent.getMessage();
      Log.debug(headerLog
          + "TeltonikaProtocolForwarderOutboundHandler message received : "
          + ChannelBuffers.hexDump(channelBuffer));
      
      // has command inside ?
      
      String commandText = TeltonikaProtocol.readCommandText(ChannelBuffers
          .copiedBuffer(channelBuffer));
      if ((commandText == null) || (commandText.equals(""))) {
        return;
      }
      
      // if found command than forward back to inbound channel
      
      synchronized (trafficLock) {
        Log.debug(headerLog
            + "TeltonikaProtocolForwarderOutboundHandler forward command : "
            + commandText);
        // forward to inbound
        write(inboundChannel, ChannelBuffers.copiedBuffer(channelBuffer));
      }
      
    }
    
    @Override
    public void channelInterestChanged(
        ChannelHandlerContext channelHandlerContext,
        ChannelStateEvent channelStateEvent) {
      // ... nothing to do
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext channelHandlerContext,
        ExceptionEvent exceptionEvent) {
      Log.debug(headerLog
          + "TeltonikaProtocolForwarderOutboundHandler caught exception "
          + exceptionEvent);
    }
    
  } // private class TeltonikaProtocolForwarderOutboundHandler
  
}
