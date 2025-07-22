/*******************************************************************************
 * Copyright (c) 2020, 2024 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package io.openliberty.guides.mongo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;

import javax.net.ssl.SSLContext;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.ibm.websphere.crypto.PasswordUtil;
import com.ibm.websphere.ssl.JSSEHelper;
import com.ibm.websphere.ssl.SSLException;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@ApplicationScoped
public class MongoProducer {

    @Inject
    @ConfigProperty(name = "mongo.hostname", defaultValue = "localhost")
    String hostname;

    @Inject
    @ConfigProperty(name = "mongo.port", defaultValue = "27017")
    int port;

    @Inject
    @ConfigProperty(name = "mongo.dbname", defaultValue = "testdb")
    String dbName;

    @Inject
    @ConfigProperty(name = "mongo.user")
    String user;

    @Inject
    @ConfigProperty(name = "mongo.pass.encoded", defaultValue = "")
    Optional<String> encodedPass;

    @Inject
    @ConfigProperty(name = "mongo.pass.file", defaultValue = "")
    Optional<String> passFile;

    public String getPassword() {
        if (passFile.isPresent() && !passFile.get().isEmpty()) {
            try {
                return Files.readString(Path.of(passFile.get())).trim();
            } catch (IOException e) {
                throw new RuntimeException("Failed to read Mongo password from file: " + passFile.get(), e);
            }
        }
        return encodedPass.orElseThrow(() ->
            new RuntimeException("Neither mongo.pass.file nor mongo.pass.encoded are defined.")
        );
    }

    @Produces
    public MongoClient createMongo() throws SSLException {
        String password = PasswordUtil.passwordDecode(getPassword());
        MongoCredential creds = MongoCredential.createCredential(
                user,
                dbName,
                password.toCharArray()
        );

        SSLContext sslContext = JSSEHelper.getInstance().getSSLContext(
                "outboundSSLContext",
                Collections.emptyMap(),
                null
        );

        return MongoClients.create(MongoClientSettings.builder()
                   .applyConnectionString(
                       new ConnectionString("mongodb://" + hostname + ":" + port))
                   .credential(creds)
                   .applyToSslSettings(builder -> {
                       builder.enabled(true);
                       builder.context(sslContext); })
                   .build());
    }

    @Produces
    public MongoDatabase createDB(
            MongoClient client) {
        return client.getDatabase(dbName);
    }

    public void close(
            @Disposes MongoClient toClose) {
        toClose.close();
    }
}
