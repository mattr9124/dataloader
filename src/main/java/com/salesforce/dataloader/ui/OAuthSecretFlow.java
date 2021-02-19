/*
 * Copyright (c) 2015, salesforce.com, inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided
 * that the following conditions are met:
 *
 *    Redistributions of source code must retain the above copyright notice, this list of conditions and the
 *    following disclaimer.
 *
 *    Redistributions in binary form must reproduce the above copyright notice, this list of conditions and
 *    the following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 *    Neither the name of salesforce.com, inc. nor the names of its contributors may be used to endorse or
 *    promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.dataloader.ui;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.salesforce.dataloader.client.SimplePost;
import com.salesforce.dataloader.client.SimplePostFactory;
import com.salesforce.dataloader.config.Config;
import com.salesforce.dataloader.exception.ParameterLoadException;
import com.salesforce.dataloader.model.OAuthToken;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.widgets.Shell;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.Map;
import java.util.Optional;

/**
 * the oauth authorization_code. this is normally reserved for server to server communications as it involves storing
 * a secret. we provide it hear for users to create their own connected app for oauth. The advantage to this flow is
 * it prompts for authentication but not authorization (once it's been authorized at least once).
 */
public class OAuthSecretFlow extends OAuthFlow {
    protected static Logger logger = LogManager.getLogger(OAuthFlow.class);

    public OAuthSecretFlow(Shell parent, Config config) {
        super(parent, config);
    }

    @Override
    protected OAuthBrowserListener getOAuthBrowserListener(Shell shell, Browser browser, Config config) {
        return new OAuthSecretBrowserListener(browser, shell, config);
    }

    @Override
    public String getStartUrl(Config config) throws UnsupportedEncodingException {
        return getStartUrlImpl(config);
    }

    //SWT Components are not testable :(
    public static String getStartUrlImpl(Config config) throws UnsupportedEncodingException {
        return config.getString(Config.OAUTH_SERVER) +
                "/services/oauth2/authorize?response_type=code&display=popup&client_id=" +
                config.getString(Config.OAUTH_CLIENTID) + "&redirect_uri=" +
                URLEncoder.encode(config.getString(Config.OAUTH_REDIRECTURI), "UTF-8");
    }

    public static class OAuthSecretBrowserListener extends OAuthBrowserListener {

        public OAuthSecretBrowserListener(Browser browser, Shell shell, Config config) {
            super(browser, shell, config);
        }

        @Override
        public void changed(ProgressEvent progressEvent) {

        }

        @Override
        public void completed(ProgressEvent progressEvent) {
            String url = browser.getUrl();
            try {
                String code = handleInitialUrl(url);

                if (code != null) {
                    SimplePost client = handleSecondPost(code, config);
                    setReasonPhrase(client.getReasonPhrase());
                    setStatusCode(client.getStatusCode());
                    setResult(client.isSuccessful());
                    shell.close();
                    shell.dispose();
                }
            } catch (URISyntaxException | IOException | ParameterLoadException e) {
                doSimpleErrorHandling(url, e, logger);
            }
        }

        public static SimplePost handleSecondPost(String code, Config config) throws IOException, ParameterLoadException {
            String server = config.getString(Config.OAUTH_SERVER) + "/services/oauth2/token";
            SimplePost client = SimplePostFactory.getInstance(config, server,
                    new BasicNameValuePair("grant_type", "authorization_code"),
                    new BasicNameValuePair("code", code),
                    new BasicNameValuePair("client_id", config.getString(Config.OAUTH_CLIENTID)),
                    new BasicNameValuePair("client_secret", config.getString(Config.OAUTH_CLIENTSECRET)),
                    new BasicNameValuePair("redirect_uri", config.getString(Config.OAUTH_REDIRECTURI))
            );
            client.post();

            if (client.isSuccessful()) {

                StringBuilder builder = new StringBuilder();
                BufferedReader in = new BufferedReader(new InputStreamReader(client.getInput(), "UTF-8"));
                for (int c = in.read(); c != -1; c = in.read()) {
                    builder.append((char) c);
                }

                String jsonTokenResult = builder.toString();
                Gson gson = new GsonBuilder()
                        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                        .create();
                OAuthToken token = gson.fromJson(jsonTokenResult, OAuthToken.class);
                config.setValue(Config.OAUTH_ACCESSTOKEN, token.getAccessToken());
                config.setValue(Config.OAUTH_REFRESHTOKEN, token.getRefreshToken());
                config.setValue(Config.ENDPOINT, token.getInstanceUrl());

            }

            return client;
        }

        public static String handleInitialUrl(String url) throws URISyntaxException {
            Map<String, String> queryParameters = getQueryParameters(url);
            return queryParameters.get("code");
        }

    }
}
