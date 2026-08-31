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
package com.soulfiremc.mod.mixin.soulfire.botfixes;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.authlib.minecraft.client.MinecraftClient;
import com.soulfiremc.server.proxy.ProxyAuthenticator;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;

@Mixin(MinecraftClient.class)
public class MixinMinecraftClient {
  @Shadow
  @Final
  private Proxy proxy;

  @Inject(method = "createUrlConnection", at = @At("RETURN"))
  private void setProxyAuthenticator(URL url, CallbackInfoReturnable<HttpURLConnection> cir) {
    var authenticator = ProxyAuthenticator.forProxy(proxy);
    if (authenticator != null) {
      // Separate HTTP credentials and keep-alive connections for equal proxy endpoints.
      cir.getReturnValue().setAuthenticator(authenticator);
    }
  }

  @WrapMethod(method = "withBody")
  private HttpURLConnection authenticateRequestBody(
    HttpURLConnection connection, String method, byte[] body, Operation<HttpURLConnection> original) {
    return ProxyAuthenticator.withProxy(proxy, () -> original.call(connection, method, body));
  }

  @WrapMethod(method = "readServiceResponse")
  private <T> MinecraftClient.ServiceResponse<T> authenticateResponse(
    URL url, Class<T> responseClass, HttpURLConnection connection, @Nullable String cachedEtag,
    Operation<MinecraftClient.ServiceResponse<T>> original) {
    return ProxyAuthenticator.withProxy(proxy, () -> original.call(url, responseClass, connection, cachedEtag));
  }
}
