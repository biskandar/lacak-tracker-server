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

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.reflections.Reflections;
import org.traccar.helper.Log;

public class ServerManager {
  
  private final List<TrackerServer> serverList = new LinkedList<>();
  
  public ServerManager() throws Exception {
    
    List<String> listClassNames = new LinkedList<>();
    
    String packageName = "org.traccar.protocol";
    Log.debug("Setup server manager : defined package name = " + packageName);
    
    String packagePath = packageName.replace('.', '/');
    Log.debug("Setup server manager : defined package path = " + packagePath);
    
    Reflections reflections = new Reflections(packageName);
    Set<Class<? extends BaseProtocol>> setProtocolClasses = reflections
        .getSubTypesOf(BaseProtocol.class);
    Log.debug("Setup server manager : loaded set protocol classes : size "
        + setProtocolClasses.size());
    
    for (Class<? extends BaseProtocol> protocolClass : setProtocolClasses) {
      Log.info("Create protocol object from class name = " + protocolClass);
      if (BaseProtocol.class.isAssignableFrom(protocolClass)) {
        BaseProtocol baseProtocol = (BaseProtocol) protocolClass.newInstance();
        initProtocolServer(baseProtocol);
      }
    }
    
  }
  
  public void start() {
    for (TrackerServer server : serverList) {
      server.start();
    }
  }
  
  public void stop() {
    for (TrackerServer server : serverList) {
      server.stop();
    }
    
    // Release resources
    GlobalChannelFactory.release();
    GlobalTimer.release();
  }
  
  private void initProtocolServer(final Protocol protocol) {
    if (Context.getConfig().hasKey(protocol.getName() + ".port")) {
      protocol.initTrackerServers(serverList);
    }
  }
  
}
