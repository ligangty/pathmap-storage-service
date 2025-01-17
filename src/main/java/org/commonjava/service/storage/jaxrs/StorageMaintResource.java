/**
 * Copyright (C) 2021 Red Hat, Inc. (https://github.com/Commonjava/service-parent)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.commonjava.service.storage.jaxrs;

import org.commonjava.service.storage.controller.StorageController;
import org.commonjava.storage.pathmapped.model.Filesystem;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.util.Collection;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Tag( name = "Storage maintenance api", description = "Resource for storage" )
@ApplicationScoped
@Path( StorageMaintResource.API_MAINT_BASE)
public class StorageMaintResource
{
    public final static String API_MAINT_BASE = "/api/storage/maint";

    private final Logger logger = LoggerFactory.getLogger( getClass() );

    @Inject
    StorageController controller;

    @Operation( summary = "Get empty filesystems." )
    @APIResponses( { @APIResponse( responseCode = "200", description = "The empty filesystems." ) } )
    @Produces( APPLICATION_JSON )
    @GET
    @Path( "filesystems/empty" )
    public Response getEmptyFilesystems()
    {
        logger.info( "Get empty filesystems" );
        Collection<? extends Filesystem> result = controller.getEmptyFilesystems();
        logger.debug( "Get empty filesystems, result: {}", result );
        return Response.ok( result ).build();
    }

    @Operation( summary = "Purge empty filesystems." )
    @APIResponses( { @APIResponse( responseCode = "200", description = "Purge done." ) } )
    @DELETE
    @Path( "filesystems/empty" )
    public Response purgeEmptyFilesystems()
    {
        logger.info( "Purge empty filesystems" );
        controller.purgeEmptyFilesystems();
        return Response.ok().build();
    }
}
