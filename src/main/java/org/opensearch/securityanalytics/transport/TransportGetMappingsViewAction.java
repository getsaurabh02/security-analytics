/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.securityanalytics.transport;

import org.opensearch.action.ActionListener;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.securityanalytics.action.GetIndexMappingsAction;
import org.opensearch.securityanalytics.action.GetIndexMappingsRequest;
import org.opensearch.securityanalytics.action.GetIndexMappingsResponse;
import org.opensearch.securityanalytics.action.GetMappingsViewAction;
import org.opensearch.securityanalytics.action.GetMappingsViewRequest;
import org.opensearch.securityanalytics.action.GetMappingsViewResponse;
import org.opensearch.securityanalytics.mapper.MapperService;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

public class TransportGetMappingsViewAction extends HandledTransportAction<GetMappingsViewRequest, GetMappingsViewResponse> {
    private MapperService mapperService;
    private ClusterService clusterService;

    @Inject
    public TransportGetMappingsViewAction(
            TransportService transportService,
            ActionFilters actionFilters,
            GetMappingsViewAction getMappingsViewAction,
            MapperService mapperService,
            ClusterService clusterService
    ) {
        super(getMappingsViewAction.NAME, transportService, actionFilters, GetMappingsViewRequest::new);
        this.clusterService = clusterService;
        this.mapperService = mapperService;
    }

    @Override
    protected void doExecute(Task task, GetMappingsViewRequest request, ActionListener<GetMappingsViewResponse> actionListener) {
        IndexMetadata index = clusterService.state().metadata().index(request.getIndexName());
        if (index == null) {
            actionListener.onFailure(new IllegalStateException("Could not find index [" + request.getIndexName() + "]"));
            return;
        }
        mapperService.getMappingsViewAction(request.getIndexName(), request.getRuleTopic(), actionListener);
    }
}