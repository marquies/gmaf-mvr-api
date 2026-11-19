package de.swa.gmaf.api;

import java.net.URI;

import com.google.gson.Gson;

import de.swa.gmaf.GMAF;
import de.swa.gmaf.jobs.ProcessingJob;
import de.swa.gmaf.jobs.ProcessingJobs;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Asynchronous processing: POST an asset, get a job back at once (202), then
 * poll the job until its status is DONE (with mmfgId) or FAILED (with error).
 * <p>
 * The synchronous endpoints under /api/asset keep the request open until the
 * analysis is done, which proxies time out on for long assets such as videos.
 **/
@Path("/api/jobs")
public class GMAF_JobsResource {
	private static final Gson gson = new Gson();

	/**
	 * stores the asset and queues it for processing with the session's plugins
	 **/
	@POST
	@Path("/asset/bytes/{auth-token}")
	@Consumes(MediaType.APPLICATION_OCTET_STREAM)
	@Produces(MediaType.APPLICATION_JSON)
	public Response submitAssetFromBytes(@PathParam("auth-token") String auth_token, byte[] bytes,
			@QueryParam("suffix") String suffix) {
		GMAF session = GMAF_SessionFactory.getInstance().getGmaf(auth_token);
		if (session == null)
			return error(Response.Status.UNAUTHORIZED, "unknown auth token");
		if (bytes == null || bytes.length == 0)
			return error(Response.Status.BAD_REQUEST, "no asset data");
		try {
			ProcessingJob job = ProcessingJobs.getInstance().submit(bytes, suffix, ProcessingJobs.with(session));
			return Response.accepted(gson.toJson(job))
					.location(URI.create("api/jobs/" + auth_token + "/" + job.getId()))
					.build();
		} catch (Exception x) {
			x.printStackTrace();
			return error(Response.Status.INTERNAL_SERVER_ERROR, x.getMessage());
		}
	}

	/**
	 * returns the job's state; any valid session may read it, as sessions do not
	 * survive a restart but jobs do
	 **/
	@GET
	@Path("/{auth-token}/{job-id}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getJob(@PathParam("auth-token") String auth_token, @PathParam("job-id") String job_id) {
		if (GMAF_SessionFactory.getInstance().getGmaf(auth_token) == null)
			return error(Response.Status.UNAUTHORIZED, "unknown auth token");
		try {
			ProcessingJob job = ProcessingJobs.getInstance().get(job_id);
			if (job == null)
				return error(Response.Status.NOT_FOUND, "unknown job");
			return Response.ok(gson.toJson(job)).build();
		} catch (Exception x) {
			x.printStackTrace();
			return error(Response.Status.INTERNAL_SERVER_ERROR, x.getMessage());
		}
	}

	private static Response error(Response.Status status, String message) {
		return Response.status(status)
				.entity(gson.toJson(java.util.Collections.singletonMap("error", String.valueOf(message))))
				.type(MediaType.APPLICATION_JSON)
				.build();
	}
}
