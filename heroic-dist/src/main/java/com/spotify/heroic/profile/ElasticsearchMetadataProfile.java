/*
 * Copyright (c) 2015 Spotify AB.
 *
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

package com.spotify.heroic.profile;

import static com.spotify.heroic.ParameterSpecification.parameter;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.spotify.heroic.ExtraParameters;
import com.spotify.heroic.HeroicConfig;
import com.spotify.heroic.ParameterSpecification;
import com.spotify.heroic.elasticsearch.ConnectionModule;
import com.spotify.heroic.elasticsearch.TransportClientWrapper;
import com.spotify.heroic.elasticsearch.index.RotatingIndexMapping;
import com.spotify.heroic.metadata.MetadataManagerModule;
import com.spotify.heroic.metadata.MetadataModule;
import com.spotify.heroic.metadata.elasticsearch.ElasticsearchMetadataModule;
import java.util.List;
import java.util.Optional;

public class ElasticsearchMetadataProfile extends HeroicProfileBase {
    private static final Splitter splitter = Splitter.on(',').trimResults();
    @Override
    public HeroicConfig.Builder build(final ExtraParameters params) {
        final RotatingIndexMapping.Builder index = RotatingIndexMapping.builder();

        params.get("pattern")
            .map(index::pattern);
        params.getDuration("interval")
            .map(index::interval);
        params.getBoolean("supportDynamicMaxReadIndices")
            .map(index::dynamicMaxReadIndices);
        params.getInteger("maxReadIndices")
            .map(index::maxReadIndices);

        final ConnectionModule.Builder connection = ConnectionModule.builder().index(index.build());

        final TransportClientWrapper.Builder esClient = TransportClientWrapper.builder();
        params.get("clusterName").map(esClient::clusterName);
        params.get("seeds").map(s -> esClient.seeds(ImmutableList.copyOf(splitter.split(s))));

        final ElasticsearchMetadataModule.Builder module = ElasticsearchMetadataModule.builder()
            .connection(connection.clientSetup(esClient.build()).build());

        params.get("type").map(module::backendType);

        return HeroicConfig
            .builder()
            .metadata(MetadataManagerModule
                .builder()
                .backends(ImmutableList.<MetadataModule>of(module.build())));
    }

    @Override
    public Optional<String> scope() {
        return Optional.of("elasticsearch");
    }

    @Override
    public String description() {
        return "Configures a metadata backend for Elasticsearch";
    }

    static final Joiner arguments = Joiner.on(", ");

    @Override
    public List<ParameterSpecification> options() {
        // @formatter:off
        return ImmutableList.of(
            parameter("interval",
                "Rotating index interval duration (examples: 7d, 24h)",
                "<Duration>"),
            parameter("pattern", "Index pattern to use (example: heroic-%s)",
                    "<pattern>"),
            parameter("clusterName", "Cluster name to connect to", "<string>"),
            parameter("seeds", "Seeds to connect to", "<host>[:<port][,..]"),
            parameter("type", "Backend type to use, available types are: " +
                    arguments.join(ElasticsearchMetadataModule.types()), "<type>")
        );
        // @formatter:on
    }
}
