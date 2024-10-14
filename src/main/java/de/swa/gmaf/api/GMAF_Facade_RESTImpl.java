package de.swa.gmaf.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.swa.cmmco.CMMCO;
import de.swa.cmmco.MD;
import de.swa.cmmco.PD;
import de.swa.cmmco.mmco.Image;
import de.swa.cmmco.tools.Base64ToByteArrayDeserializer;
import de.swa.gc.GraphCode;
import de.swa.gc.GraphCodeGenerator;
import de.swa.gc.GraphCodeIO;
import de.swa.gmaf.GMAF;
import de.swa.mmfg.GeneralMetadata;
import de.swa.mmfg.MMFG;
import de.swa.mmfg.Node;
import de.swa.mmfg.builder.FeatureVectorBuilder;
import de.swa.mmfg.builder.XMLEncodeDecode;
import de.swa.ui.Configuration;
import de.swa.ui.MMFGCollection;
import io.swagger.v3.jaxrs2.SwaggerSerializers;
import io.swagger.v3.jaxrs2.integration.resources.OpenApiResource;
import io.swagger.v3.oas.models.Paths;
import org.apache.jena.base.Sys;
import org.apache.jena.query.*;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.json.JSONArray;
import org.json.JSONObject;
//import software.amazon.awssdk.thirdparty.jackson.core.util.JacksonFeature;
import javax.activation.MimetypesFileTypeMap;
import javax.jws.WebMethod;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import com.google.gson.Gson;

/**
 * implementation of the GMAF REST API
 **/
@Path("/gmaf")
public class GMAF_Facade_RESTImpl extends ResourceConfig {
	public GMAF_Facade_RESTImpl() {
		//	packages("de.swa.gmaf.api");
		register(GMAF_Facade_RESTImpl.class);
		register(JacksonFeature.class);
		OpenApiResource openApiResource = new OpenApiResource();
		register(openApiResource);


		//register(ApiListingResource.class);
		register(SwaggerSerializers.class);
		//register("/swagger-ui/**");  //addResourceLocations("classpath:/META-INF/resources/webjars/swagger-ui/2.1.3/");
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




	@POST
	@Path("/query/{auth-token}/{query}")
	@Produces("application/json")
	@WebMethod
	public String[] queryByKeyword(@PathParam("auth-token") String auth_token, @PathParam("query") String keywords) {
		System.out.println("HIER");
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


	/**
	 * adds item to the collection
	 **/
	@POST
	@Path("/addItem/{auth-token}")
	@Produces("application/json")
	@WebMethod
	public MMFG addItem(String filejson, @PathParam("auth-token") String auth_token) {

		JSONObject myjson = new JSONObject(filejson);
		String base64string = myjson.getString("file");
		String name = myjson.getString("name");
		boolean overwrite = myjson.getBoolean("overwrite");
		String collectionPath = Configuration.getInstance().getCollectionPaths().get(0);
		String storePath = collectionPath + "/" + name;

		System.out.println(storePath);
		File file = new File(storePath);
		if (!overwrite) {
			if (file.exists()) {
				System.out.println("File already exists");
				return null;
			}
		}
		// Create a File object representing the file to write
		byte[] contentBytes = Base64.getDecoder().decode(base64string);
		// Use try-with-resources to ensure the stream is closed properly
		try (FileOutputStream fos = new FileOutputStream(file)) {
			fos.write(contentBytes);
			System.out.println("File written successfully to " + storePath);
		} catch (IOException e) {
			e.printStackTrace();
		}


		try {
			File f = new File(storePath);
			GMAF gmaf = new GMAF();
			MMFG fv = gmaf.processAsset(f);
			MMFGCollection coll = MMFGCollection.getInstance(auth_token);
			coll.addToCollection(fv);
			return fv;
		} catch (Exception e) {
			e.printStackTrace();
		}


		MMFG mmfg = new MMFG();
		MMFGCollection coll = MMFGCollection.getInstance(auth_token);
		//coll.addToCollection();

		return null;
	}

	/**
	 * delete items from the collection
	 **/
	@POST
	@Path("/deleteItem/{auth-token}/{itemid}")
	@Produces("application/json")
	@WebMethod
	public boolean deleteItem(@PathParam("itemid") String itemid, @PathParam("auth-token") String auth_token) {

		try {
			MMFGCollection coll = MMFGCollection.getInstance(auth_token);
			Vector<MMFG> v = coll.getCollection();
			for (MMFG mmfg : v) {
				JSONObject jsonObject = new JSONObject(mmfg.getGeneralMetadata());
				if (jsonObject.getString("id").equals(itemid)) {
					File f = mmfg.getGeneralMetadata().getFileReference();
					String filename = mmfg.getGeneralMetadata().getFileName();

					if (f.exists()) {
						//f.delete();
						System.out.println("File deleted successfully at: " + f.getPath());
						return true;
					}
				}
			}
		} catch (Throwable x) {

			x.printStackTrace();
			errorMessages.put(auth_token, x.getMessage());
			return false;
		}

		return false;
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
	 * Make a query with cmmco
	 **/
	@POST
	@Path("/getQueryIds/{auth-token}")
	@Produces("application/json")
	@WebMethod
	public String[] getQueryIds(String cmmcoJson, @PathParam("auth-token") String auth_token) {

		try {
			//Convert json to CMMCO Object
			Gson gson = new GsonBuilder()
					.registerTypeAdapter(byte[].class, new Base64ToByteArrayDeserializer())
					.create();
			CMMCO cmmco = gson.fromJson(cmmcoJson, CMMCO.class);

			//Process
			MMFG mmfgQuery= getMMFGfromKeywords(cmmco.getMd().getDescription());

			GMAF gmaf= new GMAF();
			Vector<String> plugins=  gmaf.getPlugins();
			System.out.println(plugins);

			/*
			//Image
			Image image= cmmco.getMmco().getImage();
			MMFG mmfgQuery = gmaf.processAsset(image.getFile(), image.getFilename(), "system", Configuration.getInstance().getMaxRecursions(), Configuration.getInstance().getMaxNodes(), null, null);

			//Audio

			//Text
			GraphCode
			*/

			GraphCode gcQuery = GraphCodeGenerator.generate(mmfgQuery);

			return getSimilarAssetsForGraphCode(auth_token, gcQuery);

		} catch (Throwable t) {
			t.getMessage();
		}

		return new String[0];

	}

	/**
	 * returns Metadata for specific collection items
	 **/
	@POST
	@Path("/getCmmco/{auth-token}/{itemid}")
	@Produces("application/json")
	@WebMethod
	public String getCmmco(@PathParam("auth-token") String auth_token, @PathParam("itemid") String itemid) {

		MMFGCollection coll = MMFGCollection.getInstance(auth_token);
		Vector<MMFG> v = coll.getCollection();

		for (MMFG mmfg : v) {
			JSONObject jsonObject = new JSONObject(mmfg.getGeneralMetadata());
			if (jsonObject.getString("id").equals(itemid)) {

				return "Return1";
			}
		}
		return "Return2";
	}


	/**
	 * returns Metadata for specific collection items
	 **/
	@POST
	@Path("/getMetadataForItem/{auth-token}/{itemid}")
	@Produces("application/json")
	@WebMethod
	public GeneralMetadata getCollectionMetadataForItem(@PathParam("auth-token") String auth_token, @PathParam("itemid") String itemid) {

		MMFGCollection coll = MMFGCollection.getInstance(auth_token);
		Vector<MMFG> v = coll.getCollection();
		for (MMFG mmfg : v) {
			JSONObject jsonObject = new JSONObject(mmfg.getGeneralMetadata());
			if (jsonObject.getString("id").equals(itemid)) {

				return mmfg.getGeneralMetadata();
			}
		}
		return new GeneralMetadata();
	}

	/**
	 * processes item based on id in the collection
	 **/
	@POST
	@Path("/processAssetById/{auth-token}/{itemid}")
	@Produces("application/json")
	@WebMethod
	public MMFG processAssetById(@PathParam("auth-token") String auth_token, @PathParam("itemid") String itemid) {
		try {
			MMFGCollection coll = MMFGCollection.getInstance(auth_token);
			Vector<MMFG> v = coll.getCollection();
			for (MMFG mmfg : v) {
				JSONObject jsonObject = new JSONObject(mmfg.getGeneralMetadata());
				if (jsonObject.getString("id").equals(itemid)) {

					File f = mmfg.getGeneralMetadata().getFileReference();

					FileInputStream fs = new FileInputStream(f);
					byte[] bytes = fs.readAllBytes();
					GMAF gmaf = new GMAF();
					MMFG fv = gmaf.processAsset(bytes, f.getName(), "system", Configuration.getInstance().getMaxRecursions(), Configuration.getInstance().getMaxNodes(), f.getName(), f);

					System.out.println("ProcessCommand: " + f.getName());
					//LogPanel.getCurrentInstance().addToLog("MMFG created");

					String xml = FeatureVectorBuilder.flatten(fv, new XMLEncodeDecode());
					RandomAccessFile rf = new RandomAccessFile(Configuration.getInstance().getMMFGRepo() + File.separatorChar + f.getName() + ".mmfg", "rw");
					rf.setLength(0);
					rf.writeBytes(xml);
					rf.close();

					//LogPanel.getCurrentInstance().addToLog("MMFG exported to " + Configuration.getInstance().getMMFGRepo());

					GraphCode gc = GraphCodeGenerator.generate(fv);
					GraphCodeIO.write(gc, new File(Configuration.getInstance().getGraphCodeRepository() + File.separatorChar + f.getName() + ".gc"));
					System.out.println("New Graph Code written to: " + Configuration.getInstance().getGraphCodeRepository() + File.separatorChar + f.getName() + ".gc");
					MMFGCollection.getInstance().replaceMMFGInCollection(fv, f);

					//LogPanel.getCurrentInstance().addToLog("GraphCode exported to " + Configuration.getInstance().getGraphCodeRepository());
					return fv;
				}
			}

		} catch (Throwable x) {
			x.printStackTrace();
			errorMessages.put(auth_token, x.getMessage());
		}
		return null;
	}


	private String[] getSimilarAssetsForGraphCode(String auth_token, GraphCode graphCode){

		try {

			Vector<String> ids = new Vector<String>();
			MMFGCollection coll = MMFGCollection.getInstance(auth_token);
			for (MMFG m : coll.getSimilarAssets(graphCode)) {
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
		return new String[0];
	}

	private MMFG getMMFGfromKeywords(String text){

		MMFG mmfg = new MMFG();
		//Keywords
		if (text.split(",").length>=1){

			GraphCode gc = new GraphCode();
			Vector<String> dict = new Vector<String>();
			text = text.replace(";", ",");
			//keywords = keywords.replace(" ", ",");
			String[] str = text.split(",");
			for (String s : str) {
				Node newNode= new Node();
				newNode.setName(s);
				mmfg.addNode(newNode);
			}

			return mmfg;
		}

		return mmfg;
	}

}