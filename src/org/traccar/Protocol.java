package org.traccar;

import java.util.Collection;
import java.util.List;

import org.traccar.database.ActiveDevice;
import org.traccar.model.Command;

public interface Protocol {
  
  String getName();
  
  Collection<String> getSupportedCommands();
  
  void sendCommand(ActiveDevice activeDevice, Command command);
  
  void initTrackerServers(List<TrackerServer> serverList);
  
}
