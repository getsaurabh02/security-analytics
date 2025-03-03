/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.securityanalytics.mapper;

import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.common.collect.ImmutableOpenMap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MapperUtils {

    public static final String PROPERTIES = "properties";
    public static final String PATH = "path";
    public static final String TYPE = "type";
    public static final String ALIAS = "alias";
    public static final String NESTED = "nested";

    public static List<Pair<String, String>> getAllAliasPathPairs(String aliasMappingsJson) throws IOException {
        MappingsTraverser mappingsTraverser = new MappingsTraverser(aliasMappingsJson, Set.of());
        return getAllAliasPathPairs(mappingsTraverser);
    }

    public static List<Pair<String, String>> getAllAliasPathPairs(MappingMetadata mappingMetadata) throws IOException {
        MappingsTraverser mappingsTraverser = new MappingsTraverser(mappingMetadata);
        return getAllAliasPathPairs(mappingsTraverser);
    }

    public static List<Pair<String, String>> getAllAliasPathPairs(MappingsTraverser mappingsTraverser) throws IOException {
        List<Pair<String, String>> aliasPathPairs = new ArrayList<>();
        mappingsTraverser.addListener(new MappingsTraverser.MappingsTraverserListener() {
            @Override
            public void onLeafVisited(MappingsTraverser.Node node) {
                // We'll ignore any irregularities in alias mappings here
                if (node.getProperties().containsKey(PATH) == false ||
                        node.getProperties().get(TYPE).equals(ALIAS) == false) {
                    return;
                }
                aliasPathPairs.add(Pair.of(node.currentPath, (String) node.getProperties().get(PATH)));
            }

            @Override
            public void onError(String error) {
                throw new IllegalArgumentException(error);
            }
        });
        mappingsTraverser.traverse();
        return aliasPathPairs;
    }

    public static List<String> getAllPathsFromAliasMappings(String aliasMappingsJson) throws IOException {
        List<String> paths = new ArrayList<>();

        MappingsTraverser mappingsTraverser = new MappingsTraverser(aliasMappingsJson, Set.of());
        mappingsTraverser.addListener(new MappingsTraverser.MappingsTraverserListener() {
            @Override
            public void onLeafVisited(MappingsTraverser.Node node) {
                if (node.getProperties().containsKey(PATH) == false) {
                    throw new IllegalArgumentException("Alias mappings are missing path for alias: [" + node.getNodeName() + "]");
                }
                if (node.getProperties().get(TYPE).equals(ALIAS) == false) {
                    throw new IllegalArgumentException("Alias mappings contains property of type: [" + node.node.get(TYPE) + "]");
                }
                paths.add((String) node.getProperties().get(PATH));
            }

            @Override
            public void onError(String error) {
                throw new IllegalArgumentException(error);
            }
        });
        mappingsTraverser.traverse();
        return paths;
    }

    /**
     * Does following validations:
     * <ul>
     *   <li>Index mappings cannot be empty
     *   <li>Alias mappings have to have property type=alias and path property has to exist
     *   <li>Paths from alias mappings should exists in index mappings
     * </ul>
     * @param indexMappings Index Mappings to which alias mappings will be applied
     * @param aliasMappingsJSON Alias Mappings as JSON string
     * @return list of alias mappings paths which are missing in index mappings
     * */
    public static List<String> validateIndexMappings(ImmutableOpenMap<String, MappingMetadata> indexMappings, String aliasMappingsJSON) throws IOException {

        // Check if index's mapping is empty
        if (isIndexMappingsEmpty(indexMappings)) {
            throw new IllegalArgumentException("Index mappings are empty");
        }

        // Get all paths (field names) to which we're going to apply aliases
        List<String> paths = getAllPathsFromAliasMappings(aliasMappingsJSON);

        // Traverse Index Mappings and extract all fields(paths)
        String indexName = indexMappings.iterator().next().key;
        MappingMetadata mappingMetadata = indexMappings.get(indexName);

        List<String> flatFields = getAllNonAliasFieldsFromIndex(mappingMetadata);
        // Return list of paths from Alias Mappings which are missing in Index Mappings
        return paths.stream()
                .filter(e -> !flatFields.contains(e))
                .collect(Collectors.toList());
    }

    public static List<String> getAllNonAliasFieldsFromIndex(MappingMetadata mappingMetadata) {
        MappingsTraverser mappingsTraverser = new MappingsTraverser(mappingMetadata);
        return mappingsTraverser.extractFlatNonAliasFields();
    }

    public static boolean isIndexMappingsEmpty(ImmutableOpenMap<String, MappingMetadata> indexMappings) {
        if (indexMappings.iterator().hasNext()) {
            return indexMappings.iterator().next().value.getSourceAsMap().size() == 0;
        }
        throw new IllegalArgumentException("Invalid Index Mappings");
    }

    public static Map<String, Object> getAliasMappingsWithFilter(
            String aliasMappingsJson,
            List<String> aliasesToInclude) throws IOException {

        // Traverse mappings and do copy with excluded type=alias properties
        MappingsTraverser mappingsTraverser = new MappingsTraverser(aliasMappingsJson, Set.of());
        // Resulting properties after filtering
        Map<String, Object> filteredProperties = new HashMap<>();

        mappingsTraverser.addListener(new MappingsTraverser.MappingsTraverserListener() {
            @Override
            public void onLeafVisited(MappingsTraverser.Node node) {
                // Skip everything except ones in include filter
                if (aliasesToInclude.contains(node.currentPath) == false) {
                    return;
                }
                MappingsTraverser.Node n = node;
                while (n.parent != null) {
                    n = n.parent;
                }
                if (n == null) {
                    n = node;
                }
                filteredProperties.put(n.getNodeName(), n.getProperties());
            }

            @Override
            public void onError(String error) {
                throw new IllegalArgumentException("");
            }
        });
        mappingsTraverser.traverse();
        // Construct filtered mappings with PROPERTIES as root and return them as result
        return Map.of(PROPERTIES, filteredProperties);
    }
}
