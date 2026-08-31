/*
 * SoulFire
 * Copyright (C) 2026  AlexProgrammerDE
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.soulfiremc.server.proxy;

import com.mojang.authlib.minecraft.client.MinecraftClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Authenticator;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ResourceLock("java.net.Authenticator")
final class ProxyAuthenticatorIT {
  @Test
  @Timeout(30)
  void isolatesAuthlibCredentialsOnSharedThreadsAndKeepAliveConnections() throws Exception {
    var previousAuthenticator = Authenticator.getDefault();
    Authenticator.setDefault(new ProxyAuthenticator());
    try (var server = new SocksServer(Map.of("first", "first-secret", "second", "second-secret"));
         var workers = Executors.newFixedThreadPool(4)) {
      var first = MinecraftClient.unauthenticated(ProxyAuthenticator.createProxy(
        new SFProxy(ProxyType.SOCKS5, server.address(), "first", "first-secret")));
      var second = MinecraftClient.unauthenticated(ProxyAuthenticator.createProxy(
        new SFProxy(ProxyType.SOCKS5, server.address(), "second", "second-secret")));
      var url = URI.create("http://session.invalid/session/minecraft/join").toURL();

      // Alternate identities at one URL while the proxy keeps HTTP connections alive.
      assertEquals("first", workers.submit(() -> first.get(url, String.class)).get(10, TimeUnit.SECONDS));
      assertEquals("second", workers.submit(() -> second.post(url, Map.of(), String.class)).get(10, TimeUnit.SECONDS));
      assertEquals("first", workers.submit(() -> first.post(url, String.class)).get(10, TimeUnit.SECONDS));

      var requests = new ArrayList<Future<?>>();
      for (var request = 0; request < 12; request++) {
        requests.add(workers.submit(() -> assertEquals("first", first.get(url, String.class))));
        requests.add(workers.submit(() -> assertEquals("second", second.post(url, Map.of(), String.class))));
      }
      for (var request : requests) {
        request.get(10, TimeUnit.SECONDS);
      }
    } finally {
      Authenticator.setDefault(previousAuthenticator);
    }
  }

  private static final class SocksServer implements AutoCloseable {
    private final ServerSocket listener;
    private final Map<String, String> credentials;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<Future<?>> tasks = new ConcurrentLinkedQueue<>();
    private volatile boolean closed;

    private SocksServer(Map<String, String> credentials) throws IOException {
      this.credentials = credentials;
      listener = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
      tasks.add(executor.submit(() -> {
        while (!closed) {
          try {
            var socket = listener.accept();
            socket.setSoTimeout(10_000);
            sockets.add(socket);
            tasks.add(executor.submit(() -> {
              serve(socket);
              return null;
            }));
          } catch (IOException e) {
            if (!closed) {
              throw e;
            }
          }
        }
        return null;
      }));
    }

    private InetSocketAddress address() {
      return new InetSocketAddress(listener.getInetAddress(), listener.getLocalPort());
    }

    private void serve(Socket socket) throws IOException {
      try (socket) {
        var input = new DataInputStream(socket.getInputStream());
        var output = socket.getOutputStream();
        assertEquals(5, input.readUnsignedByte());
        input.skipNBytes(input.readUnsignedByte());
        output.write(new byte[]{5, 2});
        output.flush();

        assertEquals(1, input.readUnsignedByte());
        var username = new String(input.readNBytes(input.readUnsignedByte()), StandardCharsets.ISO_8859_1);
        var password = new String(input.readNBytes(input.readUnsignedByte()), StandardCharsets.ISO_8859_1);
        assertEquals(credentials.get(username), password);
        output.write(new byte[]{1, 0});
        output.flush();

        assertEquals(5, input.readUnsignedByte());
        assertEquals(1, input.readUnsignedByte());
        input.readUnsignedByte();
        input.skipNBytes(switch (input.readUnsignedByte()) {
          case 1 -> 4;
          case 3 -> input.readUnsignedByte();
          case 4 -> 16;
          default -> throw new IOException("Unsupported SOCKS address type");
        });
        input.skipNBytes(2);
        output.write(new byte[]{5, 0, 0, 1, 127, 0, 0, 1, 0, 0});
        output.flush();

        while (readLine(input) != null) {
          var contentLength = 0;
          String header;
          while ((header = readLine(input)) != null && !header.isEmpty()) {
            var separator = header.indexOf(':');
            if (separator > 0 && header.substring(0, separator).equalsIgnoreCase("Content-Length")) {
              contentLength = Integer.parseInt(header.substring(separator + 1).trim());
            }
          }
          input.skipNBytes(contentLength);
          var body = ("\"" + username + "\"").getBytes(StandardCharsets.UTF_8);
          output.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "
            + body.length + "\r\nConnection: keep-alive\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
          output.write(body);
          output.flush();
        }
      } catch (IOException e) {
        if (!closed) {
          throw e;
        }
      } finally {
        sockets.remove(socket);
      }
    }

    private static String readLine(DataInputStream input) throws IOException {
      var line = new StringBuilder();
      int value;
      while ((value = input.read()) != -1) {
        if (value == '\n') {
          return line.toString();
        }
        if (value != '\r') {
          line.append((char) value);
        }
      }
      return null;
    }

    @Override
    public void close() throws Exception {
      closed = true;
      listener.close();
      for (var socket : sockets) {
        socket.close();
      }
      executor.close();
      for (var task : tasks) {
        task.get(10, TimeUnit.SECONDS);
      }
    }
  }
}
