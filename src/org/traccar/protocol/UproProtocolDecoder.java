/*
 * Copyright 2012 - 2015 Anton Tananaev (anton.tananaev@gmail.com)
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
import java.util.regex.Pattern;

import org.jboss.netty.channel.Channel;
import org.traccar.BaseProtocolDecoder;
import org.traccar.helper.DateBuilder;
import org.traccar.helper.Parser;
import org.traccar.helper.PatternBuilder;
import org.traccar.model.Position;

public class UproProtocolDecoder extends BaseProtocolDecoder {
  
  public UproProtocolDecoder(UproProtocol protocol) {
    super(protocol);
  }
  
  private static final Pattern PATTERN = new PatternBuilder().text("*AI20")
      .number("(d+),") // device id
      .expression("A.+&A").number("(dd)(dd)(dd)") // time
      .number("(dd)(dd)(dddd)") // latitude
      .number("(ddd)(dd)(dddd)") // longitude
      .number("d{5}").number("(dd)(dd)(dd)") // date
      .expression("&B").any().compile();
  
  @Override
  protected Object decode(Channel channel, SocketAddress remoteAddress,
      Object msg) throws Exception {
    
    Parser parser = new Parser(PATTERN, (String) msg);
    if (!parser.matches()) {
      return null;
    }
    
    Position position = new Position();
    position.setProtocol(getProtocolName());
    
    if (!identify(parser.next(), channel, remoteAddress)) {
      return null;
    }
    position.setDeviceId(getDeviceId());
    
    DateBuilder dateBuilder = new DateBuilder().setTime(parser.nextInt(),
        parser.nextInt(), parser.nextInt());
    
    position.setValid(true);
    position.setLatitude(parser
        .nextCoordinate(Parser.CoordinateFormat.DEG_MIN_MIN));
    position.setLongitude(parser
        .nextCoordinate(Parser.CoordinateFormat.DEG_MIN_MIN));
    
    dateBuilder.setDateReverse(parser.nextInt(), parser.nextInt(),
        parser.nextInt());
    position.setTime(dateBuilder.getDate());
    
    return position;
  }
  
}
