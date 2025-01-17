/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nifi.properties;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.internal.util.io.IOUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * To run this test, make sure to first configure sensitive credential information as in the following link
 * https://cloud.google.com/kms/docs/reference/libraries#cloud-console
 *
 * Create a project, keyring and key in the web console.
 *
 * Take note of the project name, location, keyring name and key name.
 *
 * Then, set the system properties as follows:
 * -Dgcp.kms.project="project"
 * -Dgcp.kms.location="location"
 * -Dgcp.kms.keyring="key ring name"
 * -Dgcp.kms.key="key name"
 * when running the integration tests
 */

public class GCPKMSSensitivePropertyProviderIT {
    private static final String SAMPLE_PLAINTEXT = "GCPKMSSensitivePropertyProviderIT SAMPLE-PLAINTEXT";
    private static final String PROJECT_ID_PROPS_NAME = "gcp.kms.project";
    private static final String LOCATION_ID_PROPS_NAME = "gcp.kms.location";
    private static final String KEYRING_ID_PROPS_NAME = "gcp.kms.keyring";
    private static final String KEY_ID_PROPS_NAME = "gcp.kms.key";
    private static final String BOOTSTRAP_GCP_FILE_PROPS_NAME = "nifi.bootstrap.protection.gcp.kms.conf";

    private static final String EMPTY_PROPERTY = "";

    private static GCPKMSSensitivePropertyProvider spp;

    private static BootstrapProperties props;

    private static Path mockBootstrapConf, mockGCPBootstrapConf;

    private static final Logger logger = LoggerFactory.getLogger(GCPKMSSensitivePropertyProviderIT.class);

    private static void initializeBootstrapProperties() throws IOException{
        mockBootstrapConf = Files.createTempFile("bootstrap", ".conf").toAbsolutePath();
        mockGCPBootstrapConf = Files.createTempFile("bootstrap-gcp", ".conf").toAbsolutePath();
        IOUtil.writeText(BOOTSTRAP_GCP_FILE_PROPS_NAME + "=" + mockGCPBootstrapConf.toAbsolutePath(), mockBootstrapConf.toFile());

        final Properties bootstrapProperties = new Properties();
        try (final InputStream inputStream = Files.newInputStream(mockBootstrapConf)) {
            bootstrapProperties.load(inputStream);
            props = new BootstrapProperties("nifi", bootstrapProperties, mockBootstrapConf);
        }

        String projectId = System.getProperty(PROJECT_ID_PROPS_NAME, EMPTY_PROPERTY);
        String locationId = System.getProperty(LOCATION_ID_PROPS_NAME, EMPTY_PROPERTY);
        String keyringId = System.getProperty(KEYRING_ID_PROPS_NAME, EMPTY_PROPERTY);
        String keyId = System.getProperty(KEY_ID_PROPS_NAME, EMPTY_PROPERTY);

        StringBuilder bootstrapConfText = new StringBuilder();
        String lineSeparator = System.getProperty("line.separator");
        bootstrapConfText.append(PROJECT_ID_PROPS_NAME + "=" + projectId);
        bootstrapConfText.append(lineSeparator + LOCATION_ID_PROPS_NAME + "=" + locationId);
        bootstrapConfText.append(lineSeparator + KEYRING_ID_PROPS_NAME + "=" + keyringId);
        bootstrapConfText.append(lineSeparator + KEY_ID_PROPS_NAME + "=" + keyId);
        IOUtil.writeText(bootstrapConfText.toString(), mockGCPBootstrapConf.toFile());
    }

    @BeforeClass
    public static void initOnce() throws IOException {
        initializeBootstrapProperties();
        Assert.assertNotNull(props);
        spp = new GCPKMSSensitivePropertyProvider(props);
        Assert.assertNotNull(spp);
    }

    @AfterClass
    public static void tearDownOnce() throws IOException {
        Files.deleteIfExists(mockBootstrapConf);
        Files.deleteIfExists(mockGCPBootstrapConf);

        spp.cleanUp();
    }

    @Test
    public void testEncryptDecrypt() {
        logger.info("Running testEncryptDecrypt of GCP KMS SPP integration test");
        runEncryptDecryptTest();
        logger.info("testEncryptDecrypt of GCP KMS SPP integration test completed");
    }

    private static void runEncryptDecryptTest() {
        logger.info("Plaintext: " + SAMPLE_PLAINTEXT);
        String protectedValue = spp.protect(SAMPLE_PLAINTEXT, ProtectedPropertyContext.defaultContext("property"));
        logger.info("Protected Value: " + protectedValue);
        String unprotectedValue = spp.unprotect(protectedValue, ProtectedPropertyContext.defaultContext("property"));
        logger.info("Unprotected Value: " + unprotectedValue);

        Assert.assertEquals(SAMPLE_PLAINTEXT, unprotectedValue);
        Assert.assertNotEquals(SAMPLE_PLAINTEXT, protectedValue);
        Assert.assertNotEquals(protectedValue, unprotectedValue);
    }
}
