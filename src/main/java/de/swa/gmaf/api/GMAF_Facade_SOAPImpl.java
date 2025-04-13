package de.swa.gmaf.api;

import com.google.gson.Gson;
import de.swa.gc.GraphCode;
import de.swa.gc.GraphCodeGenerator;
import de.swa.gc.GraphCodeIO;
import de.swa.gmaf.GMAF;
import de.swa.mmfg.GeneralMetadata;
import de.swa.mmfg.MMFG;
import de.swa.ui.MMFGCollection;
import de.swa.ui.MMFGCollectionFactory;

import jakarta.jws.WebMethod;
import jakarta.jws.WebService;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Hashtable;
import java.util.UUID;
import java.util.Vector;

/**
 * implementation of the GMAF SOAP API
 **/
@WebService(endpointInterface = "de.swa.gmaf.api.GMAF_Facade")
@Path("/api")
public class GMAF_Facade_SOAPImpl implements GMAF_Facade {
    // data structures to hold sessions
    private Hashtable<String, String> errorMessages = new Hashtable<String, String>();

    // returns a GMAF_Facade_SOAPImpl for a given API-Key
    private GMAF getSession(String api_key) {
        return GMAF_SessionFactory.getInstance().getGmaf(api_key);
    }

    /**
     * returns a new session token
     **/
    @GET
    @Path("/auth-token/{api-key}")
    @Produces(MediaType.APPLICATION_JSON)
    @WebMethod
    public String getAuthToken(@PathParam("api-key") String api_key) {
        System.out.println("Auth-Token: " + api_key);
        return GMAF_SessionFactory.getInstance().getAuthToken(api_key);
    }

    /**
     * processes an asset with the GMAF Core and returns the calculated MMFG
     **/
    @POST
    @Path("/asset/file/{auth-token}")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.TEXT_PLAIN)
    @WebMethod
    public String processAssetFromFile(@PathParam("auth-token") String auth_token, File f) {
        try {
            MMFG mmfg = getSession(auth_token).processAsset(f);
            MMFGCollectionFactory.createOrGetCollection(auth_token).addToCollection(mmfg);
            return mmfg.getId().toString();
        } catch (Exception x) {
            x.printStackTrace();
            errorMessages.put(auth_token, x.getMessage());
        }
        return null;
    }

    /**
     * processes an asset with the GMAF Core and returns the calculated MMFG
     **/
    @POST
    @Path("/asset/bytes/{auth-token}")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.TEXT_PLAIN)
    @WebMethod
    public String processAssetFromBytes(@PathParam("auth-token") String auth_token, byte[] bytes, @QueryParam("suffix") String suffix) {
        try {
            suffix = suffix.replace("/", "_");
            File f = File.createTempFile("gmaf", suffix);
            FileOutputStream fout = new FileOutputStream(f);
            fout.write(bytes);
            fout.flush();
            fout.close();

            MMFG mmfg = getSession(auth_token).processAsset(f);
            MMFGCollectionFactory.createOrGetCollection(auth_token).addToCollection(mmfg);
            return mmfg.getId().toString();
        } catch (Exception x) {
            x.printStackTrace();
            errorMessages.put(auth_token, x.getMessage());
        }
        return null;
    }

    /**
     * processes an asset with the GMAF Core and returns the calculated MMFG
     **/
    @POST
    @Path("/asset/url/{auth-token}")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    @WebMethod
    public String processAssetFromURL(@PathParam("auth-token") String auth_token, URL url) {
        try {
            URLConnection uc = url.openConnection();
            byte[] bytes = uc.getInputStream().readAllBytes();
            String suffix = url.toString();
            suffix = suffix.substring(suffix.lastIndexOf(".") + 1, suffix.length());
            return processAssetFromBytes(auth_token, bytes, suffix);
        } catch (Exception x) {
            x.printStackTrace();
            errorMessages.put(auth_token, x.getMessage());
        }
        return null;
    }

    /**
     * sets the classes of the processing plugins (optional)
     **/
    @POST
    @Path("/plugins/{auth-token}")
    @Consumes(MediaType.APPLICATION_JSON)
    @WebMethod
    public void setProcessingPlugins(@PathParam("auth-token") String auth_token, Vector<String> plugins) {
        getSession(auth_token).setProcessingPlugins(plugins);
    }

    /**
     * returns the collection of MMFGs for a given auth_token
     **/
    @GET
    @Path("/collections/{auth-token}")
    @Produces(MediaType.APPLICATION_JSON)
    @WebMethod
    public Vector<String> getCollectionIDs(@PathParam("auth-token") String auth_token) {
        Vector<String> ids = new Vector<String>();
        MMFGCollection coll = MMFGCollectionFactory.createOrGetCollection(auth_token);
        for (MMFG m : coll.getCollection()) {
            ids.add(m.getId().toString());
        }
        return ids;
    }

    /**
     * returns a Graph Code for a given MMFG
     **/
    @POST
    @Path("/graphcode/generate/{auth-token}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @WebMethod
    public GraphCode generateGraphCode(@PathParam("auth-token") String auth_token, MMFG mmfg) {
        return GraphCodeGenerator.generate(mmfg);
    }

    /**
     * returns a Graph Code for a given MMFG
     **/
    @GET
    @Path("/graphcode/{auth-token}/{mmfg-id}")
    @Produces(MediaType.APPLICATION_JSON)
    @WebMethod
    public GraphCode getOrGenerateGraphCode(@PathParam("auth-token") String auth_token, @PathParam("mmfg-id") String mmfg_id) {
        MMFG mmfg = MMFGCollectionFactory.createOrGetCollection(auth_token).getMMFGForId(UUID.fromString(mmfg_id));
        return GraphCodeGenerator.generate(mmfg);
    }

    /**
     * returns a list of similar assets for a given Graph Code
     **/
    @POST
    @Path("/similar-assets/{auth-token}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @WebMethod
    public Vector<String> getSimilarAssetIDsByGraphCode(@PathParam("auth-token") String auth_token, GraphCode gc) {
        Vector<String> ids = new Vector<String>();
        MMFGCollection coll = MMFGCollectionFactory.createOrGetCollection(auth_token);
        for (MMFG m : coll.getSimilarAssets(gc)) {
            ids.add(m.getId().toString());
        }
        return ids;
    }

    /**
     * returns a list of recommendations for a given Graph Code
     **/
    @POST
    @Path("/recommended-assets/{auth-token}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @WebMethod
    public Vector<String> getRecommendedAssetIDsByGraphCode(@PathParam("auth-token") String auth_token, GraphCode gc) {
        Vector<String> ids = new Vector<String>();
        MMFGCollection coll = MMFGCollectionFactory.createOrGetCollection(auth_token);
        for (MMFG m : coll.getRecommendedAssets(gc)) {
            ids.add(m.getId().toString());
        }
        return ids;
    }

    @GET
    @Path("/recommended-assets-for-mmfg-id/{auth-token}/{mmfg-id}")
    @Produces(MediaType.APPLICATION_JSON)
    @WebMethod
    public Vector<String> getRecommendedAssetIDsForMMFGId(@PathParam("auth-token") String auth_token, @PathParam("mmfg-id") String mmfg_id) {
        MMFG mmfg = MMFGCollectionFactory.createOrGetCollection(auth_token).getMMFGForId(UUID.fromString(mmfg_id));
        GraphCode gc = MMFGCollectionFactory.createOrGetCollection(auth_token).getOrGenerateGraphCode(mmfg);
        return getRecommendedAssetIDsByGraphCode(auth_token, gc);
    }

    @GET
    @Path("/similar-assets-for-mmfg-id/{auth-token}/{mmfg-id}")
    @Produces(MediaType.APPLICATION_JSON)
    @WebMethod
    public Vector<String> getSimilarAssetIDsForMMFGId(@PathParam("auth-token") String auth_token, @PathParam("mmfg-id") String mmfg_id) {
        MMFG mmfg = MMFGCollectionFactory.createOrGetCollection(auth_token).getMMFGForId(UUID.fromString(mmfg_id));
        GraphCode gc = MMFGCollectionFactory.createOrGetCollection(auth_token).getOrGenerateGraphCode(mmfg);
        return getSimilarAssetIDsByGraphCode(auth_token, gc);
    }

    @GET
    @Path("/last-error/{auth-token}")
    @Produces(MediaType.TEXT_PLAIN)
    @WebMethod
    public String getLastError(@PathParam("auth-token") String auth_token) {
        return errorMessages.get(auth_token);
    }

    @GET
    @Path("/mmfg/{auth-token}/{mmfg-id}")
    @Produces(MediaType.APPLICATION_JSON)
    @WebMethod
    public MMFG getMMFG(@PathParam("auth-token") String auth_token, @PathParam("mmfg-id") String mmfg_id) {
        MMFGCollection coll = MMFGCollectionFactory.createOrGetCollection(auth_token);
        MMFG m = coll.getMMFGForId(UUID.fromString(mmfg_id));
        return m;
    }

    @GET
    @Path("/preview/{auth-token}/{mmfg-id}")
    @Produces(MediaType.TEXT_PLAIN)
    @WebMethod
    public String getPreviewURL(@PathParam("auth-token") String auth_token, @PathParam("mmfg-id") String mmfg_id) {
        MMFGCollection coll = MMFGCollectionFactory.createOrGetCollection(auth_token);
        UUID id = UUID.fromString(mmfg_id);
        MMFG mmfg = coll.getMMFGForId(id);
        return mmfg.getGeneralMetadata().getPreviewUrl().toString();
    }

    @GET
    @Path("/keyword/{auth-token}/{keyword}")
    @Produces(MediaType.APPLICATION_JSON)
    @WebMethod
    public Vector<String> queryByKeyword(@PathParam("auth-token") String auth_token, @PathParam("keyword") String keyword) {
        GraphCode gc = new GraphCode();
        Vector<String> dict = new Vector<String>();
        keyword = keyword.replace(";", ",");
        keyword = keyword.replace(" ", ",");
        String[] str = keyword.split(",");
        for (String s : str) dict.add(s.trim());
        gc.setDictionary(dict);
        return getSimilarAssetIDsByGraphCode(auth_token, gc);
    }
}
