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
package org.traccar.protocol;

import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.regex.Pattern;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.traccar.BaseProtocolDecoder;
import org.traccar.helper.BcdUtil;
import org.traccar.helper.BitUtil;
import org.traccar.helper.DateBuilder;
import org.traccar.helper.Parser;
import org.traccar.helper.PatternBuilder;
import org.traccar.model.Position;

public class H02ProtocolDecoder extends BaseProtocolDecoder {
  
  public H02ProtocolDecoder(H02Protocol protocol) {
    super(protocol);
  }
  
  private static double readCoordinate(ChannelBuffer buf, boolean lon) {
    
    int degrees = BcdUtil.readInteger(buf, 2);
    if (lon) {
      degrees = degrees * 10 + (buf.getUnsignedByte(buf.readerIndex()) >> 4);
    }
    
    double result = 0;
    if (lon) {
      result = buf.readUnsignedByte() & 0x0f;
    }
    
    int length = 6;
    if (lon) {
      length = 5;
    }
    
    result = result * 10 + BcdUtil.readInteger(buf, length) * 0.0001;
    
    result /= 60;
    result += degrees;
    
    return result;
  }
  
  private void processStatus(Position position, long status) {
    if (!BitUtil.check(status, 0) || !BitUtil.check(status, 1)
        || !BitUtil.check(status, 3) || !BitUtil.check(status, 4)) {
      position.set(Position.KEY_ALARM, true);
    }
    position.set(Position.KEY_IGNITION, !BitUtil.check(status, 10));
    position.set(Position.KEY_STATUS, status);
  }
  
  private Position decodeBinary(ChannelBuffer buf, Channel channel,
      SocketAddress remoteAddress) {
    
    Position position = new Position();
    position.setProtocol(getProtocolName());
    
    buf.readByte(); // marker
    
    if (!identify(ChannelBuffers.hexDump(buf.readBytes(5)), channel,
        remoteAddress)) {
      return null;
    }
    position.setDeviceId(getDeviceId());
    
    DateBuilder dateBuilder = new DateBuilder()
        .setHour(BcdUtil.readInteger(buf, 2))
        .setMinute(BcdUtil.readInteger(buf, 2))
        .setSecond(BcdUtil.readInteger(buf, 2))
        .setDay(BcdUtil.readInteger(buf, 2))
        .setMonth(BcdUtil.readInteger(buf, 2))
        .setYear(BcdUtil.readInteger(buf, 2));
    position.setTime(dateBuilder.getDate());
    
    double latitude = readCoordinate(buf, false);
    position.set(Position.KEY_POWER, buf.readByte());
    double longitude = readCoordinate(buf, true);
    
    int flags = buf.readUnsignedByte() & 0x0f;
    position.setValid((flags & 0x02) != 0);
    if ((flags & 0x04) == 0) {
      latitude = -latitude;
    }
    if ((flags & 0x08) == 0) {
      longitude = -longitude;
    }
    
    position.setLatitude(latitude);
    position.setLongitude(longitude);
    
    position.setSpeed(BcdUtil.readInteger(buf, 3));
    position.setCourse((buf.readUnsignedByte() & 0x0f) * 100.0
        + BcdUtil.readInteger(buf, 2));
    
    processStatus(position, buf.readUnsignedInt());
    return position;
  }
  
  private static final Pattern PATTERN = new PatternBuilder().text("*")
      .expression("..,") // manufacturer
      .number("(d+),") // imei
      .number("Vd,") // version?
      .any().number("(?:(dd)(dd)(dd))?,") // time
      .expression("([AV])?,") // validity
      .groupBegin().number("-(d+)-(d+.d+),") // latitude
      .or().number("(d+)(dd.d+),") // latitude
      .groupEnd().expression("([NS]),").groupBegin().number("-(d+)-(d+.d+),") // longitude
      .or().number("(d+)(dd.d+),") // longitude
      .groupEnd().expression("([EW]),").number("(d+.?d*),") // speed
      .number("(d+.?d*)?,") // course
      .number("(?:(dd)(dd)(dd))?,") // date (ddmmyy)
      .number("(x{8})") // status
      .any().compile();
  
  private Position decodeText(String sentence, Channel channel,
      SocketAddress remoteAddress) {
    
    Parser parser = new Parser(PATTERN, sentence);
    if (!parser.matches()) {
      return null;
    }
    
    Position position = new Position();
    position.setProtocol(getProtocolName());
    
    if (!identify(parser.next(), channel, remoteAddress)) {
      return null;
    }
    position.setDeviceId(getDeviceId());
    
    DateBuilder dateBuilder = new DateBuilder();
    if (parser.hasNext(3)) {
      dateBuilder.setTime(parser.nextInt(), parser.nextInt(), parser.nextInt());
    }
    
    if (parser.hasNext()) {
      position.setValid(parser.next().equals("A"));
    }
    
    if (parser.hasNext(2)) {
      position.setLatitude(-parser.nextCoordinate());
    }
    if (parser.hasNext(2)) {
      position.setLatitude(parser.nextCoordinate());
    }
    
    if (parser.hasNext(2)) {
      position.setLongitude(-parser.nextCoordinate());
    }
    if (parser.hasNext(2)) {
      position.setLongitude(parser.nextCoordinate());
    }
    
    position.setSpeed(parser.nextDouble());
    position.setCourse(parser.nextDouble());
    
    if (parser.hasNext(3)) {
      dateBuilder.setDateReverse(parser.nextInt(), parser.nextInt(),
          parser.nextInt());
      position.setTime(dateBuilder.getDate());
    } else {
      position.setTime(new Date());
    }
    
    processStatus(position, parser.nextLong(16));
    
    return position;
  }
  
  @Override
  protected Object decode(Channel channel, SocketAddress remoteAddress,
      Object msg) throws Exception {
    
    ChannelBuffer buf = (ChannelBuffer) msg;
    String marker = buf.toString(0, 1, StandardCharsets.US_ASCII);
    
    // handle X mode?
    
    if (marker.equals("*")) {
      return decodeText(buf.toString(StandardCharsets.US_ASCII), channel,
          remoteAddress);
    } else if (marker.equals("$")) {
      return decodeBinary(buf, channel, remoteAddress);
    }
    
    return null;
  }
  
}
