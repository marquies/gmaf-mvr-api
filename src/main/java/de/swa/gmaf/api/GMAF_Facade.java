package de.swa.gmaf.api;

import de.swa.gc.GraphCode;
import de.swa.mmfg.MMFG;

import jakarta.jws.WebMethod;
import jakarta.jws.WebService;
import java.io.File;
import java.net.URL;
import java.util.Vector;

/**
 * interface for the GMAF API
 **/
@WebService
public interface GMAF_Facade {
	/** returns a new session token **/
	@WebMethod public String getAuthToken(String api_key);

	/** processes an asset with the GMAF Core and returns the calculated MMFG **/
	@WebMethod public String processAssetFromFile(String auth_token, File f);
	
	/** processes an asset with the GMAF Core and returns the calculated MMFG **/
	@WebMethod public String processAssetFromBytes(String auth_token, byte[] bytes, String suffix);
	
	/** processes an asset with the GMAF Core and returns the calculated MMFG **/
	@WebMethod public String processAssetFromURL(String auth_token, URL url);
	
	/** sets the classes of the processing plugins (optional) **/
	@WebMethod public void setProcessingPlugins(String auth_token, Vector<String> plugins);
	
	/** returns the collection of MMFGs for a given auth_token **/
	@WebMethod public Vector<String> getCollectionIDs(String auth_token);
	
	/** returns a Graph Code for a given MMFG **/
	@WebMethod public GraphCode generateGraphCode(String auth_token,MMFG mmfg);
	
	/** returns a Graph Code for a given MMFG-ID **/
	@WebMethod public GraphCode getOrGenerateGraphCode(String auth_token, String mmfg);

	/** returns a list of similar assets for a given Graph Code **/
	@WebMethod public Vector<String> getSimilarAssetIDsByGraphCode(String auth_token, GraphCode gc);
	
	/** returns a list of recommendations for a given Graph Code **/
	@WebMethod public Vector<String> getRecommendedAssetIDsByGraphCode(String auth_token, GraphCode gc);
	
	/** returns a list of similar assets for a given Graph Code **/
	@WebMethod public Vector<String> getSimilarAssetIDsForMMFGId(String auth_token, String id);
	
	/** returns a list of recommendations for a given Graph Code **/
	@WebMethod public Vector<String> getRecommendedAssetIDsForMMFGId(String auth_token, String id);

	/** returns a list of recommendations for a given Graph Code **/
	@WebMethod public MMFG getMMFG(String auth_token, String id);
	
	/** returns an URL to preview a MMFG **/
	@WebMethod public String getPreviewURL(String auth_token, String mmfg_id);
	
	/** returns a list of similar assets for a given keyword **/
	@WebMethod public Vector<String> queryByKeyword(String auth_token, String keywords);
}
