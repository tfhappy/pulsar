/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.pulsar.broker.authentication;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwt;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.sql.Date;
import java.util.HashMap;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import javax.crypto.SecretKey;
import javax.naming.AuthenticationException;

import org.apache.pulsar.broker.ServiceConfiguration;
import org.apache.pulsar.broker.authentication.utils.AuthTokenUtils;
import org.testng.annotations.Test;

public class AuthenticationProviderTokenTest {

    private static final String SUBJECT = "my-test-subject";

    @Test
    public void testInvalidInitialize() throws Exception {
        AuthenticationProviderToken provider = new AuthenticationProviderToken();

        try {
            provider.initialize(new ServiceConfiguration());
            fail("should have failed");
        } catch (IOException e) {
            // Expected, secret key was not defined
        } finally {
            // currently, will not close any resource
            provider.close();
        }
    }

    @Test
    public void testSerializeSecretKey() {
        SecretKey secretKey = AuthTokenUtils.createSecretKey(SignatureAlgorithm.HS256);

        String token = Jwts.builder()
                .setSubject(SUBJECT)
                .signWith(secretKey)
                .compact();

        @SuppressWarnings("unchecked")
        Jwt<?, Claims> jwt = Jwts.parser()
                .setSigningKey(AuthTokenUtils.decodeSecretKey(secretKey.getEncoded()))
                .parse(token);

        assertNotNull(jwt);
        assertNotNull(jwt.getBody());
        assertEquals(jwt.getBody().getSubject(), SUBJECT);
    }

    @Test
    public void testSerializeKeyPair() throws Exception {
        KeyPair keyPair = Keys.keyPairFor(SignatureAlgorithm.RS256);

        String privateKey = AuthTokenUtils.encodeKeyBase64(keyPair.getPrivate());
        String publicKey = AuthTokenUtils.encodeKeyBase64(keyPair.getPublic());

        String token = AuthTokenUtils.createToken(AuthTokenUtils.decodePrivateKey(Decoders.BASE64.decode(privateKey), SignatureAlgorithm.RS256),
                SUBJECT,
                Optional.empty());

        @SuppressWarnings("unchecked")
        Jwt<?, Claims> jwt = Jwts.parser()
                .setSigningKey(AuthTokenUtils.decodePublicKey(Decoders.BASE64.decode(publicKey), SignatureAlgorithm.RS256))
                .parse(token);

        assertNotNull(jwt);
        assertNotNull(jwt.getBody());
        assertEquals(jwt.getBody().getSubject(), SUBJECT);
    }

    @Test
    public void testAuthSecretKey() throws Exception {
        SecretKey secretKey = AuthTokenUtils.createSecretKey(SignatureAlgorithm.HS256);

        AuthenticationProviderToken provider = new AuthenticationProviderToken();
        assertEquals(provider.getAuthMethodName(), AuthenticationProviderToken.TOKEN);

        Properties properties = new Properties();
        properties.setProperty(AuthenticationProviderToken.CONF_TOKEN_SECRET_KEY,
                AuthTokenUtils.encodeKeyBase64(secretKey));

        ServiceConfiguration conf = new ServiceConfiguration();
        conf.setProperties(properties);
        provider.initialize(conf);

        try {
            provider.authenticate(new AuthenticationDataSource() {
            });
            fail("Should have failed");
        } catch (AuthenticationException e) {
            // expected, no credential passed
        }

        String token = AuthTokenUtils.createToken(secretKey, SUBJECT, Optional.empty());

        // Pulsar protocol auth
        String subject = provider.authenticate(new AuthenticationDataSource() {
            @Override
            public boolean hasDataFromCommand() {
                return true;
            }

            @Override
            public String getCommandData() {
                return token;
            }
        });
        assertEquals(subject, SUBJECT);

        // HTTP protocol auth
        provider.authenticate(new AuthenticationDataSource() {
            @Override
            public boolean hasDataFromHttp() {
                return true;
            }

            @Override
            public String getHttpHeader(String name) {
                if (name.equals(AuthenticationProviderToken.HTTP_HEADER_NAME)) {
                    return AuthenticationProviderToken.HTTP_HEADER_VALUE_PREFIX + token;
                } else {
                    throw new IllegalArgumentException("Wrong HTTP header");
                }
            }
        });
        assertEquals(subject, SUBJECT);

        // Expired token. This should be rejected by the authentication provider
        String expiredToken = AuthTokenUtils.createToken(secretKey, SUBJECT,
                Optional.of(new Date(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1))));

        // Pulsar protocol auth
        try {
            provider.authenticate(new AuthenticationDataSource() {
                @Override
                public boolean hasDataFromCommand() {
                    return true;
                }

                @Override
                public String getCommandData() {
                    return expiredToken;
                }
            });
            fail("Should have failed");
        } catch (AuthenticationException e) {
            // expected, token was expired
        }

        provider.close();
    }

    @Test
    public void testAuthSecretKeyFromFile() throws Exception {
        SecretKey secretKey = AuthTokenUtils.createSecretKey(SignatureAlgorithm.HS256);

        File secretKeyFile = File.createTempFile("pulsar-test-secret-key-", ".key");
        secretKeyFile.deleteOnExit();
        Files.write(Paths.get(secretKeyFile.toString()), secretKey.getEncoded());

        AuthenticationProviderToken provider = new AuthenticationProviderToken();

        Properties properties = new Properties();
        properties.setProperty(AuthenticationProviderToken.CONF_TOKEN_SECRET_KEY, "file://" + secretKeyFile.toString());

        ServiceConfiguration conf = new ServiceConfiguration();
        conf.setProperties(properties);
        provider.initialize(conf);

        String token = AuthTokenUtils.createToken(secretKey, SUBJECT, Optional.empty());

        // Pulsar protocol auth
        String subject = provider.authenticate(new AuthenticationDataSource() {
            @Override
            public boolean hasDataFromCommand() {
                return true;
            }

            @Override
            public String getCommandData() {
                return token;
            }
        });
        assertEquals(subject, SUBJECT);
        provider.close();
    }

    @Test
    public void testAuthSecretKeyFromDataBase64() throws Exception {
        SecretKey secretKey = AuthTokenUtils.createSecretKey(SignatureAlgorithm.HS256);

        AuthenticationProviderToken provider = new AuthenticationProviderToken();

        Properties properties = new Properties();
        properties.setProperty(AuthenticationProviderToken.CONF_TOKEN_SECRET_KEY,
                "data:;base64," + AuthTokenUtils.encodeKeyBase64(secretKey));

        ServiceConfiguration conf = new ServiceConfiguration();
        conf.setProperties(properties);
        provider.initialize(conf);

        String token = AuthTokenUtils.createToken(secretKey, SUBJECT, Optional.empty());

        // Pulsar protocol auth
        String subject = provider.authenticate(new AuthenticationDataSource() {
            @Override
            public boolean hasDataFromCommand() {
                return true;
            }

            @Override
            public String getCommandData() {
                return token;
            }
        });
        assertEquals(subject, SUBJECT);
        provider.close();
    }

    @Test
    public void testAuthSecretKeyPair() throws Exception {
        KeyPair keyPair = Keys.keyPairFor(SignatureAlgorithm.RS256);

        String privateKeyStr = AuthTokenUtils.encodeKeyBase64(keyPair.getPrivate());
        String publicKeyStr = AuthTokenUtils.encodeKeyBase64(keyPair.getPublic());

        AuthenticationProviderToken provider = new AuthenticationProviderToken();

        Properties properties = new Properties();
        // Use public key for validation
        properties.setProperty(AuthenticationProviderToken.CONF_TOKEN_PUBLIC_KEY, publicKeyStr);

        ServiceConfiguration conf = new ServiceConfiguration();
        conf.setProperties(properties);
        provider.initialize(conf);

        // Use private key to generate token
        PrivateKey privateKey = AuthTokenUtils.decodePrivateKey(Decoders.BASE64.decode(privateKeyStr), SignatureAlgorithm.RS256);
        String token = AuthTokenUtils.createToken(privateKey, SUBJECT, Optional.empty());

        // Pulsar protocol auth
        String subject = provider.authenticate(new AuthenticationDataSource() {
            @Override
            public boolean hasDataFromCommand() {
                return true;
            }

            @Override
            public String getCommandData() {
                return token;
            }
        });
        assertEquals(subject, SUBJECT);

        provider.close();
    }

    @Test
    public void testAuthSecretKeyPairWithCustomClaim() throws Exception {
        String authRoleClaim = "customClaim";
        String authRole = "my-test-role";

        KeyPair keyPair = Keys.keyPairFor(SignatureAlgorithm.RS256);

        String privateKeyStr = AuthTokenUtils.encodeKeyBase64(keyPair.getPrivate());
        String publicKeyStr = AuthTokenUtils.encodeKeyBase64(keyPair.getPublic());

        AuthenticationProviderToken provider = new AuthenticationProviderToken();

        Properties properties = new Properties();
        // Use public key for validation
        properties.setProperty(AuthenticationProviderToken.CONF_TOKEN_PUBLIC_KEY, publicKeyStr);
        // Set custom claim field
        properties.setProperty(AuthenticationProviderToken.CONF_TOKEN_AUTH_CLAIM, authRoleClaim);

        ServiceConfiguration conf = new ServiceConfiguration();
        conf.setProperties(properties);
        provider.initialize(conf);


        // Use private key to generate token
        PrivateKey privateKey = AuthTokenUtils.decodePrivateKey(Decoders.BASE64.decode(privateKeyStr), SignatureAlgorithm.RS256);
        String token = Jwts.builder()
                .setClaims(new HashMap<String, Object>() {{
                    put(authRoleClaim, authRole);
                }})
                .signWith(privateKey)
                .compact();


        // Pulsar protocol auth
        String role = provider.authenticate(new AuthenticationDataSource() {
            @Override
            public boolean hasDataFromCommand() {
                return true;
            }

            @Override
            public String getCommandData() {
                return token;
            }
        });
        assertEquals(role, authRole);

        provider.close();
    }

    @Test
    public void testAuthSecretKeyPairWithECDSA() throws Exception {
        KeyPair keyPair = Keys.keyPairFor(SignatureAlgorithm.ES256);

        String privateKeyStr = AuthTokenUtils.encodeKeyBase64(keyPair.getPrivate());
        String publicKeyStr = AuthTokenUtils.encodeKeyBase64(keyPair.getPublic());

        AuthenticationProviderToken provider = new AuthenticationProviderToken();

        Properties properties = new Properties();
        // Use public key for validation
        properties.setProperty(AuthenticationProviderToken.CONF_TOKEN_PUBLIC_KEY, publicKeyStr);
        // Set that we are using EC keys
        properties.setProperty(AuthenticationProviderToken.CONF_TOKEN_PUBLIC_ALG, SignatureAlgorithm.ES256.getValue());

        ServiceConfiguration conf = new ServiceConfiguration();
        conf.setProperties(properties);
        provider.initialize(conf);

        // Use private key to generate token
        PrivateKey privateKey = AuthTokenUtils.decodePrivateKey(Decoders.BASE64.decode(privateKeyStr), SignatureAlgorithm.ES256);
        String token = AuthTokenUtils.createToken(privateKey, SUBJECT, Optional.empty());

        // Pulsar protocol auth
        String subject = provider.authenticate(new AuthenticationDataSource() {
            @Override
            public boolean hasDataFromCommand() {
                return true;
            }

            @Override
            public String getCommandData() {
                return token;
            }
        });
        assertEquals(subject, SUBJECT);

        provider.close();
    }

    @Test(expectedExceptions = AuthenticationException.class)
    public void testAuthenticateWhenNoJwtPassed() throws AuthenticationException {
        AuthenticationProviderToken provider = new AuthenticationProviderToken();
        provider.authenticate(new AuthenticationDataSource() {
            @Override
            public boolean hasDataFromCommand() {
                return false;
            }

            @Override
            public boolean hasDataFromHttp() {
                return false;
            }
        });
    }

    @Test(expectedExceptions = AuthenticationException.class)
    public void testAuthenticateWhenAuthorizationHeaderNotExist() throws AuthenticationException {
        AuthenticationProviderToken provider = new AuthenticationProviderToken();
        provider.authenticate(new AuthenticationDataSource() {
            @Override
            public String getHttpHeader(String name) {
                return null;
            }

            @Override
            public boolean hasDataFromHttp() {
                return true;
            }
        });
    }

    @Test(expectedExceptions = AuthenticationException.class)
    public void testAuthenticateWhenAuthHeaderValuePrefixIsInvalid() throws AuthenticationException {
        AuthenticationProviderToken provider = new AuthenticationProviderToken();
        provider.authenticate(new AuthenticationDataSource() {
            @Override
            public String getHttpHeader(String name) {
                return "MyBearer ";
            }

            @Override
            public boolean hasDataFromHttp() {
                return true;
            }
        });
    }

    @Test(expectedExceptions = AuthenticationException.class)
    public void testAuthenticateWhenJwtIsBlank() throws AuthenticationException {
        AuthenticationProviderToken provider = new AuthenticationProviderToken();
        provider.authenticate(new AuthenticationDataSource() {
            @Override
            public String getHttpHeader(String name) {
                return AuthenticationProviderToken.HTTP_HEADER_VALUE_PREFIX + "      ";
            }

            @Override
            public boolean hasDataFromHttp() {
                return true;
            }
        });
    }

    @Test(expectedExceptions = AuthenticationException.class)
    public void testAuthenticateWhenInvalidTokenIsPassed() throws AuthenticationException, IOException {
        SecretKey secretKey = AuthTokenUtils.createSecretKey(SignatureAlgorithm.HS256);

        Properties properties = new Properties();
        properties.setProperty(AuthenticationProviderToken.CONF_TOKEN_SECRET_KEY,
                AuthTokenUtils.encodeKeyBase64(secretKey));

        ServiceConfiguration conf = new ServiceConfiguration();
        conf.setProperties(properties);

        AuthenticationProviderToken provider = new AuthenticationProviderToken();
        provider.initialize(conf);
        provider.authenticate(new AuthenticationDataSource() {
            @Override
            public String getHttpHeader(String name) {
                return AuthenticationProviderToken.HTTP_HEADER_VALUE_PREFIX + "invalid_token";
            }

            @Override
            public boolean hasDataFromHttp() {
                return true;
            }
        });
    }

    @Test(expectedExceptions = IOException.class)
    public void testValidationKeyWhenBlankSecretKeyIsPassed() throws IOException {
        Properties properties = new Properties();
        properties.setProperty(AuthenticationProviderToken.CONF_TOKEN_SECRET_KEY, "   ");

        ServiceConfiguration conf = new ServiceConfiguration();
        conf.setProperties(properties);

        AuthenticationProviderToken provider = new AuthenticationProviderToken();
        provider.initialize(conf);
    }

    @Test(expectedExceptions = IOException.class)
    public void testValidationKeyWhenBlankPublicKeyIsPassed() throws IOException {
        Properties properties = new Properties();
        properties.setProperty(AuthenticationProviderToken.CONF_TOKEN_PUBLIC_KEY, "   ");

        ServiceConfiguration conf = new ServiceConfiguration();
        conf.setProperties(properties);

        AuthenticationProviderToken provider = new AuthenticationProviderToken();
        provider.initialize(conf);
    }

    @Test(expectedExceptions = IOException.class)
    public void testInitializeWhenSecretKeyFilePathIsInvalid() throws IOException {
        Properties properties = new Properties();
        properties.setProperty(AuthenticationProviderToken.CONF_TOKEN_SECRET_KEY,
                "file://" + "invalid_secret_key_file");

        ServiceConfiguration conf = new ServiceConfiguration();
        conf.setProperties(properties);

        new AuthenticationProviderToken().initialize(conf);
    }

    @Test(expectedExceptions = IOException.class)
    public void testInitializeWhenPublicKeyFilePathIsInvalid() throws IOException {
        Properties properties = new Properties();
        properties.setProperty(AuthenticationProviderToken.CONF_TOKEN_PUBLIC_KEY,
                "file://" + "invalid_public_key_file");

        ServiceConfiguration conf = new ServiceConfiguration();
        conf.setProperties(properties);

        new AuthenticationProviderToken().initialize(conf);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testValidationWhenPublicKeyAlgIsInvalid() throws IOException {
        Properties properties = new Properties();
        properties.setProperty(AuthenticationProviderToken.CONF_TOKEN_PUBLIC_ALG,
                "invalid");

        ServiceConfiguration conf = new ServiceConfiguration();
        conf.setProperties(properties);

        new AuthenticationProviderToken().initialize(conf);
    }
}
