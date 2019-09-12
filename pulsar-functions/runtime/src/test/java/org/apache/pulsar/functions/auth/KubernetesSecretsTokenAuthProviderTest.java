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
package org.apache.pulsar.functions.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1Container;
import io.kubernetes.client.models.V1PodSpec;
import io.kubernetes.client.models.V1PodTemplateSpec;
import io.kubernetes.client.models.V1Secret;
import io.kubernetes.client.models.V1StatefulSet;
import io.kubernetes.client.models.V1StatefulSetSpec;

import java.util.Collections;
import java.util.Optional;

import org.apache.commons.lang.StringUtils;
import org.apache.pulsar.broker.authentication.AuthenticationDataSource;
import org.apache.pulsar.client.impl.auth.AuthenticationToken;
import org.apache.pulsar.functions.instance.AuthenticationConfig;
import org.testng.Assert;
import org.testng.annotations.Test;


public class KubernetesSecretsTokenAuthProviderTest {

    @Test
    public void testConfigureAuthDataStatefulSet() {

        CoreV1Api coreV1Api = mock(CoreV1Api.class);
        KubernetesSecretsTokenAuthProvider kubernetesSecretsTokenAuthProvider = new KubernetesSecretsTokenAuthProvider(coreV1Api, "default");


        V1StatefulSet statefulSet = new V1StatefulSet();
        statefulSet.setSpec(
                new V1StatefulSetSpec().template(
                        new V1PodTemplateSpec().spec(
                                new V1PodSpec().containers(
                                        Collections.singletonList(new V1Container())))));
        FunctionAuthData functionAuthData = FunctionAuthData.builder().data("foo".getBytes()).build();
        kubernetesSecretsTokenAuthProvider.configureAuthDataStatefulSet(statefulSet, Optional.of(functionAuthData));

        Assert.assertEquals(statefulSet.getSpec().getTemplate().getSpec().getVolumes().size(), 1);
        Assert.assertEquals(statefulSet.getSpec().getTemplate().getSpec().getVolumes().get(0).getName(), "function-auth");
        Assert.assertEquals(statefulSet.getSpec().getTemplate().getSpec().getVolumes().get(0).getSecret().getSecretName(), "pf-secret-foo");

        Assert.assertEquals(statefulSet.getSpec().getTemplate().getSpec().getContainers().size(), 1);
        Assert.assertEquals(statefulSet.getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts().size(), 1);
        Assert.assertEquals(statefulSet.getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts().get(0).getName(), "function-auth");
        Assert.assertEquals(statefulSet.getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts().get(0).getMountPath(), "/etc/auth");
    }

    @Test
    public void testCacheAuthData() throws ApiException {
        CoreV1Api coreV1Api = mock(CoreV1Api.class);
        doReturn(new V1Secret()).when(coreV1Api).createNamespacedSecret(anyString(), any(), anyString());
        KubernetesSecretsTokenAuthProvider kubernetesSecretsTokenAuthProvider = new KubernetesSecretsTokenAuthProvider(coreV1Api, "default");
        Optional<FunctionAuthData> functionAuthData = kubernetesSecretsTokenAuthProvider.cacheAuthData("test-tenant",
                "test-ns", "test-func", new AuthenticationDataSource() {
                    @Override
                    public boolean hasDataFromCommand() {
                        return true;
                    }

                    @Override
                    public String getCommandData() {
                        return "test-token";
                    }
                });

        Assert.assertTrue(functionAuthData.isPresent());
        Assert.assertTrue(StringUtils.isNotBlank(new String(functionAuthData.get().getData())));
    }

    @Test
    public void configureAuthenticationConfig() {
        CoreV1Api coreV1Api = mock(CoreV1Api.class);
        KubernetesSecretsTokenAuthProvider kubernetesSecretsTokenAuthProvider = new KubernetesSecretsTokenAuthProvider(coreV1Api, "default");
        AuthenticationConfig authenticationConfig = AuthenticationConfig.builder().build();
        FunctionAuthData functionAuthData = FunctionAuthData.builder().data("foo".getBytes()).build();
        kubernetesSecretsTokenAuthProvider.configureAuthenticationConfig(authenticationConfig, Optional.of(functionAuthData));

        Assert.assertEquals(authenticationConfig.getClientAuthenticationPlugin(), AuthenticationToken.class.getName());
        Assert.assertEquals(authenticationConfig.getClientAuthenticationParameters(), "file:///etc/auth/token");
    }

    @Test
    public void testUpdateAuthData() throws Exception {
        CoreV1Api coreV1Api = mock(CoreV1Api.class);
        KubernetesSecretsTokenAuthProvider kubernetesSecretsTokenAuthProvider = new KubernetesSecretsTokenAuthProvider(coreV1Api, "default");

        // test when existingFunctionAuthData is empty
        Optional<FunctionAuthData> existingFunctionAuthData = Optional.empty();
        Optional<FunctionAuthData> functionAuthData = kubernetesSecretsTokenAuthProvider.updateAuthData("test-tenant",
                "test-ns", "test-func", existingFunctionAuthData, new AuthenticationDataSource() {
                    @Override
                    public boolean hasDataFromCommand() {
                        return true;
                    }

                    @Override
                    public String getCommandData() {
                        return "test-token";
                    }
                });


        Assert.assertTrue(functionAuthData.isPresent());
        Assert.assertTrue(StringUtils.isNotBlank(new String(functionAuthData.get().getData())));

        // test when existingFunctionAuthData is NOT empty
        existingFunctionAuthData = Optional.of(new FunctionAuthData("pf-secret-z7mxx".getBytes(), null));
        functionAuthData = kubernetesSecretsTokenAuthProvider.updateAuthData("test-tenant",
                "test-ns", "test-func", existingFunctionAuthData, new AuthenticationDataSource() {
                    @Override
                    public boolean hasDataFromCommand() {
                        return true;
                    }

                    @Override
                    public String getCommandData() {
                        return "test-token";
                    }
                });


        Assert.assertTrue(functionAuthData.isPresent());
        Assert.assertEquals(new String(functionAuthData.get().getData()), "pf-secret-z7mxx");
    }
}
