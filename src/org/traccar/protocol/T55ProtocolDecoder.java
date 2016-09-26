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
import java.util.Date;
import java.util.regex.Pattern;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.socket.DatagramChannel;
import org.traccar.BaseProtocolDecoder;
import org.traccar.helper.DateBuilder;
import org.traccar.helper.Parser;
import org.traccar.helper.PatternBuilder;
import org.traccar.model.Position;

public class T55ProtocolDecoder extends BaseProtocolDecoder {
  
  public T55ProtocolDecoder(T55Protocol protocol) {
    super(protocol);
  }
  
  private static final Pattern PATTERN_GPRMC = new PatternBuilder()
      .text("$GPRMC,").number("(dd)(dd)(dd).?d*,") // time
      .expression("([AV]),") // validity
      .number("(dd)(dd.d+),") // latitude
      .expression("([NS]),").number("(d{2,3})(dd.d+),") // longitude
      .expression("([EW]),").number("(d+.?d*)?,") // speed
      .number("(d+.?d*)?,") // course
      .number("(dd)(dd)(dd),") // date
      .expression("[^*]+").text("*").expression("[^,]+").number(",(d+)") // satellites
      .number(",(d+)") // imei
      .number(",(d+)").optional(3).any().compile();
  
  private static final Pattern PATTERN_GPGGA = new PatternBuilder()
      .text("$GPGGA,").number("(dd)(dd)(dd).?d*,") // time
      .number("(d+)(dd.d+),") // latitude
      .expression("([NS]),").number("(d+)(dd.d+),") // longitude
      .expression("([EW]),").any().compile();
  
  private static final Pattern PATTERN_GPRMA = new PatternBuilder()
      .text("$GPRMA,").expression("([AV]),") // validity
      .number("(dd)(dd.d+),") // latitude
      .expression("([NS]),").number("(ddd)(dd.d+),") // longitude
      .expression("([EW]),,,").number("(d+.?d*)?,") // speed
      .number("(d+.?d*)?,") // course
      .any().compile();
  
  private static final Pattern PATTERN_TRCCR = new PatternBuilder()
      .text("$TRCCR,").number("(dddd)(dd)(dd)") // date
      .number("(dd)(dd)(dd).?d*,") // time
      .expression("([AV]),") // validity
      .number("(-?d+.d+),") // latitude
      .number("(-?d+.d+),") // longitude
      .number("(d+.d+),") // speed
      .number("(d+.d+),") // course
      .number("(-?d+.d+),") // altitude
      .number("(d+.?d*),") // battery
      .any().compile();
  
  private Position position = null;
  
  private Position decodeGprmc(String sentence, SocketAddress remoteAddress,
      Channel channel) {
    
    if (channel != null && !(channel instanceof DatagramChannel)) {
      channel.write("OK1\r\n");
    }
    
    Parser parser = new Parser(PATTERN_GPRMC, sentence);
    if (!parser.matches()) {
      return null;
    }
    
    Position position = new Position();
    position.setProtocol(getProtocolName());
    
    if (hasDeviceId()) {
      position.setDeviceId(getDeviceId());
    }
    
    DateBuilder dateBuilder = new DateBuilder().setTime(parser.nextInt(),
        parser.nextInt(), parser.nextInt());
    
    position.setValid(parser.next().equals("A"));
    position.setLatitude(parser.nextCoordinate());
    position.setLongitude(parser.nextCoordinate());
    position.setSpeed(parser.nextDouble());
    position.setCourse(parser.nextDouble());
    
    dateBuilder.setDateReverse(parser.nextInt(), parser.nextInt(),
        parser.nextInt());
    position.setTime(dateBuilder.getDate());
    
    if (parser.hasNext(3)) {
      position.set(Position.KEY_SATELLITES, parser.next());
      if (!identify(parser.next(), channel, remoteAddress)) {
        return null;
      }
      position.setDeviceId(getDeviceId());
    }
    
    if (hasDeviceId()) {
      return position;
    } else {
      this.position = position; // save position
      return null;
    }
  }
  
  private Position decodeGpgga(String sentence) {
    
    Parser parser = new Parser(PATTERN_GPGGA, sentence);
    if (!parser.matches()) {
      return null;
    }
    
    Position position = new Position();
    position.setProtocol(getProtocolName());
    position.setDeviceId(getDeviceId());
    
    DateBuilder dateBuilder = new DateBuilder().setCurrentDate().setTime(
        parser.nextInt(), parser.nextInt(), parser.nextInt());
    position.setTime(dateBuilder.getDate());
    
    position.setValid(true);
    position.setLatitude(parser.nextCoordinate());
    position.setLongitude(parser.nextCoordinate());
    
    return position;
  }
  
  private Position decodeGprma(String sentence) {
    
    Parser parser = new Parser(PATTERN_GPRMA, sentence);
    if (!parser.matches()) {
      return null;
    }
    
    Position position = new Position();
    position.setProtocol(getProtocolName());
    position.setDeviceId(getDeviceId());
    
    position.setTime(new Date());
    position.setValid(parser.next().equals("A"));
    position.setLatitude(parser.nextCoordinate());
    position.setLongitude(parser.nextCoordinate());
    position.setSpeed(parser.nextDouble());
    position.setCourse(parser.nextDouble());
    
    return position;
  }
  
  private Position decodeTrccr(String sentence) {
    
    Parser parser = new Parser(PATTERN_TRCCR, sentence);
    if (!parser.matches()) {
      return null;
    }
    
    Position position = new Position();
    position.setProtocol(getProtocolName());
    position.setDeviceId(getDeviceId());
    
    DateBuilder dateBuilder = new DateBuilder().setDate(parser.nextInt(),
        parser.nextInt(), parser.nextInt()).setTime(parser.nextInt(),
        parser.nextInt(), parser.nextInt());
    position.setTime(dateBuilder.getDate());
    
    position.setValid(parser.next().equals("A"));
    position.setLatitude(parser.nextDouble());
    position.setLongitude(parser.nextDouble());
    position.setSpeed(parser.nextDouble());
    position.setCourse(parser.nextDouble());
    position.setAltitude(parser.nextDouble());
    
    position.set(Position.KEY_BATTERY, parser.next());
    
    return position;
  }
  
  @Override
  protected Object decode(Channel channel, SocketAddress remoteAddress,
      Object msg) throws Exception {
    
    String sentence = (String) msg;
    
    if (!sentence.startsWith("$") && sentence.contains("$")) {
      int index = sentence.indexOf("$");
      String id = sentence.substring(0, index);
      if (id.endsWith(",")) {
        id = id.substring(0, id.length() - 1);
      } else if (id.endsWith("/")) {
        id = id.substring(id.indexOf('/') + 1, id.length() - 1);
      }
      identify(id, channel, remoteAddress);
      sentence = sentence.substring(index);
    }
    
    if (sentence.startsWith("$PGID")) {
      identify(sentence.substring(6, sentence.length() - 3), channel,
          remoteAddress);
    } else if (sentence.startsWith("$PCPTI")) {
      identify(sentence.substring(7, sentence.indexOf(",", 7)), channel,
          remoteAddress);
    } else if (sentence.startsWith("IMEI")) {
      identify(sentence.substring(5, sentence.length()), channel, remoteAddress);
    } else if (sentence.startsWith("$GPFID")) {
      if (identify(sentence.substring(7, sentence.length()), channel,
          remoteAddress) && position != null) {
        Position position = this.position;
        position.setDeviceId(getDeviceId());
        this.position = null;
        return position;
      }
    } else if (sentence.matches("^[0-9A-F]+$")) {
      identify(sentence, channel, remoteAddress);
    } else if (sentence.startsWith("$GPRMC")) {
      return decodeGprmc(sentence, remoteAddress, channel);
    } else if (sentence.startsWith("$GPGGA") && hasDeviceId()) {
      return decodeGpgga(sentence);
    } else if (sentence.startsWith("$GPRMA") && hasDeviceId()) {
      return decodeGprma(sentence);
    } else if (sentence.startsWith("$TRCCR") && hasDeviceId()) {
      return decodeTrccr(sentence);
    }
    
    return null;
  }
  
}
