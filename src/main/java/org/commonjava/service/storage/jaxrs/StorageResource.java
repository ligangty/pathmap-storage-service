package org.commonjava.service.storage.jaxrs;

import org.commonjava.service.storage.controller.StorageController;
import org.commonjava.service.storage.dto.BatchCleanupRequest;
import org.commonjava.service.storage.dto.BatchCleanupResult;
import org.commonjava.service.storage.dto.FileInfoObj;
import org.commonjava.service.storage.util.ResponseHelper;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.eclipse.microprofile.openapi.annotations.enums.ParameterIn.PATH;

import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.spi.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.commonjava.storage.pathmapped.util.PathMapUtils.ROOT_DIR;

@Tag( name = "Storage api", description = "Resource for storage" )
@ApplicationScoped
@Path( StorageResource.API_BASE )
public class StorageResource
{
    public final static String API_BASE = "/api/storage";

    private final Logger logger = LoggerFactory.getLogger( getClass() );

    @Inject
    ResponseHelper responseHelper;

    @Inject
    StorageController controller;

    @Operation( description = "Write a file" )
    @APIResponse( responseCode = "201", description = "The file was created" )
    @PUT
    @Path( "content/{filesystem}/{path: (.*)}" )
    public Response put(final @PathParam( "filesystem" ) String filesystem,
                        final @PathParam( "path" ) String path,
                        final @QueryParam( "timeout" ) String timeout,
                        final @Context UriInfo uriInfo,
                        final @Context HttpRequest request,
                        final @Context SecurityContext securityContext )
    {
        logger.info( "Write [{}]{}, timeout: [{}]", filesystem, path, timeout );
        Response response;
        try
        {
            controller.writeFile( filesystem, path, request.getInputStream(), timeout );
        }
        catch ( final Exception e )
        {
            response = responseHelper.formatResponse( e, "message" );
            return response;
        }
        return Response.ok().build();
    }

    @Operation( description = "Retrieve a file" )
    @APIResponse( responseCode = "200", description = "The file content" )
    @APIResponse( responseCode = "404", description = "The file doesn't exist" )
    @GET
    @Path( "content/{filesystem}/{path: (.*)}" )
    public Response get( final @PathParam( "filesystem" ) String filesystem,
                         final @PathParam( "path" ) String path )
    {
        logger.info( "Get [{}]{}", filesystem, path );
        Response response;
        try
        {
            final InputStream is = controller.openInputStream( filesystem, path );
            response = Response.ok( is ).build();
        }
        catch ( final IOException e )
        {
            response = Response.status( Response.Status.NOT_FOUND ).build();
        }
        catch ( final Exception e )
        {
            logger.error( e.getMessage(), e );
            response = responseHelper.formatResponse( e );
        }
        return response;
    }

    @Operation( summary = "Delete a file." )
    @APIResponses( { @APIResponse ( responseCode = "200", description = "The file is removed.") })
    @DELETE
    @Path( "content/{filesystem}/{path: (.*)}" )
    public Response delete(
            final @Parameter( in = PATH, required = true ) @PathParam( "filesystem" ) String filesystem,
            final @Parameter( in = PATH, required = true ) @PathParam( "path" ) String path,
            @Context UriInfo uriInfo )
    {
        logger.info( "Delete [{}]{}", filesystem, path );
        controller.delete( filesystem, path );
        return Response.ok().build();
    }

    @Operation( description = "List a directory" )
    @APIResponse( responseCode = "200", description = "The dir list" )
    @APIResponse( responseCode = "404", description = "The dir doesn't exist" )
    @GET
    @Produces( APPLICATION_JSON )
    @Path( "browse{path: (.*)}" )
    public Response list( final @PathParam( "path" ) String rawPath,
                          final @QueryParam( "recursive" ) boolean recursive,
                          final @QueryParam( "filetype" ) String fileType,
                          final @QueryParam( "limit" ) int limit )
    {
        logger.info( "List [{}]", rawPath );
        Response response;
        if ( isBlank(rawPath) )
        {
            List<String> result = controller.getFilesystems().stream().map(s -> s + "/").collect(Collectors.toList());
            return Response.ok( result ).build();
        }
        try
        {
            String[] tokens = rawPath.substring(1).split("/", 2 ); // trim the leading /, then separate filesystem and path
            String filesystem = tokens[0];
            String path;
            if ( tokens.length >= 2 )
            {
                path = tokens[1];
            }
            else
            {
                path = ROOT_DIR;
            }
            logger.debug( "List filesystem: {}, path: {}", filesystem, path );
            String[] ret = controller.list(filesystem, path, recursive, fileType, limit);
            if ( ret == null )
            {
                response = Response.status( Response.Status.NOT_FOUND ).build();
            }
            else
            {
                response = Response.ok( ret ).build();
            }
        }
        catch ( final Exception e )
        {
            logger.error( e.getMessage(), e );
            response = responseHelper.formatResponse( e );
        }
        return response;
    }

    @Operation( summary = "Get the information of the file." )
    @APIResponses( { @APIResponse( responseCode = "200",
                    content = @Content( schema = @Schema( implementation = FileInfoObj.class ) ),
                    description = "The detailed info of the file." ) } )
    @Produces( APPLICATION_JSON )
    @GET
    @Path( "info/{filesystem}/{path: (.*)}" )
    public Response getFileInfo(
                    final @Parameter( in = PATH, required = true ) @PathParam( "filesystem" ) String filesystem,
                    final @Parameter( in = PATH, required = true ) @PathParam( "path" ) String path,
                    @Context UriInfo uriInfo )
    {
        FileInfoObj result = controller.getFileInfo( filesystem, path );
        logger.info( "File info: {}", result );
        return responseHelper.formatOkResponseWithJsonEntity( result );
    }

    @Operation( summary = "Get the filesystems containing the specified path." )
    @RequestBody( description = "The filesystem candidates", name = "body", required = true,
            content = @Content( schema = @Schema( implementation = Collection.class ) ) )
    @APIResponses( { @APIResponse( responseCode = "200", description = "The filesystems that contain the path." ) } )
    @Consumes( APPLICATION_JSON )
    @Produces( APPLICATION_JSON )
    @GET
    @Path( "filesystem/containing/{path: (.+)}" )
    public Response getFilesystemContaining( final @PathParam( "path" ) String path, final Collection<String> candidates )
    {
        logger.info( "Get filesystems containing path: {}, candidates: {}", path, candidates );
        Response response;
        try
        {
            Collection<String> result = controller.getFileSystemContaining(candidates, path);
            response = responseHelper.formatOkResponseWithJsonEntity( result );
        }
        catch (Exception e)
        {
            logger.error( e.getMessage(), e );
            response = responseHelper.formatResponse( e );
        }
        return response;
    }

    @Operation( summary = "Cleanup the files of the path in specified filesystems." )
    @RequestBody( description = "The cleanup request", name = "body", required = true,
            content = @Content( schema = @Schema( implementation = BatchCleanupRequest.class ) ) )
    @APIResponses( { @APIResponse( responseCode = "200",
            content = @Content( schema = @Schema( implementation = BatchCleanupResult.class ) ),
            description = "The result of cleaned files." ) } )
    @Consumes( APPLICATION_JSON )
    @Produces( APPLICATION_JSON )
    @POST
    @Path( "filesystem/cleanup" )
    public Response cleanup( final BatchCleanupRequest request )
    {
        logger.info( "Batch cleanup: {}", request );
        BatchCleanupResult result = controller.cleanup( request.getPath(), request.getFilesystems() );

        logger.debug( "Batch cleanup result: {}", result );
        return responseHelper.formatOkResponseWithJsonEntity( result );
    }

    @Operation( summary = "Get all filesystems." )
    @APIResponses( { @APIResponse( responseCode = "200", description = "The filesystems." ) } )
    @Produces( APPLICATION_JSON )
    @GET
    @Path( "filesystems" )
    public Response filesystems()
    {
        logger.info( "List filesystems" );
        Collection<String> result = controller.getFilesystems();
        logger.debug( "List filesystems, result: {}", result );
        return Response.ok( result ).build();
    }

}