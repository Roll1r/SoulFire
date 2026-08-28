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
package com.soulfiremc.bootstrap.client.settings;

import com.google.gson.JsonPrimitive;
import com.soulfiremc.bootstrap.client.cli.SFCommandDefinition;
import com.soulfiremc.grpc.generated.AccountTypeCredentials;
import com.soulfiremc.grpc.generated.CredentialsAuthEnd;
import com.soulfiremc.grpc.generated.CredentialsAuthOneFailure;
import com.soulfiremc.grpc.generated.CredentialsAuthOneSuccess;
import com.soulfiremc.grpc.generated.CredentialsAuthResponse;
import com.soulfiremc.grpc.generated.InstanceConfig;
import com.soulfiremc.server.account.AuthType;
import com.soulfiremc.server.account.MinecraftAccount;
import com.soulfiremc.server.account.service.OfflineJavaData;
import com.soulfiremc.server.settings.instance.AccountSettings;
import com.soulfiremc.server.settings.lib.InstanceSettingsImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ClientSettingsManagerTest {
  @Test
  void uploadsAuthenticationSettingsBeforeAuthenticatingAccounts(@TempDir Path tempDirectory) throws IOException {
    var accountFile = tempDirectory.resolve("accounts.txt");
    Files.writeString(accountFile, "first\nsecond\n");
    var proxyFile = tempDirectory.resolve("proxies.txt");
    Files.writeString(proxyFile, "127.0.0.1:1080\n");

    var uploadedConfigs = new ArrayList<InstanceConfig>();
    var authenticatedAccount = new MinecraftAccount(
      AuthType.OFFLINE,
      OfflineJavaData.getOfflineUUID("first"),
      "first",
      new OfflineJavaData(),
      Map.of(),
      Map.of());
    var settingsManager = new ClientSettingsManager(request -> {
      assertEquals(AccountTypeCredentials.OFFLINE, request.getService());
      assertEquals(List.of("first", "second"), request.getPayloadList());
      assertEquals(1, uploadedConfigs.size());

      var authenticationConfig = InstanceSettingsImpl.Stem.fromProto(uploadedConfigs.getFirst());
      assertEquals(1, authenticationConfig.proxies().size());
      assertTrue(authenticationConfig.get(AccountSettings.USE_PROXIES_FOR_ACCOUNT_AUTH)
        .orElseThrow()
        .getAsBoolean());

      return List.of(
        CredentialsAuthResponse.newBuilder()
          .setOneSuccess(CredentialsAuthOneSuccess.newBuilder()
            .setAccount(authenticatedAccount.toProto())
            .build())
          .build(),
        CredentialsAuthResponse.newBuilder()
          .setOneFailure(CredentialsAuthOneFailure.getDefaultInstance())
          .build(),
        CredentialsAuthResponse.newBuilder()
          .setEnd(CredentialsAuthEnd.getDefaultInstance())
          .build())
        .iterator();
    });
    settingsManager.registerProvider(
      new PropertyKey("account", "use-proxies-for-account-auth"),
      () -> new JsonPrimitive(true));

    var commandDefinition = new SFCommandDefinition(null);
    new CommandLine(commandDefinition).parseArgs(
      "--account-file", accountFile.toString(),
      "--account-type", "OFFLINE",
      "--proxy-file", proxyFile.toString(),
      "--proxy-type", "SOCKS5");
    settingsManager.commandDefinition(commandDefinition);

    settingsManager.configureInstance(UUID.randomUUID(), uploadedConfigs::add);

    assertEquals(2, uploadedConfigs.size());
    assertEquals(0, uploadedConfigs.getFirst().getAccountsCount());
    assertEquals(1, uploadedConfigs.getLast().getAccountsCount());
    assertEquals(1, uploadedConfigs.getLast().getProxiesCount());
  }
}
