package de.swa.gmaf.api;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Hashtable;
import java.util.UUID;
import java.util.Vector;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import de.swa.gc.GraphCode;
import de.swa.gc.GraphCodeGenerator;
import de.swa.gmaf.GMAF;
import de.swa.mmfg.MMFG;
import de.swa.ui.MMFGCollection;

/** implementation of the GMAF SOAP API **/
@WebService(endpointInterface = "de.swa.gmaf.api.GMAF_Facade")
@Path("/api")
public class GMAF_Facade_SOAPImpl implements GMAF_Facade {
	// data structures to hold sessions
	private Hashtable<String, String> errorMessages = new Hashtable<String, String>();
	
	// returns a GMAF_Facade_SOAPImpl for a given API-Key
	private GMAF getSession(String api_key) {
		return GMAF_SessionFactory.getInstance().getGmaf(api_key);
	}
	
	/** returns a new session token **/
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/getAuthToken")
	@WebMethod public String getAuthToken(String api_key) {
		System.out.println("Auth-Token: " + api_key);
		return GMAF_SessionFactory.getInstance().getAuthToken(api_key);
	}
	
	/** processes an asset with the GMAF Core and returns the calculated MMFG **/
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@WebMethod public String processAssetFromFile(String auth_token, File f) {
		try {
			MMFG mmfg = getSession(auth_token).processAsset(f);
			MMFGCollection.getInstance(auth_token).addToCollection(mmfg);
			return mmfg.getId().toString();
		}
		catch (Exception x) {
			x.printStackTrace();
			errorMessages.put(auth_token, x.getMessage());
		}
		return null;
	}
	
	/** processes an asset with the GMAF Core and returns the calculated MMFG **/
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@WebMethod public String processAssetFromBytes(String auth_token, byte[] bytes, String suffix) {
		try {
			suffix = suffix.replace("/", "_");
			File f = File.createTempFile("gmaf", suffix);
			FileOutputStream fout = new FileOutputStream(f);
			fout.write(bytes);
			fout.flush();
			fout.close();
			
			MMFG mmfg = getSession(auth_token).processAsset(f);
			MMFGCollection.getInstance(auth_token).addToCollection(mmfg);
			return mmfg.getId().toString();
		}
		catch (Exception x) {
			x.printStackTrace();
			errorMessages.put(auth_token, x.getMessage());
		}
		return null;
	}

	/** processes an asset with the GMAF Core and returns the calculated MMFG **/
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@WebMethod public String processAssetFromURL(String auth_token, URL url) {
		try {
			URLConnection uc = url.openConnection();
			byte[] bytes = uc.getInputStream().readAllBytes();
			String suffix = url.toString();
			suffix = suffix.substring(suffix.lastIndexOf(".") + 1, suffix.length());
			return processAssetFromBytes(auth_token, bytes, suffix);
		}
		catch (Exception x) {
			x.printStackTrace();
			errorMessages.put(auth_token, x.getMessage());
		}
		return null;
	}
	
	/** sets the classes of the processing plugins (optional) **/
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@WebMethod public void setProcessingPlugins(String auth_token, Vector<String> plugins) {
		getSession(auth_token).setProcessingPlugins(plugins);
	}
	
	/** returns the collection of MMFGs for a given auth_token **/
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@WebMethod public Vector<String> getCollectionIDs(String auth_token) {
		Vector<String> ids = new Vector<String>();
		MMFGCollection coll = MMFGCollection.getInstance(auth_token);
		for (MMFG m : coll.getCollection()) {
			ids.add(m.getId().toString());
		}
		return ids;
	}
	
	/** returns a Graph Code for a given MMFG **/
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@WebMethod public GraphCode generateGraphCode(String auth_token, MMFG mmfg) {
		return GraphCodeGenerator.generate(mmfg);
	}
	
	/** returns a Graph Code for a given MMFG **/
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@WebMethod public GraphCode getOrGenerateGraphCode(String auth_token, String id) {
		MMFG mmfg = MMFGCollection.getInstance(auth_token).getMMFGForId(UUID.fromString(id));
		return GraphCodeGenerator.generate(mmfg);
	}
	
	/** returns a list of similar assets for a given Graph Code **/
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@WebMethod public Vector<String> getSimilarAssetIDsByGraphCode(String auth_token, GraphCode gc) {
		Vector<String> ids = new Vector<String>();
		MMFGCollection coll = MMFGCollection.getInstance(auth_token);
		for (MMFG m : coll.getSimilarAssets(gc)) {
			ids.add(m.getId().toString());
		}
		return ids;
	}
	
	/** returns a list of recommendations for a given Graph Code **/
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@WebMethod public Vector<String> getRecommendedAssetIDsByGraphCode(String auth_token, GraphCode gc) {
		Vector<String> ids = new Vector<String>();
		MMFGCollection coll = MMFGCollection.getInstance(auth_token);
		for (MMFG m : coll.getRecommendedAssets(gc)) {
			ids.add(m.getId().toString());
		}
		return ids;
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@WebMethod public Vector<String> getRecommendedAssetIDsForMMFGId(String auth_token, String id) {
		MMFG mmfg = MMFGCollection.getInstance(auth_token).getMMFGForId(UUID.fromString(id));
		GraphCode gc = MMFGCollection.getInstance(auth_token).getOrGenerateGraphCode(mmfg);
		return getRecommendedAssetIDsByGraphCode(auth_token, gc);
	}
	
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@WebMethod public Vector<String> getSimilarAssetIDsForMMFGId(String auth_token, String id) {
		MMFG mmfg = MMFGCollection.getInstance(auth_token).getMMFGForId(UUID.fromString(id));
		GraphCode gc = MMFGCollection.getInstance(auth_token).getOrGenerateGraphCode(mmfg);
		return getSimilarAssetIDsByGraphCode(auth_token, gc);
	}
	
	
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@WebMethod public String getLastError(String auth_token) {
		return errorMessages.get(auth_token);
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@WebMethod public MMFG getMMFG(String auth_token, String id) {
		MMFGCollection coll = MMFGCollection.getInstance(auth_token);
		MMFG m = coll.getMMFGForId(UUID.fromString(id));
		return m;
	}
	
	
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@WebMethod public String getPreviewURL(String auth_token, String mmfg_id) {
		MMFGCollection coll = MMFGCollection.getInstance(auth_token);
		UUID id = UUID.fromString(mmfg_id);
		MMFG mmfg = coll.getMMFGForId(id);
		return mmfg.getGeneralMetadata().getPreviewUrl().toString();
	}
	
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Vector<String> queryByKeyword(String auth_token, String keywords) {
		GraphCode gc = new GraphCode();
		Vector<String> dict = new Vector<String>();
		keywords = keywords.replace(";", ",");
		keywords = keywords.replace(" ", ",");
		String[] str = keywords.split(",");
		for (String s : str) dict.add(s.trim());
		gc.setDictionary(dict);
		return getSimilarAssetIDsByGraphCode(auth_token, gc);
	}
	
}
