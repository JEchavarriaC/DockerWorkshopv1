/*******************************************************************************
 * Copyright (c) 2020, 2022 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package io.openliberty.guides.application;

import java.util.Set;

import java.io.StringWriter;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.Json;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import jakarta.validation.Validator;
import jakarta.validation.ConstraintViolation;

import com.mongodb.client.FindIterable;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

@Path("/crew")
@ApplicationScoped
public class CrewService {

    @Inject
    MongoDatabase db;

    @Inject
    Validator validator;

    private JsonArray getViolations(CrewMember crewMember) {
        Set<ConstraintViolation<CrewMember>> violations = validator.validate(
                crewMember);

        JsonArrayBuilder messages = Json.createArrayBuilder();

        for (ConstraintViolation<CrewMember> v : violations) {
            messages.add(v.getMessage());
        }

        return messages.build();
    }

    @POST
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Successfully added crew member."),
        @APIResponse(
            responseCode = "400",
            description = "Invalid crew member configuration.") })
    @Operation(summary = "Add a new crew member to the database.")
    // tag::add[]
    public Response add(CrewMember crewMember) {
        JsonArray violations = getViolations(crewMember);

        if (!violations.isEmpty()) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(violations.toString())
                    .build();
        }

        MongoCollection<Document> crew = db.getCollection("Crew");

        Document newCrewMember = new Document();
        newCrewMember.put("Name", crewMember.getName());
        newCrewMember.put("Rank", crewMember.getRank());
        newCrewMember.put("CrewID", crewMember.getCrewID());

        crew.insertOne(newCrewMember);

        return Response
            .status(Response.Status.OK)
            .entity(newCrewMember.toJson())
            .build();
    }

    @GET
    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Successfully listed the crew members."),
        @APIResponse(
            responseCode = "500",
            description = "Failed to list the crew members.") })
    @Operation(summary = "List the crew members from the database.")
    public Response retrieve() {
        StringWriter sb = new StringWriter();

        try {
            MongoCollection<Document> crew = db.getCollection("Crew");
            sb.append("[");
            boolean first = true;
            FindIterable<Document> docs = crew.find();
            for (Document d : docs) {
                if (!first) {
                    sb.append(",");
                } else {
                    first = false;
                }
                sb.append(d.toJson());
            }
            sb.append("]");
        } catch (Exception e) {
            e.printStackTrace(System.out);
            return Response
                .status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("[\"Unable to list crew members!\"]")
                .build();
        }

        return Response
            .status(Response.Status.OK)
            .entity(sb.toString())
            .build();
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Successfully updated crew member."),
        @APIResponse(
            responseCode = "400",
            description = "Invalid object id or crew member configuration."),
        @APIResponse(
            responseCode = "404",
            description = "Crew member object id was not found.") })
    @Operation(summary = "Update a crew member in the database.")
    public Response update(CrewMember crewMember,
        @Parameter(
            description = "Object id of the crew member to update.",
            required = true
        )
        @PathParam("id") String id) {

        JsonArray violations = getViolations(crewMember);

        if (!violations.isEmpty()) {
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(violations.toString())
                    .build();
        }

        ObjectId oid;

        try {
            oid = new ObjectId(id);
        } catch (Exception e) {
            return Response
                .status(Response.Status.BAD_REQUEST)
                .entity("[\"Invalid object id!\"]")
                .build();
        }

        MongoCollection<Document> crew = db.getCollection("Crew");

        Document query = new Document("_id", oid);

        Document newCrewMember = new Document();
        newCrewMember.put("Name", crewMember.getName());
        newCrewMember.put("Rank", crewMember.getRank());
        newCrewMember.put("CrewID", crewMember.getCrewID());

        UpdateResult updateResult = crew.replaceOne(query, newCrewMember);

        if (updateResult.getMatchedCount() == 0) {
            return Response
                .status(Response.Status.NOT_FOUND)
                .entity("[\"_id was not found!\"]")
                .build();
        }

        newCrewMember.put("_id", oid);

        return Response
            .status(Response.Status.OK)
            .entity(newCrewMember.toJson())
            .build();
    }

    @DELETE
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponses({
        @APIResponse(
            responseCode = "200",
            description = "Successfully deleted crew member."),
        @APIResponse(
            responseCode = "400",
            description = "Invalid object id."),
        @APIResponse(
            responseCode = "404",
            description = "Crew member object id was not found.") })
    @Operation(summary = "Delete a crew member from the database.")
    public Response remove(
        @Parameter(
            description = "Object id of the crew member to delete.",
            required = true
        )
        @PathParam("id") String id) {

        ObjectId oid;

        try {
            oid = new ObjectId(id);
        } catch (Exception e) {
            return Response
                .status(Response.Status.BAD_REQUEST)
                .entity("[\"Invalid object id!\"]")
                .build();
        }

        MongoCollection<Document> crew = db.getCollection("Crew");

        Document query = new Document("_id", oid);

        DeleteResult deleteResult = crew.deleteOne(query);

        if (deleteResult.getDeletedCount() == 0) {
            return Response
                .status(Response.Status.NOT_FOUND)
                .entity("[\"_id was not found!\"]")
                .build();
        }

        return Response
            .status(Response.Status.OK)
            .entity(query.toJson())
            .build();
    }
}
