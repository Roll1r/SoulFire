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

import org.junit.jupiter.api.Test;

import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ProxyAuthenticatorTest {
  private static final InetSocketAddress ENDPOINT = new InetSocketAddress("127.0.0.1", 1080);

  @Test
  void distinguishesProxyReferencesAndRestoresNestedScopesAfterFailure() {
    var first = ProxyAuthenticator.createProxy(new SFProxy(ProxyType.SOCKS5, ENDPOINT, "first", "first-secret"));
    var second = ProxyAuthenticator.createProxy(new SFProxy(ProxyType.SOCKS5, ENDPOINT, "second", "second-secret"));
    var unregistered = new Proxy(Proxy.Type.SOCKS, ENDPOINT);
    var authenticator = new ProxyAuthenticator();

    ProxyAuthenticator.withProxy(first, () -> {
      assertCredentials("first", "first-secret", request(authenticator, ENDPOINT, "SOCKS5", Authenticator.RequestorType.SERVER));
      assertThrows(IllegalStateException.class, () -> ProxyAuthenticator.withProxy(second, () -> {
        assertCredentials("second", "second-secret", request(authenticator, ENDPOINT, "SOCKS5", Authenticator.RequestorType.SERVER));
        throw new IllegalStateException();
      }));
      ProxyAuthenticator.withProxy(unregistered, () -> {
        assertNull(request(authenticator, ENDPOINT, "SOCKS5", Authenticator.RequestorType.SERVER));
        return null;
      });
      assertCredentials("first", "first-secret", request(authenticator, ENDPOINT, "SOCKS5", Authenticator.RequestorType.SERVER));
      return null;
    });

    assertNull(request(authenticator, ENDPOINT, "SOCKS5", Authenticator.RequestorType.SERVER));
  }

  @Test
  void rejectsOriginAuthenticationAndUnrelatedProxyEndpoints() {
    var proxy = ProxyAuthenticator.createProxy(new SFProxy(ProxyType.HTTP, ENDPOINT, "user", "secret"));
    var authenticator = ProxyAuthenticator.forProxy(proxy);

    assertCredentials("user", "secret", request(authenticator, ENDPOINT, "https", Authenticator.RequestorType.PROXY));
    assertNull(request(authenticator, ENDPOINT, "https", Authenticator.RequestorType.SERVER));
    assertNull(request(authenticator, ENDPOINT, "SOCKS5", Authenticator.RequestorType.SERVER));
    assertNull(request(authenticator, new InetSocketAddress("127.0.0.2", ENDPOINT.getPort()), "http", Authenticator.RequestorType.PROXY));
    assertNull(request(authenticator, new InetSocketAddress(ENDPOINT.getAddress(), ENDPOINT.getPort() + 1), "http", Authenticator.RequestorType.PROXY));
  }

  private static PasswordAuthentication request(
    Authenticator authenticator, InetSocketAddress endpoint, String protocol, Authenticator.RequestorType requestorType) {
    return authenticator.requestPasswordAuthenticationInstance(endpoint.getHostString(), endpoint.getAddress(),
      endpoint.getPort(), protocol, null, null, null, requestorType);
  }

  private static void assertCredentials(String username, String password, PasswordAuthentication authentication) {
    assertEquals(username, authentication.getUserName());
    assertArrayEquals(password.toCharArray(), authentication.getPassword());
  }
}
