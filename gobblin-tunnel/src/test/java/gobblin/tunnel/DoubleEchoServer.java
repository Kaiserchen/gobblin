/*
 * Copyright (C) 2014-2016 LinkedIn Corp. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied.
 */

package gobblin.tunnel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * A TCP server that reads a line from a socket, trims it, and echoes it back twice on the same line.
 *
 * @author kkandekar@linkedin.com
 */
class DoubleEchoServer extends MockServer {
  private final long delay;

  public DoubleEchoServer(long delay) {
    this.delay = delay;
  }

  @Override
  void handleClientSocket(Socket clientSocket) throws IOException {
    try {
      BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
      PrintWriter out = new PrintWriter(clientSocket.getOutputStream());
      String line = in.readLine();
      while (line != null && isServerRunning()) {
        if (delay > 0) {
          sleepQuietly(delay);
        }
        out.println(line + " " + line);
        out.flush();
        line = in.readLine();
      }
    } catch (IOException ignored) {
      // This gets thrown when the proxy abruptly closes a connection
    } finally {
      clientSocket.close();
    }
  }
}
