/*
 * Copyright 2015 Anton Tananaev (anton.tananaev@gmail.com)
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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.xml.bind.DatatypeConverter;

import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.string.StringEncoder;
import org.traccar.database.ActiveDevice;
import org.traccar.model.Command;

public abstract class BaseProtocol implements Protocol {
  
  private final String name;
  private final Set<String> supportedCommands = new HashSet<>();
  
  public BaseProtocol(String name) {
    this.name = name;
  }
  
  @Override
  public String getName() {
    return name;
  }
  
  public void setSupportedCommands(String... commands) {
    supportedCommands.addAll(Arrays.asList(commands));
    supportedCommands.add(Command.TYPE_CUSTOM);
  }
  
  @Override
  public Collection<String> getSupportedCommands() {
    return supportedCommands;
  }
  
  @Override
  public void sendCommand(ActiveDevice activeDevice, Command command) {
    if (command.getType().equals(Command.TYPE_CUSTOM)) {
      String data = (String) command.getAttributes().get(Command.KEY_DATA);
      if (activeDevice.getChannel().getPipeline().get(StringEncoder.class) != null) {
        activeDevice.write(data);
      } else {
        activeDevice.write(ChannelBuffers.wrappedBuffer(DatatypeConverter
            .parseHexBinary(data)));
      }
    } else {
      if (!supportedCommands.contains(command.getType())) {
        throw new RuntimeException("Command " + command.getType()
            + " is not supported in protocol " + getName());
      }
      activeDevice.write(command);
    }
  }
  
}
