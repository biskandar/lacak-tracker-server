package org.traccar.protocol;

import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.frame.DelimiterBasedFrameDecoder;

public class G15cFrameDecoder extends DelimiterBasedFrameDecoder {
  
  private static final int MAX_FRAME_LENGTH = 1024;
  
  private static final boolean STRIP_DELIMITER = true;
  
  // '*' = 0x2a , '#' = 0x23
  
  private static final byte[] delimiter1 = { (byte) 0x2a, (byte) 0x23 };
  private static final byte[] delimiter2 = { (byte) 0x2a };
  private static final byte[] delimiter3 = { (byte) 0x23 };
  
  public G15cFrameDecoder() {
    super(MAX_FRAME_LENGTH, STRIP_DELIMITER, ChannelBuffers
        .wrappedBuffer(delimiter1), ChannelBuffers.wrappedBuffer(delimiter2),
        ChannelBuffers.wrappedBuffer(delimiter3));
  }
  
}
