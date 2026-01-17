package de.swa.gmaf.api;

import com.google.gson.Gson;
import de.swa.gc.GraphCode;
import de.swa.gc.GraphCodeGenerator;
import de.swa.gc.GraphCodeIO;
import de.swa.gmaf.GMAF;
import de.swa.mmfg.GeneralMetadata;
import de.swa.mmfg.MMFG;
import de.swa.ui.Configuration;
import de.swa.ui.MMFGCollection;
import org.apache.jena.query.*;

import jakarta.activation.MimetypesFileTypeMap;
import jakarta.jws.WebMethod;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.*;

/**
 * implementation of the GMAF REST API
 **/
@Path("/gmaf")
public class GMAF_Facade_RESTImpl {

	public GMAF_Facade_RESTImpl() {
	}

	/**
	 * returns a new session token
	 **/
	@GET
	@Path("/getToken/{api-key}")
	public String getAuthToken(
			@PathParam("api-key") String api_key) {
		String uuid = UUID.randomUUID().toString();
		sessions.put(uuid, new GMAF());
		System.out.println("KEY: " + uuid);
		return uuid;
	}


	/**
	 * returns a Graph Code for a given MMFG
	 **/
	@POST
	@Path("/getgc/{auth-token}/{mmfg_id}")
	@Produces("application/json")
	public String getOrGenerateGraphCode(@PathParam("auth-token") String auth_token, @PathParam("mmfg_id") String mmfg_id) {
		System.out.println("getGc " + mmfg_id);
		MMFGCollection coll = MMFGCollection.getInstance(auth_token);
		UUID id = UUID.fromString(mmfg_id);
		MMFG mmfg = coll.getMMFGForId(id);
		GraphCode gc = GraphCodeGenerator.generate(mmfg);
		String json = GraphCodeIO.asJson(gc);
		System.out.println("-> " + json);
		return json;
	}

	/**
	 * returns MMFG for id
	 **/
	@POST
	@Path("/getmmfg/{auth-token}/{mmfg-id}")
	@Produces("application/json" + ";charset=utf-8")
	public Response getMMFGForId(@PathParam("auth-token") String auth_token, @PathParam("mmfg-id") String mmfg_id) {
		System.out.println("getMMFG " + mmfg_id);
		MMFGCollection coll = MMFGCollection.getInstance(auth_token);
		UUID id = UUID.fromString(mmfg_id);
		MMFG m = coll.getMMFGForId(id);
		if (m == null) {
			System.out.println("-> not found");
			return Response.status(Response.Status.NOT_FOUND).build();
		}
		System.out.println("-> " + m.getId() + " with " + m.getAllNodes().size() + " nodes");
		return Response.ok(m).build();
	}

	// data structures to hold sessions
	private Hashtable<String, GMAF> sessions = new Hashtable<String, GMAF>();
	private Hashtable<String, String> errorMessages = new Hashtable<String, String>();

	// returns a GMAF_Facade_SOAPImpl for a given API-Key
	@POST
	@Path("/{session}/{api-key}")
	@Produces("application/json")
	public GMAF getSession(@PathParam("api-key") String api_key) {
		if (sessions.contains(api_key)) return sessions.get(api_key);
		else throw new RuntimeException("no valid API key");
	}

	/**
	 * returns the collection of MMFGs for a given auth_token
	 **/
	@POST
	@Path("/getCollection/{auth-token}")
	@Produces("application/json")
	@WebMethod
	public Vector<MMFG> getCollection(@PathParam("auth-token") String auth_token) {
		MMFGCollection coll = MMFGCollection.getInstance(auth_token);
		return coll.getCollection();
	}

	/**
	 * returns an image with given id
	 **/
	@GET
	@Path("/preview/{auth-token}/{id}")
	@Produces("image/*")
	@WebMethod
	public Response getImage(@PathParam("auth-token") String auth_token, @PathParam("id") String mmfg_id) {
		System.out.println("preview " + mmfg_id);
		MMFGCollection coll = MMFGCollection.getInstance(auth_token);
		UUID id = UUID.fromString(mmfg_id);
		MMFG mmfg = coll.getMMFGForId(id);
		if (mmfg == null)
			return Response.status(Response.Status.NOT_FOUND).build();
		File file = mmfg.getGeneralMetadata().getFileReference();
		System.out.println("-> " + file.getAbsolutePath());
		if (!file.exists())
			return Response.status(Response.Status.NOT_FOUND).build();
		String type = "application/jpg";
		try {
			type = new MimetypesFileTypeMap().getContentType(file);
		} catch (Error ex) {
			ex.printStackTrace();
		}
		//System.out.println("--> " + type);
		StreamingOutput stream = output -> {
			try (FileInputStream fis = new FileInputStream(file)) {
				byte[] buffer = new byte[8192];
				int bytesRead;
				while ((bytesRead = fis.read(buffer)) != -1) {
					output.write(buffer, 0, bytesRead);
				}
				output.flush();
			} catch (java.io.IOException e) {
				System.err.println("Client disconnected during preview: " + e.getMessage());
				throw e;
			}
		};
		Response res = Response.ok(stream)
				.type(type)
				.header("Content-Length", file.length())
				.build();
		//System.out.println("---> " + res.getHeaderString("contentType"));
		return res;
	}

	/**
	 * returns image-URL as String
	 **/
	@POST
	@Path("/preview/{auth-token}/{id}")
	@Produces("application/json")
	@WebMethod
	public String getPreviewURL(@PathParam("auth-token") String auth_token, @PathParam("id") String mmfg_id) {
		MMFGCollection coll = MMFGCollection.getInstance(auth_token);
		UUID id = UUID.fromString(mmfg_id);
		MMFG mmfg = coll.getMMFGForId(id);
		return mmfg.getGeneralMetadata().getPreviewUrl().toString();
	}

	/**
	 * downloads an asset with given id
	 **/
	@GET
	@Path("/download/{auth-token}/{id}")
	@Produces("application/octet-stream")
	@WebMethod
	public Response downloadAsset(@PathParam("auth-token") String auth_token, @PathParam("id") String mmfg_id) {
		System.out.println("download " + mmfg_id);
		MMFGCollection coll = MMFGCollection.getInstance(auth_token);
		UUID id = UUID.fromString(mmfg_id);
		MMFG mmfg = coll.getMMFGForId(id);
		if (mmfg == null)
			return Response.status(Response.Status.NOT_FOUND).build();
		File file = mmfg.getGeneralMetadata().getFileReference();
		System.out.println("-> " + file.getAbsolutePath());
		if (!file.exists())
			return Response.status(Response.Status.NOT_FOUND).build();
		String type = "application/octet-stream";
		try {
			type = new MimetypesFileTypeMap().getContentType(file);
		} catch (Error ex) {
			ex.printStackTrace();
		}
		System.out.println("--> " + type);
		StreamingOutput stream = output -> {
			try (FileInputStream fis = new FileInputStream(file)) {
				byte[] buffer = new byte[8192];
				int bytesRead;
				while ((bytesRead = fis.read(buffer)) != -1) {
					output.write(buffer, 0, bytesRead);
				}
				output.flush();
			} catch (java.io.IOException e) {
				System.err.println("Client disconnected during download: " + e.getMessage());
				throw e;
			}
		};
		Response res = Response.ok(stream)
				.type(type)
				.header("Content-Length", file.length())
				.header("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"")
				.build();
		System.out.println("---> " + res.getHeaderString("contentType"));
		return res;
	}


	/**
	 * returns a list of similar assets for a given Graph Code
	 **/
	@POST
	@Path("/getSim/{auth-token}/{id}")
	@Produces("application/json")
	@WebMethod
	public Vector<MMFG> getSimilarAssets(@PathParam("auth-token") String auth_token, @PathParam("id") String mmfg_id) {
		MMFGCollection coll = MMFGCollection.getInstance(auth_token);
		UUID id = UUID.fromString(mmfg_id);
		MMFG mmfg = coll.getMMFGForId(id);
		GraphCode gc = GraphCodeGenerator.generate(mmfg);
		return coll.getSimilarAssets(gc);
	}

	/**
	 * returns a list of recommendations for a given Graph Code
	 **/
	@POST
	@Path("/getRec/{auth-token}/{id}")
	@Produces("application/json")
	@WebMethod
	public Vector<MMFG> getRecommendedAssets(@PathParam("auth-token") String auth_token, @PathParam("id") String mmfg_id) {
		MMFGCollection coll = MMFGCollection.getInstance(auth_token);
		UUID id = UUID.fromString(mmfg_id);
		MMFG mmfg = coll.getMMFGForId(id);
		GraphCode gc = GraphCodeGenerator.generate(mmfg);
		return coll.getRecommendedAssets(gc);
	}

	@POST
	@Path("/query/{auth-token}/{query}")
	@Produces("application/json")
	@WebMethod
	public String[] queryByKeyword(@PathParam("auth-token") String auth_token, @PathParam("query") String keywords) {
		GraphCode gc = new GraphCode();
		Vector<String> dict = new Vector<String>();
		keywords = keywords.replace(";", ",");
		//keywords = keywords.replace(" ", ",");
		String[] str = keywords.split(",");
		for (String s : str) dict.add(s.trim());
		gc.setDictionary(dict);

		System.out.println("query by keyword " + keywords + " with token " + auth_token);

		try {

			Vector<String> ids = new Vector<String>();
			MMFGCollection coll = MMFGCollection.getInstance(auth_token);
			for (MMFG m : coll.getSimilarAssets(gc)) {
				ids.add(m.getId().toString());
			}

			if (ids.size() == 0) {
				Vector<MMFG> mmfgs = MMFGCollection.getInstance(auth_token).getCollection();
				for (MMFG m : mmfgs) ids.add(m.getId().toString());
			}

			System.out.println("found " + ids.size() + " results");

			String[] strx = new String[ids.size()];
			for (int i = 0; i < strx.length; i++) {
				String s = ids.get(i);
				strx[i] = s;
			}
			return strx;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	@POST
	@Path("/get-collection-ids/{auth-token}")
	@Produces("application/json")
	@WebMethod
	public String[] getCollectionIds(@PathParam("auth-token") String auth_token) {
		MMFGCollection coll = MMFGCollection.getInstance(auth_token);
		Vector<MMFG> v = coll.getCollection();
		String[] str = new String[v.size()];
		for (int i = 0; i < v.size(); i++) {
			str[i] = v.get(i).getGeneralMetadata().getId().toString();
		}
		return str;
	}


	/**
	 * returns Metadata for collection items
	 **/
	@POST
	@Path("/getMetadata/{auth-token}")
	@Produces("application/json")
	@WebMethod
	public GeneralMetadata[] getCollectionMetadata(@PathParam("auth-token") String auth_token) {
		MMFGCollection coll = MMFGCollection.getInstance(auth_token);
		Vector<MMFG> v = coll.getCollection();
		GeneralMetadata[] str = new GeneralMetadata[v.size()];
		for (int i = 0; i < v.size(); i++) {
			str[i] = v.get(i).getGeneralMetadata();
		}
		return str;
	}
	/*
	 *//** processes an asset with the GMAF Core and returns the calculated MMFG **//*
	public MMFG processAsset(String auth_token, @FormParam("file") File f) {
		try {
			return getSession(auth_token).processAsset(f);
		}
		catch (Exception x) {
			x.printStackTrace();
			errorMessages.put(auth_token, x.getMessage());
		}
		return null;
	}

	*//** processes an asset with the GMAF Core and returns the calculated MMFG **//*
	@WebMethod public MMFG processAsset(String auth_token, byte[] bytes, String suffix) {
		try {
			File f = File.createTempFile("gmaf", suffix);
			FileOutputStream fout = new FileOutputStream(f);
			fout.write(bytes);
			fout.flush();
			fout.close();
			return getSession(auth_token).processAsset(f);
		}
		catch (Exception x) {
			x.printStackTrace();
			errorMessages.put(auth_token, x.getMessage());
		}
		return null;
	}

	*//** processes an asset with the GMAF Core and returns the calculated MMFG **//*
	@POST
    @Path("/{process-asset}/{mmfg}")
	@Produces("application/json")
	public MMFG processAsset(@PathParam("auth-token") String auth_token, @PathParam("url") String surl) {
		try {
			URL url = new URL(surl);
			URLConnection uc = url.openConnection();
			byte[] bytes = uc.getInputStream().readAllBytes();
			String suffix = url.toString();
			suffix = suffix.substring(suffix.lastIndexOf(".") + 1, suffix.length());
			return processAsset(auth_token, bytes, suffix);
		}
		catch (Exception x) {
			x.printStackTrace();
			errorMessages.put(auth_token, x.getMessage());
		}
		return null;
	}

	*/

	/**
	 * sets the classes of the processing plugins (optional)
	 **//*
	@WebMethod public void setProcessingPlugins(String auth_token, Vector<String> plugins) {
		getSession(auth_token).setProcessingPlugins(plugins);
	}                                                                      */
	                                 /*
	@POST
	@Path("/query-by-example")
	@Produces("application/json")
	@WebMethod public String[] queryByExample(@PathParam("auth-token") String auth_token, @PathParam("mmfg-id") String mmfg_id) {
		QueryByExampleCommand qbe = new QueryByExampleCommand(mmfg_id, auth_token);
		qbe.execute();
		return getCollectionIds(auth_token);
	}

	@POST
	@Path("/{query-by-sparql}")
	@Produces("application/json")
	@WebMethod public String[] queryBySPARQL(@PathParam("auth-token") String auth_token, @PathParam("query") String query) {
		QueryBySPARQLCommand qbs = new QueryBySPARQLCommand(query);
		qbs.setSessionId(auth_token);
		qbs.execute();
		return getCollectionIds(auth_token);
	}

	@POST
    @Path("/{get-last-error}")
	@Produces("application/json")
	@WebMethod public String getLastError(@PathParam("auth-token") String auth_token) {
		return errorMessages.get(auth_token);
	}*/
	@POST
	@Path("/test")
	@Produces("application/json")
	@WebMethod
	public String getTestPost() {
		System.out.println("Called Test, return stuff");
		return "test";
	}
	@GET
	@Path("/test")
	@Produces("application/json")
	@WebMethod
	public String getTestGet() {
		System.out.println("Called Test, return stuff");
		return "test";
	}


	@POST
	@Path("/getsimilarassetsbygraphcode/{auth-token}")
	@Produces("application/json")
	@WebMethod
	public Vector<String> getSimilarAssetIDsByGraphCode(@PathParam("auth-token") String auth_token, String x) {
		Gson gson = new Gson();
		GraphCode gc = gson.fromJson(x, GraphCode.class);

		Vector<String> ids = new Vector<String>();
		MMFGCollection coll = MMFGCollection.getInstance(auth_token);
		for (MMFG m : coll.getSimilarAssets(gc)) {
			ids.add(m.getId().toString());
		}
		return ids;
	}

	@POST
	@Path("/sparql/{auth-token}/")
	@Consumes(MediaType.TEXT_PLAIN)
	@Produces(MediaType.APPLICATION_JSON)
	@WebMethod
	public String[] queryBySparQL(@PathParam("auth-token") String auth_token, String query) {
		Query q = QueryFactory.create(query);

		QueryExecution qexec = QueryExecutionFactory.create(q, MMFGCollection.getInstance().getRDFModel());

		ResultSet results = qexec.execSelect();
		int colNum = results.getResultVars().size();
		int rowNum = 0;
		String[] header = new String[colNum];
		results.getResultVars().toArray(header);
		ArrayList<ArrayList> tempData = new ArrayList<ArrayList>();

		for (; results.hasNext(); ) {
			QuerySolution soln = results.nextSolution();
			ArrayList row = new ArrayList();
			for (int i = 0; i < colNum; i++) {
				row.add(soln.get(header[i]));
			}
			tempData.add(row);
			rowNum++;

		}
		Object[][] data = new Object[rowNum][colNum];

		String[] str = new String[rowNum];
		for (int i = 0; i < rowNum; i++) {
			for (int j = 0; j < colNum; j++) {
				//data[i][j] = tempData.get(i).get(j)
				System.out.println(tempData.get(i).get(j));
				String f = tempData.get(i).get(j).toString();
				String[] xf = f.split("/");
				String fname = xf[xf.length - 1];
				System.out.println(fname);

				File file = new File(Configuration.getInstance().getCollectionPaths().elementAt(0) + "/" + fname);
				MMFG mmfg = MMFGCollection.getInstance().getMMFGForFile(
						file);
				System.out.println(mmfg.getId());
				str[i] = mmfg.getId().toString();
			}

		}

		qexec.close();

		return str;

	}

}
